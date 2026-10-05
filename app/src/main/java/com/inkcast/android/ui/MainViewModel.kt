package com.inkcast.android.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkcast.android.data.cache.AudioCacheManager
import com.inkcast.android.data.cache.PodcastFeedCacheManager
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.FeedResolveResult
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.data.model.PodcastFeed
import com.inkcast.android.data.resolver.FeedResolverAgent
import com.inkcast.android.util.ImageLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

enum class NavigationTab {
    LIBRARY,
    EXPLORE,
    SETTINGS
}

@Immutable
data class MainUiState(
    val currentTab: NavigationTab = NavigationTab.LIBRARY,
    val isDetailOpen: Boolean = false,
    val isPlayerSheetExpanded: Boolean = false,
    val subscribedFeeds: List<PodcastFeed> = emptyList(),
    val selectedFeed: PodcastFeed? = null,
    val allEpisodes: List<Episode> = emptyList(),
    val episodeProgressMap: Map<String, PlaybackProgress> = emptyMap(),
    val currentPage: Int = 1,
    val itemsPerPage: Int = 6,
    val totalPages: Int = 1,
    val pagedEpisodes: List<Episode> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAddFeedDialogOpen: Boolean = false,
    val isSettingsDialogOpen: Boolean = false,
    val isResolvingFeed: Boolean = false,
    val searchResolveResult: FeedResolveResult? = null,
    val settings: AppSettings = AppSettings(),
    // Cache statistics & state
    val audioCacheSizeBytes: Long = 0L,
    val feedCacheSizeBytes: Long = 0L,
    val imageCacheSizeBytes: Long = 0L,
    val cachedEpisodeIds: Set<String> = emptySet(),
    val cachingProgressMap: Map<String, Int> = emptyMap(),
    val isOfflineCacheLoaded: Boolean = false
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val resolverAgent: FeedResolverAgent = FeedResolverAgent.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        const val ITEMS_PER_PAGE = 6
    }

    private val prefsManager = PreferencesManager.getInstance(application)
    private val feedCacheManager = PodcastFeedCacheManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            subscribedFeeds = prefsManager.getSubscribedFeeds(),
            settings = prefsManager.getSettings()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val initialFeeds = prefsManager.getSubscribedFeeds()
        val selectedId = prefsManager.getSelectedFeedId()
        val selectedFeed = initialFeeds.find { it.id == selectedId } ?: initialFeeds.firstOrNull()

        _uiState.update {
            it.copy(
                subscribedFeeds = initialFeeds,
                selectedFeed = selectedFeed
            )
        }

        // Listen for cache updates
        viewModelScope.launch {
            AudioCacheManager.cachedEpisodeIds.collect { ids ->
                _uiState.update { it.copy(cachedEpisodeIds = ids) }
            }
        }
        viewModelScope.launch {
            AudioCacheManager.cachingProgress.collect { progressMap ->
                _uiState.update { it.copy(cachingProgressMap = progressMap) }
            }
        }

        refreshCacheSizes()

        selectedFeed?.let {
            loadEpisodesForFeed(it)
        }
    }

    fun setCurrentTab(tab: NavigationTab) {
        _uiState.update { it.copy(currentTab = tab, isDetailOpen = false) }
    }

    fun openPodcastDetail(feed: PodcastFeed) {
        selectFeed(feed.id)
        _uiState.update { it.copy(isDetailOpen = true) }
    }

    fun closePodcastDetail() {
        _uiState.update { it.copy(isDetailOpen = false) }
    }

    fun openPlayerSheet() {
        _uiState.update { it.copy(isPlayerSheetExpanded = true) }
    }

    fun closePlayerSheet() {
        _uiState.update { it.copy(isPlayerSheetExpanded = false) }
    }

    fun selectFeed(feedId: String) {
        val feed = _uiState.value.subscribedFeeds.find { it.id == feedId } ?: return
        prefsManager.setSelectedFeedId(feedId)
        _uiState.update {
            it.copy(
                selectedFeed = feed,
                currentPage = 1,
                errorMessage = null
            )
        }
        loadEpisodesForFeed(feed)
    }

    fun loadEpisodesForFeed(feed: PodcastFeed) {
        viewModelScope.launch {
            // Step 1: Stale-While-Revalidate: Immediately check local feed cache for instant rendering
            val cachedEpisodes = withContext(ioDispatcher) {
                feedCacheManager.getCachedEpisodes(feed.id)
            }
            if (cachedEpisodes.isNotEmpty()) {
                val cachedProgresses = withContext(ioDispatcher) {
                    cachedEpisodes.mapNotNull { ep -> prefsManager.getProgress(ep.id) }
                        .associateBy { it.episodeId }
                }
                val cachedTotalPages = max(1, ceil(cachedEpisodes.size.toDouble() / ITEMS_PER_PAGE).toInt())
                val cachedPaged = getPageSlice(cachedEpisodes, 1, ITEMS_PER_PAGE)
                _uiState.update {
                    it.copy(
                        allEpisodes = cachedEpisodes,
                        episodeProgressMap = cachedProgresses,
                        currentPage = 1,
                        totalPages = cachedTotalPages,
                        pagedEpisodes = cachedPaged,
                        isLoading = false,
                        isOfflineCacheLoaded = true
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, isOfflineCacheLoaded = false) }
            }

            // Step 2: Fetch latest episodes from remote RSS network
            try {
                val result = withContext(ioDispatcher) {
                    val requestUrl = resolverAgent.applyProxyIfOverseas(feed.feedUrl, _uiState.value.settings.cfWorkerUrl)
                    val xmlContent = resolverAgent.fetchXml(requestUrl)
                    resolverAgent.parseRssXml(
                        xmlContent = xmlContent,
                        feedUrl = feed.feedUrl,
                        originalInput = feed.originalInput,
                        cfWorkerUrl = _uiState.value.settings.cfWorkerUrl
                    )
                }

                val episodes = result.episodes.take(20)
                // Save fresh episodes to disk cache
                withContext(ioDispatcher) {
                    feedCacheManager.saveEpisodes(feed.id, episodes)
                }
                refreshCacheSizes()

                val progresses = withContext(ioDispatcher) {
                    episodes.mapNotNull { ep -> prefsManager.getProgress(ep.id) }
                        .associateBy { it.episodeId }
                }
                val totalPages = max(1, ceil(episodes.size.toDouble() / ITEMS_PER_PAGE).toInt())
                val paged = getPageSlice(episodes, 1, ITEMS_PER_PAGE)

                _uiState.update {
                    it.copy(
                        allEpisodes = episodes,
                        episodeProgressMap = progresses,
                        currentPage = 1,
                        totalPages = totalPages,
                        pagedEpisodes = paged,
                        isLoading = false,
                        isOfflineCacheLoaded = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load episodes for ${feed.title}", e)
                val hadCache = _uiState.value.isOfflineCacheLoaded || cachedEpisodes.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (!hadCache) "加载单集列表失败: ${e.message ?: "未知网络错误"}" else null
                    )
                }
            }
        }
    }

    fun refreshCurrentFeed() {
        _uiState.value.selectedFeed?.let { loadEpisodesForFeed(it) }
    }

    fun nextPage() {
        val current = _uiState.value.currentPage
        val total = _uiState.value.totalPages
        if (current < total) {
            val nextPage = current + 1
            val paged = getPageSlice(_uiState.value.allEpisodes, nextPage, ITEMS_PER_PAGE)
            _uiState.update {
                it.copy(currentPage = nextPage, pagedEpisodes = paged)
            }
        }
    }

    fun prevPage() {
        val current = _uiState.value.currentPage
        if (current > 1) {
            val prevPage = current - 1
            val paged = getPageSlice(_uiState.value.allEpisodes, prevPage, ITEMS_PER_PAGE)
            _uiState.update {
                it.copy(currentPage = prevPage, pagedEpisodes = paged)
            }
        }
    }

    private fun getPageSlice(list: List<Episode>, page: Int, pageSize: Int): List<Episode> {
        val startIndex = (page - 1) * pageSize
        if (startIndex >= list.size) return emptyList()
        val endIndex = (startIndex + pageSize).coerceAtMost(list.size)
        return list.subList(startIndex, endIndex)
    }

    fun addSubscription(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入播客名称或链接") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingFeed = true, errorMessage = null) }
            try {
                val result = withContext(ioDispatcher) {
                    resolverAgent.resolveAndFetch(input, _uiState.value.settings)
                }

                // Add to local preferences
                prefsManager.addFeed(result.feed)
                val updatedFeeds = prefsManager.getSubscribedFeeds()
                prefsManager.setSelectedFeedId(result.feed.id)

                val episodes = result.episodes.take(20)
                val progresses = withContext(ioDispatcher) {
                    episodes.mapNotNull { ep -> prefsManager.getProgress(ep.id) }
                        .associateBy { it.episodeId }
                }
                val totalPages = max(1, ceil(episodes.size.toDouble() / ITEMS_PER_PAGE).toInt())
                val paged = getPageSlice(episodes, 1, ITEMS_PER_PAGE)

                _uiState.update {
                    it.copy(
                        subscribedFeeds = updatedFeeds,
                        selectedFeed = result.feed,
                        allEpisodes = episodes,
                        episodeProgressMap = progresses,
                        currentPage = 1,
                        totalPages = totalPages,
                        pagedEpisodes = paged,
                        isResolvingFeed = false,
                        isAddFeedDialogOpen = false,
                        searchResolveResult = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve and subscribe to '$input'", e)
                _uiState.update {
                    it.copy(
                        isResolvingFeed = false,
                        errorMessage = "添加订阅失败: ${e.message ?: "解析失败，请检查输入或网络"}"
                    )
                }
            }
        }
    }

    fun searchAndResolve(query: String) {
        val input = query.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingFeed = true, errorMessage = null) }
            try {
                val result = withContext(ioDispatcher) {
                    resolverAgent.resolveAndFetch(input, _uiState.value.settings)
                }
                val trimmedResult = result.copy(episodes = result.episodes.take(20))
                _uiState.update {
                    it.copy(
                        isResolvingFeed = false,
                        searchResolveResult = trimmedResult
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve search '$input'", e)
                _uiState.update {
                    it.copy(
                        isResolvingFeed = false,
                        errorMessage = "智能解析失败: ${e.message ?: "未找到匹配播客"}"
                    )
                }
            }
        }
    }

    fun subscribeFromExplore(feed: PodcastFeed) {
        prefsManager.addFeed(feed)
        val updatedFeeds = prefsManager.getSubscribedFeeds()
        _uiState.update {
            it.copy(
                subscribedFeeds = updatedFeeds,
                searchResolveResult = null
            )
        }
    }

    fun clearSearchResolveResult() {
        _uiState.update { it.copy(searchResolveResult = null) }
    }

    fun removeFeed(feedId: String) {
        prefsManager.removeFeed(feedId)
        val updatedFeeds = prefsManager.getSubscribedFeeds()
        val selectedId = prefsManager.getSelectedFeedId()
        val nextFeed = updatedFeeds.find { it.id == selectedId } ?: updatedFeeds.firstOrNull()

        _uiState.update {
            it.copy(
                subscribedFeeds = updatedFeeds,
                selectedFeed = nextFeed,
                allEpisodes = emptyList(),
                pagedEpisodes = emptyList(),
                currentPage = 1,
                totalPages = 1,
                isDetailOpen = if (it.selectedFeed?.id == feedId) false else it.isDetailOpen
            )
        }

        nextFeed?.let { loadEpisodesForFeed(it) }
    }

    fun openAddFeedDialog() {
        _uiState.update { it.copy(isAddFeedDialogOpen = true, errorMessage = null) }
    }

    fun closeAddFeedDialog() {
        _uiState.update { it.copy(isAddFeedDialogOpen = false, isResolvingFeed = false) }
    }

    fun openSettingsDialog() {
        _uiState.update { it.copy(isSettingsDialogOpen = true) }
    }

    fun closeSettingsDialog() {
        _uiState.update { it.copy(isSettingsDialogOpen = false) }
    }

    fun saveSettings(newSettings: AppSettings) {
        prefsManager.saveSettings(newSettings)
        _uiState.update { it.copy(settings = newSettings, isSettingsDialogOpen = false) }
        refreshCurrentFeed()
    }

    fun getProgressForEpisode(episodeId: String): PlaybackProgress? {
        return _uiState.value.episodeProgressMap[episodeId] ?: prefsManager.getProgress(episodeId)
    }

    fun cacheEpisode(episode: Episode) {
        viewModelScope.launch {
            val result = AudioCacheManager.cacheEpisodeAudio(
                context = getApplication(),
                episodeId = episode.id,
                audioUrl = episode.audioUrl
            )
            refreshCacheSizes()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "缓存失败: ${result.exceptionOrNull()?.message ?: "网络错误"}") }
            }
        }
    }

    fun isEpisodeCached(episodeId: String, audioUrl: String): Boolean {
        return AudioCacheManager.isEpisodeCached(getApplication(), episodeId, audioUrl)
    }

    fun refreshCacheSizes() {
        viewModelScope.launch(ioDispatcher) {
            val audioSize = AudioCacheManager.getAudioCacheSizeBytes(getApplication())
            val feedSize = feedCacheManager.getCacheSizeBytes()
            val imgSize = ImageLoader.getDiskCacheSizeBytes(getApplication())
            _uiState.update {
                it.copy(
                    audioCacheSizeBytes = audioSize,
                    feedCacheSizeBytes = feedSize,
                    imageCacheSizeBytes = imgSize
                )
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch(ioDispatcher) {
            AudioCacheManager.clearAudioCache(getApplication())
            refreshCacheSizes()
        }
    }

    fun clearAllCaches() {
        viewModelScope.launch(ioDispatcher) {
            AudioCacheManager.clearAudioCache(getApplication())
            feedCacheManager.clearCache()
            ImageLoader.clearCache(getApplication())
            refreshCacheSizes()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

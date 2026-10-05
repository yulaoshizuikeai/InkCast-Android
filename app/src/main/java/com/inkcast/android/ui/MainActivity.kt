package com.inkcast.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.inkcast.android.data.local.PreferencesManager
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PodcastFeed
import com.inkcast.android.playback.PlaybackController
import com.inkcast.android.ui.components.FullPlayerBottomSheet
import com.inkcast.android.ui.components.ModernAsyncImage
import com.inkcast.android.ui.components.ModernEpisodeRow
import com.inkcast.android.ui.components.ModernMiniPlayer
import com.inkcast.android.ui.components.ModernPodcastCard
import com.inkcast.android.ui.theme.InkTheme
import com.inkcast.android.util.ImageLoader

/**
 * Format timestamp in milliseconds to HH:MM:SS or MM:SS.
 */
fun formatTime(ms: Long, forceHours: Boolean = false): String {
    if (ms <= 0L) return if (forceHours) "00:00:00" else "00:00"
    val totalSeconds = ms / 1000L
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return if (h > 0 || forceHours) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var playbackController: PlaybackController

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyHighRefreshRate()
        playbackController = PlaybackController(this)

        checkNotificationPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            val isDark = when (uiState.settings.darkMode) {
                true -> true
                false -> false
                else -> isSystemInDarkTheme()
            }

            InkTheme(
                darkTheme = isDark,
                dynamicColor = uiState.settings.dynamicColor
            ) {
                ModernInkCastApp(
                    viewModel = viewModel,
                    playbackController = playbackController
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighRefreshRate()
    }

    private fun applyHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lp = window.attributes
            lp.preferredRefreshRate = 120f
            window.attributes = lp
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::playbackController.isInitialized) {
            playbackController.release()
        }
    }
}

@Composable
fun ModernInkCastApp(
    viewModel: MainViewModel,
    playbackController: PlaybackController
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by playbackController.isPlaying.collectAsState()
    val currentPlayingEpisode by playbackController.currentEpisode.collectAsState()
    val playbackSpeed by playbackController.playbackSpeed.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Intercept back button if in PodcastDetailScreen
    BackHandler(enabled = uiState.isDetailOpen) {
        viewModel.closePodcastDetail()
    }

    val onTogglePlayPause = remember(playbackController) { { playbackController.togglePlayPause() } }
    val onOpenPlayerSheet = remember(viewModel) { { viewModel.openPlayerSheet() } }
    val onClosePlayerSheet = remember(viewModel) { { viewModel.closePlayerSheet() } }
    val onSeekTo = remember(playbackController) { { pos: Long -> playbackController.seekTo(pos) } }
    val onSeekBack15 = remember(playbackController) { { playbackController.seekBack15() } }
    val onSeekForward30 = remember(playbackController) { { playbackController.seekForward30() } }
    val onCycleSpeed = remember(playbackController) { { playbackController.cyclePlaybackSpeed() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (currentPlayingEpisode != null) 160.dp else 90.dp)
            )
        }
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // LAYER 1: Full-bleed scrollable content that flows under the floating dock
            if (uiState.isDetailOpen && uiState.selectedFeed != null) {
                // Dedicated Podcast Detail Screen (Smooth lazy list of episodes)
                val selectedFeed = uiState.selectedFeed!!
                ModernPodcastDetailScreen(
                    feed = selectedFeed,
                    episodes = uiState.allEpisodes,
                    isLoading = uiState.isLoading,
                    currentPlayingEpisode = currentPlayingEpisode,
                    isPlaying = isPlaying,
                    getProgressForEpisode = remember(viewModel) { { viewModel.getProgressForEpisode(it) } },
                    onPlayEpisode = remember(playbackController) { { playbackController.playEpisode(it) } },
                    onRefresh = remember(viewModel) { { viewModel.refreshCurrentFeed() } },
                    onBack = remember(viewModel) { { viewModel.closePodcastDetail() } },
                    onUnsubscribe = remember(viewModel, selectedFeed.id) { { viewModel.removeFeed(selectedFeed.id) } }
                )
            } else {
                when (uiState.currentTab) {
                    NavigationTab.LIBRARY -> {
                        ModernLibraryTab(
                            subscribedFeeds = uiState.subscribedFeeds,
                            onSelectFeed = remember(viewModel) { { feed -> viewModel.openPodcastDetail(feed) } },
                            onAddClick = remember(viewModel) { { viewModel.openAddFeedDialog() } },
                            onGoToExplore = remember(viewModel) { { viewModel.setCurrentTab(NavigationTab.EXPLORE) } },
                            onUnsubscribe = remember(viewModel) { { feedId -> viewModel.removeFeed(feedId) } }
                        )
                    }
                    NavigationTab.EXPLORE -> {
                        ModernExploreTab(
                            isResolving = uiState.isResolvingFeed,
                            searchResult = uiState.searchResolveResult,
                            subscribedFeeds = uiState.subscribedFeeds,
                            onSearch = remember(viewModel) { { query -> viewModel.searchAndResolve(query) } },
                            onSubscribeFeed = remember(viewModel) { { feed -> viewModel.subscribeFromExplore(feed) } },
                            onClearResult = remember(viewModel) { { viewModel.clearSearchResolveResult() } }
                        )
                    }
                    NavigationTab.SETTINGS -> {
                        ModernSettingsTab(
                            settings = uiState.settings,
                            onSaveSettings = { viewModel.saveSettings(it) }
                        )
                    }
                }
            }

            // LAYER 2: True Floating Dock (MiniPlayer + Translucent NavigationBar)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Persistent Floating MiniPlayer docked above NavigationBar
                ModernMiniPlayer(
                    episode = currentPlayingEpisode,
                    feedTitle = uiState.selectedFeed?.title.orEmpty(),
                    isPlaying = isPlaying,
                    playbackController = playbackController,
                    onTogglePlayPause = onTogglePlayPause,
                    onExpand = onOpenPlayerSheet
                )

                // Translucent Material Design 3 NavigationBar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == NavigationTab.LIBRARY && !uiState.isDetailOpen,
                        onClick = { viewModel.setCurrentTab(NavigationTab.LIBRARY) },
                        icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "资料库") },
                        label = { Text("资料库") }
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == NavigationTab.EXPLORE && !uiState.isDetailOpen,
                        onClick = { viewModel.setCurrentTab(NavigationTab.EXPLORE) },
                        icon = { Icon(Icons.Filled.Explore, contentDescription = "探索发现") },
                        label = { Text("探索发现") }
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == NavigationTab.SETTINGS && !uiState.isDetailOpen,
                        onClick = { viewModel.setCurrentTab(NavigationTab.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                        label = { Text("设置") }
                    )
                }
            }
        }
    }

    // Full Player ModalBottomSheet
    if (uiState.isPlayerSheetExpanded && currentPlayingEpisode != null) {
        FullPlayerBottomSheet(
            episode = currentPlayingEpisode,
            feed = uiState.selectedFeed,
            isPlaying = isPlaying,
            playbackController = playbackController,
            playbackSpeed = playbackSpeed,
            onDismissRequest = onClosePlayerSheet,
            onTogglePlayPause = onTogglePlayPause,
            onSeekTo = onSeekTo,
            onSeekBack15 = onSeekBack15,
            onSeekForward30 = onSeekForward30,
            onCycleSpeed = onCycleSpeed
        )
    }

    // Add Feed Dialog
    if (uiState.isAddFeedDialogOpen) {
        ModernAddFeedDialog(
            isResolving = uiState.isResolvingFeed,
            onDismiss = { viewModel.closeAddFeedDialog() },
            onConfirm = { input -> viewModel.addSubscription(input) }
        )
    }
}

/**
 * Modern Library Tab: 2-column waterfall grid with smooth gestures and big covers.
 */
@Composable
fun ModernLibraryTab(
    subscribedFeeds: List<PodcastFeed>,
    onSelectFeed: (PodcastFeed) -> Unit,
    onAddClick: () -> Unit,
    onGoToExplore: () -> Unit,
    onUnsubscribe: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Library Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "资料库",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "已订阅 ${subscribedFeeds.size} 档播客",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "添加订阅")
            }
        }

        if (subscribedFeeds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "资料库暂无播客",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "前往【探索发现】收听热门推荐，或粘贴链接添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onGoToExplore) {
                        Text("去探索发现")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 170.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(subscribedFeeds, key = { it.id }) { feed ->
                    val onFeedClick = remember(feed.id) { { onSelectFeed(feed) } }
                    val onFeedUnsubscribe = remember(feed.id) { { onUnsubscribe(feed.id) } }
                    ModernPodcastCard(
                        feed = feed,
                        onClick = onFeedClick,
                        onUnsubscribe = onFeedUnsubscribe
                    )
                }
            }
        }
    }
}

/**
 * Modern Explore Tab: Smart Search Bar, Fast Resolver, Hot Feeds recommendation list.
 */
@Composable
fun ModernExploreTab(
    isResolving: Boolean,
    searchResult: com.inkcast.android.data.model.FeedResolveResult?,
    subscribedFeeds: List<PodcastFeed>,
    onSearch: (String) -> Unit,
    onSubscribeFeed: (PodcastFeed) -> Unit,
    onClearResult: () -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val subscribedIds = remember(subscribedFeeds) { subscribedFeeds.map { it.id }.toSet() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = statusBarTop + 12.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "探索发现",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "支持小宇宙、喜马拉雅链接、免翻反查播客名或标准 RSS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Apple Music Style Capsule Search Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (queryText.isEmpty()) {
                                Text(
                                    text = "搜索播客或输入小宇宙/RSS...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            BasicTextField(
                                value = queryText,
                                onValueChange = { queryText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                    keyboardType = KeyboardType.Uri
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (queryText.isNotBlank() && !isResolving) {
                                            onSearch(queryText.trim())
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (queryText.isNotEmpty()) {
                            IconButton(
                                onClick = { queryText = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Apple-style external animated search button
                AnimatedVisibility(visible = queryText.isNotBlank()) {
                    Row {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onSearch(queryText.trim()) },
                            enabled = !isResolving,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("搜索", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Quick Search Tags with smooth horizontal scrolling
        item {
            Text(
                text = "热门快速搜索",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            val sampleTags = remember {
                listOf("声动早咖啡", "忽左忽右", "Life Kit", "Hidden Brain", "TED Radio Hour", "6 Minute English")
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(sampleTags) { sample ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            queryText = sample
                            onSearch(sample)
                        },
                        label = { Text(sample) }
                    )
                }
            }
        }

        // Search Result Card
        if (searchResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "解析成功",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = onClearResult) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ModernAsyncImage(
                                url = searchResult.feed.artworkUrl,
                                contentDescription = searchResult.feed.title,
                                modifier = Modifier.size(72.dp),
                                cornerRadius = 12.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = searchResult.feed.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (searchResult.feed.author.isNotBlank()) searchResult.feed.author else "包含 ${searchResult.episodes.size} 期单集",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val isSubscribed = searchResult.feed.id in subscribedIds
                        Button(
                            onClick = { onSubscribeFeed(searchResult.feed) },
                            enabled = !isSubscribed,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubscribed) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSubscribed) "已添加到资料库" else "立即订阅此播客")
                        }
                    }
                }
            }
        }

        // Hot Recommended Feeds
        item {
            Text(
                text = "热门精选推荐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(PreferencesManager.PRESET_FEEDS, key = { it.id }) { presetFeed ->
            val isSubscribed = presetFeed.id in subscribedIds
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernAsyncImage(
                        url = presetFeed.artworkUrl,
                        contentDescription = presetFeed.title,
                        modifier = Modifier.size(60.dp),
                        cornerRadius = 10.dp,
                        targetSizePx = 150
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = presetFeed.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = presetFeed.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = { onSubscribeFeed(presetFeed) },
                        enabled = !isSubscribed,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isSubscribed) "已订阅" else "订阅")
                    }
                }
            }
        }
    }
}

/**
 * Modern Settings Tab: Dynamic theme, overseas proxy configuration, cache management.
 */
@Composable
fun ModernSettingsTab(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit
) {
    var rsshubUrl by remember(settings) { mutableStateOf(settings.rsshubBaseUrl) }
    var cfProxyUrl by remember(settings) { mutableStateOf(settings.cfWorkerUrl) }
    var darkModeOption by remember(settings) { mutableStateOf(settings.darkMode) }
    var dynamicColorEnabled by remember(settings) { mutableStateOf(settings.dynamicColor) }

    val context = LocalContext.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = statusBarTop + 12.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "定制您的 Material 3 与音频网络体验",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Appearance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "外观与主题 (Material You)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "暗色模式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = darkModeOption == null,
                            onClick = {
                                darkModeOption = null
                                onSaveSettings(settings.copy(darkMode = null))
                            },
                            label = { Text("跟随系统") }
                        )
                        FilterChip(
                            selected = darkModeOption == false,
                            onClick = {
                                darkModeOption = false
                                onSaveSettings(settings.copy(darkMode = false))
                            },
                            label = { Text("浅色") }
                        )
                        FilterChip(
                            selected = darkModeOption == true,
                            onClick = {
                                darkModeOption = true
                                onSaveSettings(settings.copy(darkMode = true))
                            },
                            label = { Text("深色") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "动态色彩 (Dynamic Color)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "基于 Android 12~16 系统壁纸动态生成强调色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColorEnabled,
                            onCheckedChange = {
                                dynamicColorEnabled = it
                                onSaveSettings(settings.copy(dynamicColor = it))
                            }
                        )
                    }
                }
            }
        }

        // Network Proxy Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "网络与加速节点",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rsshubUrl,
                        onValueChange = { rsshubUrl = it },
                        label = { Text("RSSHub 镜像节点") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cfProxyUrl,
                        onValueChange = { cfProxyUrl = it },
                        label = { Text("Cloudflare Worker 代理根域名") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "* 配置 Worker 代理后，海外常见源 (NPR/BBC/Simplecast/Megaphone等) 的 RSS 与音频流将自动通过边缘节点分流加速。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                rsshubUrl = AppSettings.DEFAULT_RSSHUB_URL
                                cfProxyUrl = AppSettings.DEFAULT_CF_WORKER_URL
                                onSaveSettings(
                                    settings.copy(
                                        rsshubBaseUrl = AppSettings.DEFAULT_RSSHUB_URL,
                                        cfWorkerUrl = AppSettings.DEFAULT_CF_WORKER_URL
                                    )
                                )
                                Toast.makeText(context, "已恢复默认配置", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("恢复默认")
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                onSaveSettings(
                                    settings.copy(
                                        rsshubBaseUrl = rsshubUrl.trim(),
                                        cfWorkerUrl = cfProxyUrl.trim(),
                                        darkMode = darkModeOption,
                                        dynamicColor = dynamicColorEnabled
                                    )
                                )
                                Toast.makeText(context, "网络配置已保存", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("保存设置")
                        }
                    }
                }
            }
        }

        // About Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "关于 InkCast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "版本: 2.1.0 (Native Android 16 Edition)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "技术栈: Jetpack Compose M3 Expressive • Media3 ExoPlayer • Edge-to-Edge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            ImageLoader.clearCache()
                            Toast.makeText(context, "已清除封面缓存", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("清除图片缓存")
                    }
                }
            }
        }
    }
}

/**
 * Modern Podcast Detail Screen: Large Cover, Author, Description, and smooth scrolling Episode List.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPodcastDetailScreen(
    feed: PodcastFeed,
    episodes: List<Episode>,
    isLoading: Boolean,
    currentPlayingEpisode: Episode?,
    isPlaying: Boolean,
    getProgressForEpisode: (String) -> com.inkcast.android.data.model.PlaybackProgress?,
    onPlayEpisode: (Episode) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = TopAppBarDefaults.windowInsets,
            title = {
                Text(
                    text = feed.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "取消订阅",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Podcast Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernAsyncImage(
                            url = feed.artworkUrl,
                            contentDescription = feed.title,
                            modifier = Modifier
                                .size(96.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            cornerRadius = 12.dp,
                            targetSizePx = 400
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feed.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (feed.author.isNotBlank()) feed.author else "未知作者",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "单集数量: ${episodes.size} 期",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (feed.description.isNotBlank()) {
                        Text(
                            text = feed.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单集列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (episodes.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无单集",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(episodes, key = { it.id }) { ep ->
                    val isPlayingThis = (ep.id == currentPlayingEpisode?.id) && isPlaying
                    val isCurrent = (ep.id == currentPlayingEpisode?.id)
                    val progress = getProgressForEpisode(ep.id)
                    val onPlayClick = remember(ep.id) { { onPlayEpisode(ep) } }

                    ModernEpisodeRow(
                        episode = ep,
                        isCurrentPlaying = isCurrent,
                        progress = progress,
                        onPlayClick = onPlayClick
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("取消订阅确认") },
            text = { Text("确定要取消订阅《${feed.title}》吗？取消后单集将从资料库中移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onUnsubscribe()
                    }
                ) {
                    Text("确定退订", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("保留")
                }
            }
        )
    }
}

/**
 * Modern Dialog to Add/Resolve Podcast Feed.
 */
@Composable
fun ModernAddFeedDialog(
    isResolving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加播客订阅") },
        text = {
            Column {
                Text(
                    text = "支持：小宇宙分享文案、喜马拉雅专辑链接、播客中文/英文名称（免翻反查）或原生 RSS 链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("粘贴链接或输入播客名称...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { inputText = "声动早咖啡" },
                        label = { Text("声动早咖啡") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { inputText = "忽左忽右" },
                        label = { Text("忽左忽右") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(inputText) },
                enabled = !isResolving && inputText.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("解析中...")
                } else {
                    Text("智能解析并订阅")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

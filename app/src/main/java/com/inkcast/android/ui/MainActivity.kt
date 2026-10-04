package com.inkcast.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.inkcast.android.data.model.AppSettings
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PodcastFeed
import com.inkcast.android.playback.PlaybackController
import com.inkcast.android.ui.components.EpisodeItemRow
import com.inkcast.android.ui.components.InkButton
import com.inkcast.android.ui.components.InkCard
import com.inkcast.android.ui.components.InkDialog
import com.inkcast.android.ui.components.InkPaginationBar
import com.inkcast.android.ui.components.InkTextField
import com.inkcast.android.ui.theme.InkBlack
import com.inkcast.android.ui.theme.InkGrayDark
import com.inkcast.android.ui.theme.InkTheme
import com.inkcast.android.ui.theme.InkWhite

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var playbackController: PlaybackController

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackController = PlaybackController(this)

        checkNotificationPermission()

        setContent {
            InkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = InkWhite
                ) {
                    InkCastMainScreen(
                        viewModel = viewModel,
                        playbackController = playbackController
                    )
                }
            }
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
        playbackController.release()
    }
}

@Composable
fun InkCastMainScreen(
    viewModel: MainViewModel,
    playbackController: PlaybackController
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by playbackController.isPlaying.collectAsState()
    val currentPlayingEpisode by playbackController.currentEpisode.collectAsState()
    val steppedPositionMs by playbackController.steppedPositionMs.collectAsState()
    val durationMs by playbackController.durationMs.collectAsState()
    val playbackSpeed by playbackController.playbackSpeed.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InkWhite)
    ) {
        // 1. Top Header & Subscriptions Bar
        InkHeaderBar(
            subscribedFeeds = uiState.subscribedFeeds,
            selectedFeed = uiState.selectedFeed,
            onSelectFeed = { viewModel.selectFeed(it) },
            onAddClick = { viewModel.openAddFeedDialog() },
            onSettingsClick = { viewModel.openSettingsDialog() },
            onRefreshClick = { viewModel.refreshCurrentFeed() }
        )

        // 2. Main Content Area (Episodes Paged List)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        InkCard {
                            Text(
                                text = "【 正在读取单集数据，请稍候... 】",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = InkBlack
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        InkCard {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "提示信息",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.errorMessage ?: "",
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                InkButton(
                                    text = "重试 / 刷新",
                                    onClick = { viewModel.refreshCurrentFeed() }
                                )
                            }
                        }
                    }
                }

                uiState.pagedEpisodes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        InkCard {
                            Text(
                                text = "暂无单集内容，请点击顶部 [+ 订阅] 添加播客",
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Fixed 6-8 items vertical discrete layout
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.pagedEpisodes.forEach { episode ->
                                val isCurrentlyPlayingThis = currentPlayingEpisode?.id == episode.id
                                val progress = viewModel.getProgressForEpisode(episode.id)
                                EpisodeItemRow(
                                    episode = episode,
                                    isCurrentPlaying = isCurrentlyPlayingThis,
                                    progress = progress,
                                    onPlayClick = {
                                        playbackController.playEpisode(episode)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Physical Pagination Bar
                        InkPaginationBar(
                            currentPage = uiState.currentPage,
                            totalPages = uiState.totalPages,
                            totalItems = uiState.allEpisodes.size,
                            onPrevPage = { viewModel.prevPage() },
                            onNextPage = { viewModel.nextPage() }
                        )
                    }
                }
            }
        }

        // 3. Persistent Stepped Bottom Playback Bar (>= 48dp buttons, 10s stepped refresh)
        InkBottomPlaybackBar(
            currentEpisode = currentPlayingEpisode,
            isPlaying = isPlaying,
            steppedPositionMs = steppedPositionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            onPlayPauseClick = { playbackController.togglePlayPause() },
            onSeekBack15 = { playbackController.seekBack15() },
            onSeekForward30 = { playbackController.seekForward30() },
            onCycleSpeed = { playbackController.cyclePlaybackSpeed() }
        )
    }

    // Add Feed Dialog
    if (uiState.isAddFeedDialogOpen) {
        AddFeedDialog(
            isResolving = uiState.isResolvingFeed,
            onDismiss = { viewModel.closeAddFeedDialog() },
            onConfirm = { input -> viewModel.addSubscription(input) }
        )
    }

    // Settings Dialog
    if (uiState.isSettingsDialogOpen) {
        SettingsDialog(
            currentSettings = uiState.settings,
            onDismiss = { viewModel.closeSettingsDialog() },
            onSave = { newSettings -> viewModel.saveSettings(newSettings) }
        )
    }
}

/**
 * Top Header Bar containing title, action buttons and subscription selector chips.
 */
@Composable
fun InkHeaderBar(
    subscribedFeeds: List<PodcastFeed>,
    selectedFeed: PodcastFeed?,
    onSelectFeed: (String) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, InkBlack), RectangleShape)
            .background(InkWhite)
            .padding(8.dp)
    ) {
        // App Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INKCAST",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    color = InkBlack
                )
                Text(
                    text = "E-INK NATIVE v2.0",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkGrayDark,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InkButton(
                    text = "+ 订阅",
                    onClick = onAddClick,
                    minHeight = 40
                )
                InkButton(
                    text = "刷新",
                    onClick = onRefreshClick,
                    minHeight = 40
                )
                InkButton(
                    text = "设置",
                    onClick = onSettingsClick,
                    minHeight = 40
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subscriptions Chip Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subscribedFeeds.forEach { feed ->
                val isSelected = feed.id == selectedFeed?.id
                InkButton(
                    text = feed.title,
                    onClick = { onSelectFeed(feed.id) },
                    inverted = isSelected,
                    minHeight = 38
                )
            }
        }
    }
}

/**
 * Persistent Stepped E-ink Bottom Playback Bar.
 * Strictly no dragging Seekbar, low-frequency 10-second stepped progress display,
 * and large physical control buttons (>= 48dp).
 */
@Composable
fun InkBottomPlaybackBar(
    currentEpisode: Episode?,
    isPlaying: Boolean,
    steppedPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlayPauseClick: () -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward30: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val durationToUse = if (durationMs > 0) durationMs else (currentEpisode?.durationSeconds ?: 0L) * 1000L
    val posStr = formatTime(steppedPositionMs)
    val durStr = formatTime(durationToUse)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(3.dp, InkBlack), RectangleShape)
            .background(InkWhite)
            .padding(10.dp)
    ) {
        // Episode Title
        Text(
            text = currentEpisode?.title ?: "【 暂无播放单集，请选择单集播放 】",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = InkBlack
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Static Stepped Time Display (Discrete 10-second updates)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "进度: $posStr / $durStr",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = InkBlack
            )
            Text(
                text = if (isPlaying) "● 播放中" else "○ 已暂停",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = InkBlack
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Physical Buttons Row (All >= 48dp height)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // -15s Seek
            InkButton(
                text = "-15s",
                onClick = onSeekBack15,
                minHeight = 48,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Play / Pause (Physical Inverted Contrast)
            InkButton(
                text = if (isPlaying) "[ 暂停 ]" else "[ 播放 ]",
                onClick = onPlayPauseClick,
                inverted = isPlaying,
                minHeight = 48,
                modifier = Modifier.weight(1.5f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // +30s Seek
            InkButton(
                text = "+30s",
                onClick = onSeekForward30,
                minHeight = 48,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Speed toggle (1.0x / 1.2x / 1.5x / 2.0x)
            InkButton(
                text = "${playbackSpeed}x",
                onClick = onCycleSpeed,
                minHeight = 48,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Smart Feed Resolver Input Dialog.
 * Supports Xiaoyuzhou shared text, Ximalaya album URL, Apple CN Podcast search keywords, and direct RSS links.
 */
@Composable
fun AddFeedDialog(
    isResolving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    InkDialog(
        title = "添加播客订阅 (智能识别)",
        onDismissRequest = onDismiss
    ) {
        Column {
            Text(
                text = "支持：小宇宙分享文案、喜马拉雅专辑链接、播客中文/英文名称（免翻反查）或原生 RSS 链接",
                fontSize = 12.sp,
                color = InkGrayDark,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            InkTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = "粘贴小宇宙链接、输入播客名或标准 RSS...",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fast sample tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "快捷填充:", fontSize = 11.sp, color = InkGrayDark, modifier = Modifier.align(Alignment.CenterVertically))
                InkButton(
                    text = "声动早咖啡",
                    onClick = { inputText = "声动早咖啡" },
                    minHeight = 32
                )
                InkButton(
                    text = "忽左忽右",
                    onClick = { inputText = "忽左忽右" },
                    minHeight = 32
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                InkButton(
                    text = "取消",
                    onClick = onDismiss,
                    minHeight = 46,
                    modifier = Modifier.width(90.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                InkButton(
                    text = if (isResolving) "解析中..." else "智能解析并订阅",
                    onClick = { onConfirm(inputText) },
                    enabled = !isResolving && inputText.isNotBlank(),
                    inverted = true,
                    minHeight = 46
                )
            }
        }
    }
}

/**
 * Settings Dialog for RSSHub Mirror and Cloudflare Worker Proxy configuration.
 */
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var rsshubUrl by remember { mutableStateOf(currentSettings.rsshubBaseUrl) }
    var cfProxyUrl by remember { mutableStateOf(currentSettings.cfWorkerUrl) }

    InkDialog(
        title = "全局服务配置",
        onDismissRequest = onDismiss
    ) {
        Column {
            InkTextField(
                label = "RSSHub 镜像节点地址:",
                value = rsshubUrl,
                onValueChange = { rsshubUrl = it },
                placeholder = "https://rsshub.rssforever.com"
            )

            Spacer(modifier = Modifier.height(10.dp))

            InkTextField(
                label = "Cloudflare Worker 代理根域名 (海外源代理):",
                value = cfProxyUrl,
                onValueChange = { cfProxyUrl = it },
                placeholder = "例如: https://podcast-proxy.example.workers.dev (留空为直连)"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "* 配置 Worker 代理后，海外常见源 (NPR/BBC/Simplecast/Megaphone等) 的音频流将自动通过 Worker 进行分流加速。",
                fontSize = 11.sp,
                color = InkGrayDark,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InkButton(
                    text = "恢复默认",
                    onClick = {
                        rsshubUrl = AppSettings.DEFAULT_RSSHUB_URL
                        cfProxyUrl = ""
                    },
                    minHeight = 44
                )

                Row {
                    InkButton(
                        text = "取消",
                        onClick = onDismiss,
                        minHeight = 44,
                        modifier = Modifier.width(80.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    InkButton(
                        text = "保存配置",
                        onClick = {
                            onSave(
                                currentSettings.copy(
                                    rsshubBaseUrl = rsshubUrl.trim(),
                                    cfWorkerUrl = cfProxyUrl.trim()
                                )
                            )
                        },
                        inverted = true,
                        minHeight = 44,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

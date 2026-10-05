package com.inkcast.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inkcast.android.R
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.data.model.PodcastFeed
import com.inkcast.android.playback.PlaybackController

/**
 * Modern High-Resolution Async Image Component powered by Coil.
 * Handles automatic layout-aware downsampling, GPU Hardware Bitmaps,
 * and lifecycle-aware coroutine cancellation on scroll/fling.
 */
@Composable
fun ModernAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    targetSizePx: Int = 300,
    placeholderText: String = "CAST"
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(cornerRadius)
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Global Floating MiniPlayer docked directly above the Bottom Navigation Bar.
 * Features 500ms smooth continuous progress bar, marquee title scrolling,
 * and tap-to-expand into Full Player Sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernMiniPlayer(
    episode: Episode?,
    feedTitle: String,
    isPlaying: Boolean,
    playbackController: PlaybackController,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = episode != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (episode == null) return@AnimatedVisibility

        val coverUrl = episode.imageUrl.ifBlank { "" }
        val positionState = playbackController.positionMs.collectAsState()
        val durationState = playbackController.durationMs.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .clickable(onClick = onExpand),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small square rounded artwork
                    ModernAsyncImage(
                        url = coverUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 10.dp,
                        targetSizePx = 200
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title with smooth marquee and feed title
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = feedTitle.ifBlank { episode.pubDate },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Play / Pause Action Button
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Tiny smooth real-time progress indicator at the bottom edge.
                // Evaluated in draw phase with lambda to avoid recomposing ModernMiniPlayer.
                LinearProgressIndicator(
                    progress = {
                        val durMs = durationState.value.let { if (it > 0) it else (episode.durationSeconds * 1000L).coerceAtLeast(1L) }
                        (positionState.value.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

/**
 * Immersive Full-Screen Player ModalBottomSheet.
 * Features huge rounded artwork, smooth interactive slider, -15s/+30s skips,
 * speed selector, and expandable episode details.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerBottomSheet(
    episode: Episode?,
    feed: PodcastFeed?,
    isPlaying: Boolean,
    playbackController: PlaybackController,
    playbackSpeed: Float,
    onDismissRequest: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward30: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    if (episode == null) return

    val durationMs by playbackController.durationMs.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val durationToUse = if (durationMs > 0) durationMs else (episode.durationSeconds * 1000L).coerceAtLeast(1L)
    val coverUrl = episode.imageUrl.ifBlank { feed?.artworkUrl.orEmpty() }

    var showNotes by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 2.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Collapse Icon | Title | Actions (Speed + Notes Toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起播放器",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = feed?.title ?: "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Notes / Cover Toggle Button
                    FilledTonalButton(
                        onClick = { showNotes = !showNotes },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (showNotes) Icons.Filled.Image else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showNotes) "封面" else "简介",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Speed Button
                    FilledTonalButton(
                        onClick = onCycleSpeed,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${playbackSpeed}x", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center Display: Seamless toggle between 260dp Artwork and 260dp Scrollable Show Notes Card
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (showNotes) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "单集简介",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = episode.description.ifBlank { "暂无单集简介" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    ModernAsyncImage(
                        url = coverUrl,
                        contentDescription = episode.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showNotes = true },
                        cornerRadius = 24.dp,
                        targetSizePx = 800
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Episode Title with Marquee & Subtitle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (feed?.author?.isNotBlank() == true) "${feed.author} • ${episode.pubDate}" else episode.pubDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Smooth Interactive Slider isolated to prevent recomposing full bottom sheet
            PlayerProgressSlider(
                playbackController = playbackController,
                durationMs = durationToUse,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Core Playback Controls Row: -15s, Big Play/Pause FAB, +30s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seek Back 15s
                FilledTonalIconButton(
                    onClick = onSeekBack15,
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_replay_15),
                        contentDescription = "后退15秒",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Central Play/Pause FAB
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Seek Forward 30s
                FilledTonalIconButton(
                    onClick = onSeekForward30,
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward_30),
                        contentDescription = "快进30秒",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Isolated progress slider component.
 * Recomposes every 500ms and during drag WITHOUT triggering recomposition
 * of the parent FullPlayerBottomSheet (artwork, metadata, playback controls).
 */
@Composable
private fun PlayerProgressSlider(
    playbackController: PlaybackController,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionMs by playbackController.positionMs.collectAsState()
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggingSliderValue by remember { mutableFloatStateOf(0f) }

    val currentDisplayPosMs = if (isDraggingSlider) draggingSliderValue.toLong() else positionMs
    val sliderValue = if (isDraggingSlider) draggingSliderValue else positionMs.toFloat().coerceIn(0f, durationMs.toFloat())

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                isDraggingSlider = true
                draggingSliderValue = newValue
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                onSeekTo(draggingSliderValue.toLong())
            },
            valueRange = 0f..durationMs.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentDisplayPosMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Modern Card for browsing Podcasts in Library and Explore.
 */
@Composable
fun ModernPodcastCard(
    feed: PodcastFeed,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUnsubscribe: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernAsyncImage(
                    url = feed.artworkUrl,
                    contentDescription = feed.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 12.dp,
                    targetSizePx = 400
                )

                if (onUnsubscribe != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("取消订阅") },
                                onClick = {
                                    showMenu = false
                                    onUnsubscribe()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (feed.author.isNotBlank()) feed.author else "已订阅",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Modern Episode List Item with smooth ripple and listening state badge.
 */
@Composable
fun ModernEpisodeRow(
    episode: Episode,
    isCurrentPlaying: Boolean,
    progress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentPlaying) 3.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (episode.pubDate.isNotBlank()) {
                        Text(
                            text = episode.pubDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = episode.durationFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (progress != null && progress.progressPercent > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (progress.isFinished) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (progress.isFinished) "已播完" else "已听 ${progress.progressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (progress.isFinished) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledTonalIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isCurrentPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isCurrentPlaying) "暂停" else "播放",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

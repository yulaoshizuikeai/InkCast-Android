package com.inkcast.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inkcast.android.R
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.ui.theme.InkBlack
import com.inkcast.android.ui.theme.InkGrayDark
import com.inkcast.android.ui.theme.InkGrayLight
import com.inkcast.android.ui.theme.InkWhite

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

/**
 * High-contrast 1-bit physical button for E-ink displays.
 * Minimum touch target is 48.dp, border is 2.dp InkBlack.
 * Provides instantaneous inverted physical feedback upon press.
 * Zero emojis; supports native SVG vector drawables.
 */
@Composable
fun InkButton(
    text: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconResId: Int? = null,
    iconAtEnd: Boolean = false,
    inverted: Boolean = false,
    enabled: Boolean = true,
    minHeight: Int = 44
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Inverted logic: if pressed or inverted, show black bg with white text
    val activeInverted = (inverted || (enabled && isPressed))
    val bgColor = if (!enabled) InkWhite else if (activeInverted) InkBlack else InkWhite
    val contentColor = if (!enabled) InkGrayDark else if (activeInverted) InkWhite else InkBlack
    val borderColor = if (!enabled) InkGrayDark else InkBlack

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (text.isBlank()) minHeight.dp else 44.dp, minHeight = minHeight.dp)
            .border(BorderStroke(2.dp, borderColor), RectangleShape)
            .background(bgColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // No ripple animation
                onClick = onClick
            )
            .padding(horizontal = if (text.isBlank()) 6.dp else 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconResId != null && !iconAtEnd) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = text.ifBlank { null },
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (text.isNotBlank()) {
                if (iconResId != null && !iconAtEnd) {
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
                if (iconResId != null && iconAtEnd) {
                    Spacer(modifier = Modifier.width(5.dp))
                }
            }
            if (iconResId != null && iconAtEnd) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = text.ifBlank { null },
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 1-bit Container Card with 2.dp border and zero elevation.
 */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .border(BorderStroke(2.dp, InkBlack), RectangleShape)
            .background(if (inverted) InkBlack else InkWhite, RectangleShape)
            .padding(10.dp)
    ) {
        content()
    }
}

/**
 * High-contrast E-ink image loader with 2.dp border, zero animations, and fallback placeholder.
 * Retains podcast cover art ("注意保留播客封面").
 */
@Composable
fun EInkAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderText: String = "CAST",
    borderWidth: Int = 1,
    targetSizePx: Int = 400
) {
    Box(
        modifier = modifier
            .then(if (borderWidth > 0) Modifier.border(BorderStroke(borderWidth.dp, InkBlack), RectangleShape) else Modifier)
            .background(InkWhite, RectangleShape),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(false)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = placeholderText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = InkBlack
            )
        }
    }
}

/**
 * High-contrast Text Field with thick 2.dp border and pure black text.
 */
@Composable
fun InkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = InkBlack,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .border(BorderStroke(2.dp, InkBlack), RectangleShape)
                .background(InkWhite, RectangleShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    color = InkGrayDark,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = InkBlack,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(InkBlack),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * High-contrast discrete Dialog for E-ink displays (no blur, 2.dp border, zero elevation).
 */
@Composable
fun InkDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .border(BorderStroke(3.dp, InkBlack), RectangleShape)
                .background(InkWhite, RectangleShape)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = InkBlack
                    )
                    InkButton(
                        text = "",
                        iconResId = R.drawable.ic_close,
                        onClick = onDismissRequest,
                        minHeight = 34,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

/**
 * High-contrast 1-bit Physical Progress Bar for E-ink displays.
 * Pure black fill, 1.5dp black border, monospace time stamps, zero animations.
 * Supports direct discrete tap-to-seek.
 */
@Composable
fun InkProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false
) {
    val durationToUse = durationMs.coerceAtLeast(0L)
    val fraction = if (durationToUse > 0L) (positionMs.toFloat() / durationToUse.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    val hasHours = durationToUse >= 3600_000L || positionMs >= 3600_000L
    val posStr = formatTime(positionMs, forceHours = hasHours)
    val durStr = formatTime(durationToUse, forceHours = hasHours)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .border(BorderStroke(1.5.dp, InkBlack), RectangleShape)
                .background(InkWhite)
                .pointerInput(durationToUse) {
                    detectTapGestures { offset ->
                        if (durationToUse > 0L && size.width > 0) {
                            val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek((ratio * durationToUse).toLong())
                        }
                    }
                }
        ) {
            val totalWidth = maxWidth
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(totalWidth * fraction)
                        .background(InkBlack)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$posStr / $durStr",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = InkBlack
            )
            Text(
                text = if (isPlaying) "[$percent% 播放中]" else "[$percent% 暂停]",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = InkBlack
            )
        }
    }
}

/**
 * Discrete Physical Pagination Bar: Strictly replaces waterfall scrolling.
 */
@Composable
fun InkPaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
    unitName: String = "条"
) {
    val safeTotalPages = totalPages.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(InkBlack)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(InkWhite)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InkButton(
                text = "上一页",
                iconResId = R.drawable.ic_chevron_left,
                onClick = onPrevPage,
                enabled = currentPage > 1,
                minHeight = 38,
                modifier = Modifier.width(96.dp)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "第 $currentPage / $safeTotalPages 页",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = InkBlack
                )
                Text(
                    text = "共 $totalItems $unitName",
                    fontSize = 11.sp,
                    color = InkGrayDark
                )
            }

            InkButton(
                text = "下一页",
                iconResId = R.drawable.ic_chevron_right,
                iconAtEnd = true,
                onClick = onNextPage,
                enabled = currentPage < safeTotalPages,
                minHeight = 38,
                modifier = Modifier.width(96.dp)
            )
        }
    }
}

/**
 * Single episode list item designed with editorial index aesthetic, clean hairline divider,
 * and discrete play button.
 */
@Composable
fun EpisodeItemRow(
    episode: Episode,
    isCurrentPlaying: Boolean,
    progress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .background(if (isCurrentPlaying) InkBlack else InkWhite)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) InkWhite else InkBlack
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (episode.pubDate.isNotBlank()) {
                        Text(
                            text = episode.pubDate,
                            fontSize = 11.sp,
                            color = if (isCurrentPlaying) InkWhite.copy(alpha = 0.85f) else InkGrayDark,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = episode.durationFormatted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentPlaying) InkWhite else InkBlack,
                        fontFamily = FontFamily.Monospace
                    )
                    if (progress != null && progress.progressPercent > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (progress.isFinished) "[已播完]" else "[已播 ${progress.progressPercent}%]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentPlaying) InkWhite else InkBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            InkButton(
                text = if (isCurrentPlaying) "暂停" else "播放",
                iconResId = if (isCurrentPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                onClick = onPlayClick,
                inverted = !isCurrentPlaying,
                minHeight = 38,
                modifier = Modifier.defaultMinSize(minWidth = 72.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isCurrentPlaying) InkWhite.copy(alpha = 0.3f) else InkGrayLight)
        )
    }
}

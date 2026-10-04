package com.inkcast.android.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inkcast.android.data.model.Episode
import com.inkcast.android.data.model.PlaybackProgress
import com.inkcast.android.ui.theme.InkBlack
import com.inkcast.android.ui.theme.InkGrayDark
import com.inkcast.android.ui.theme.InkWhite

/**
 * High-contrast 1-bit physical button for E-ink displays.
 * Minimum touch target is 48.dp, border is 2.dp InkBlack.
 * Provides instantaneous inverted physical feedback upon press.
 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    enabled: Boolean = true,
    minHeight: Int = 48
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
            .defaultMinSize(minWidth = 48.dp, minHeight = minHeight.dp)
            .border(BorderStroke(2.dp, borderColor), RectangleShape)
            .background(bgColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // No ripple animation
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )
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
                        text = "✕",
                        onClick = onDismissRequest,
                        minHeight = 36,
                        modifier = Modifier.width(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, InkBlack), RectangleShape)
            .background(InkWhite, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        InkButton(
            text = "◀ 上一页",
            onClick = onPrevPage,
            enabled = currentPage > 1,
            minHeight = 44,
            modifier = Modifier.width(110.dp)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第 $currentPage / $totalPages 页",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = InkBlack
            )
            Text(
                text = "共 $totalItems 条",
                fontSize = 11.sp,
                color = InkGrayDark
            )
        }

        InkButton(
            text = "下一页 ▶",
            onClick = onNextPage,
            enabled = currentPage < totalPages,
            minHeight = 44,
            modifier = Modifier.width(110.dp)
        )
    }
}

/**
 * Single episode list item designed with physical border and discrete play button.
 */
@Composable
fun EpisodeItemRow(
    episode: Episode,
    isCurrentPlaying: Boolean,
    progress: PlaybackProgress?,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, InkBlack), RectangleShape)
            .background(if (isCurrentPlaying) InkBlack else InkWhite, RectangleShape)
            .padding(10.dp)
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
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) InkWhite else InkBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (episode.pubDate.isNotBlank()) {
                        Text(
                            text = episode.pubDate,
                            fontSize = 11.sp,
                            color = if (isCurrentPlaying) InkWhite else InkGrayDark,
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
                text = if (isCurrentPlaying) "播放中" else "▶ 播放",
                onClick = onPlayClick,
                inverted = !isCurrentPlaying, // Invert button contrast relative to row
                minHeight = 44,
                modifier = Modifier.defaultMinSize(minWidth = 75.dp)
            )
        }
    }
}

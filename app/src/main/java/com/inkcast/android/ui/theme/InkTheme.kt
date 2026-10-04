package com.inkcast.android.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val InkBlack = Color(0xFF000000)
val InkWhite = Color(0xFFFFFFFF)
val InkGrayLight = Color(0xFFEEEEEE)
val InkGrayDark = Color(0xFF666666)

private val InkColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = InkWhite,
    primaryContainer = InkBlack,
    onPrimaryContainer = InkWhite,
    secondary = InkBlack,
    onSecondary = InkWhite,
    background = InkWhite,
    onBackground = InkBlack,
    surface = InkWhite,
    onSurface = InkBlack,
    outline = InkBlack,
    surfaceVariant = InkWhite,
    onSurfaceVariant = InkBlack
)

private val InkTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = InkBlack
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = InkBlack
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = InkBlack
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = InkBlack
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = InkBlack
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = InkBlack
    )
)

private val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

/**
 * Modern Compose IndicationNodeFactory to completely suppress ripple and tap visual noise on E-ink screens.
 */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node() {}
    }
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

/**
 * InkTheme: E-ink specialized theme.
 * Completely disables smooth ripples, smooth animations, and soft shadows.
 * Everything is rendered in 1-bit high-contrast black and white.
 */
@Composable
fun InkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        // Remove system ripple animation entirely to prevent E-ink display ghosting
        LocalIndication provides NoIndication
    ) {
        MaterialTheme(
            colorScheme = InkColorScheme,
            typography = InkTypography,
            shapes = InkShapes,
            content = content
        )
    }
}

package com.puzzlequest.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.R

object QuestColors {
    val BgTop = Color(0xFF2C5C3E)
    val BgDeep = Color(0xFF0E2018)
    val BgMid = Color(0xFF173327)
    val BgHill = Color(0xFF20452F)
    val Cream = Color(0xFFF6F1E4)
    val CreamDim = Color(0xFFCFE3D4)
    val Gold = Color(0xFFE8C158)
    val Moss = Color(0xFF8FBF5C)
    val MossLight = Color(0xFFC7E29A)
    val Danger = Color(0xFFE2604F)
    val Ink = Color(0xFF1D2F4B)
    val VisualTile = Color(0xB8FFFFFF)

    // Parent dashboard light dataviz palette (matches VIZ in Charts.tsx)
    val VizSurface = Color(0xFFFCFCFB)
    val VizInk = Color(0xFF0B0B0B)
    val VizInk2 = Color(0xFF52514E)
    val VizMuted = Color(0xFF898781)
    val VizGrid = Color(0xFFE1E0D9)
    val VizBaseline = Color(0xFFC3C2B7)
    val VizSeries = Color(0xFF2A78D6)
    val VizSeriesLight = Color(0xFFB7D3F6)
    val VizGood = Color(0xFF0CA30C)
    val VizWarning = Color(0xFFFAB219)
}

val BalooDisplayFont = FontFamily(
    Font(R.font.baloo_2, FontWeight.SemiBold),
    Font(R.font.baloo_2, FontWeight.Bold),
    Font(R.font.baloo_2, FontWeight.ExtraBold),
)

val NunitoBodyFont = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
    Font(R.font.nunito, FontWeight.ExtraBold),
)

val PuzzleQuestTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NunitoBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BalooDisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NunitoBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

private val QuestColorScheme = darkColorScheme(
    primary = QuestColors.Gold,
    onPrimary = QuestColors.Ink,
    primaryContainer = QuestColors.BgHill,
    onPrimaryContainer = QuestColors.Cream,
    secondary = QuestColors.Moss,
    onSecondary = QuestColors.Ink,
    secondaryContainer = QuestColors.BgMid,
    onSecondaryContainer = QuestColors.MossLight,
    tertiary = QuestColors.Cream,
    onTertiary = QuestColors.Ink,
    background = QuestColors.BgDeep,
    onBackground = QuestColors.Cream,
    surface = QuestColors.BgMid,
    onSurface = QuestColors.Cream,
    surfaceVariant = QuestColors.BgHill,
    onSurfaceVariant = QuestColors.CreamDim,
    error = QuestColors.Danger,
    onError = QuestColors.Cream,
    outline = Color(0x26FFFFFF),
)

@Composable
fun PuzzleQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QuestColorScheme,
        typography = PuzzleQuestTypography,
        content = content,
    )
}

@Composable
fun QuestBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to QuestColors.BgTop,
                        0.45f to QuestColors.BgMid,
                        1.0f to QuestColors.BgDeep,
                    ),
                    center = Offset(540f, -120f),
                    radius = 1500f,
                ),
            ),
        content = content,
    )
}

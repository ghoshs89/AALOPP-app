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
    // Calm, low-glare eucalyptus-slate dusk surfaces (easy on the eyes, no harsh pitch-black)
    val BgTop = Color(0xFF2C3734)
    val BgDeep = Color(0xFF1C2422)
    val BgMid = Color(0xFF232C2A)
    val BgHill = Color(0xFF2E3A37)

    // Warm matte parchment & soft ink (zero glaring pure white)
    val Cream = Color(0xFFE6E1D5)
    val PaperSoft = Color(0xFFEFEBE1)
    val CreamDim = Color(0xFFB7C1BA)
    val Gold = Color(0xFFD6B97E)
    val Moss = Color(0xFF7FA87E)
    val MossLight = Color(0xFFB4CFB1)
    val Danger = Color(0xFFDF8B7F)
    val Ink = Color(0xFF242E38)
    val VisualTile = Color(0xCCECE7DC)

    // Parent dashboard soft warm-paper dataviz palette
    val VizSurface = Color(0xFFF3EFE6)
    val VizInk = Color(0xFF28302E)
    val VizInk2 = Color(0xFF555E5B)
    val VizMuted = Color(0xFF7F8783)
    val VizGrid = Color(0xFFDFDACE)
    val VizBaseline = Color(0xFFC4BFB2)
    val VizSeries = Color(0xFF4C7AA8)
    val VizSeriesLight = Color(0xFFC4D6E8)
    val VizGood = Color(0xFF4F8F63)
    val VizWarning = Color(0xFFD49B4B)

    // Soft pastel-warm confetti particle palette (joyful without neon glare)
    val ConfettiAmber = Color(0xFFDFB874)
    val ConfettiCoral = Color(0xFFD98A7C)
    val ConfettiSky = Color(0xFF7FB3C8)
    val ConfettiLavender = Color(0xFFA997C9)
    val ConfettiMint = Color(0xFF82BA9D)

    val ConfettiPalette = listOf(
        Gold,
        Moss,
        MossLight,
        Cream,
        ConfettiAmber,
        ConfettiCoral,
        ConfettiSky,
        ConfettiLavender,
        ConfettiMint,
    )
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
    onError = QuestColors.Ink,
    outline = Color(0x22E6E1D5),
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
                        0.50f to QuestColors.BgMid,
                        1.0f to QuestColors.BgDeep,
                    ),
                    center = Offset(540f, -120f),
                    radius = 1550f,
                ),
            ),
        content = content,
    )
}

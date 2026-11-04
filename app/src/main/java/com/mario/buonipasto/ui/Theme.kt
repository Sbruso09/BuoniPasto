package com.mario.buonipasto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mario.buonipasto.R

/** Palette iOS scura, la stessa di BuyForTime. */
object Ios {
    val accent = Color(0xFF5E5CE6)
    val accentPressed = Color(0xFF4B49D1)
    val background = Color(0xFF000000)
    val surface = Color(0xFF1C1C1E)
    val surfaceElevated = Color(0xFF2C2C2E)
    val fillSecondary = Color(0x51787880)
    val fillTertiary = Color(0x3D767680)
    val chrome = Color(0xE61C1C1E)
    val separator = Color(0x99545458)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0x99EBEBF5)
    val textTertiary = Color(0x4DEBEBF5)
    val green = Color(0xFF30D158)
    val red = Color(0xFFFF453A)
    val orange = Color(0xFFFF9F0A)
    val pink = Color(0xFFFF375F)
    val teal = Color(0xFF64D2FF)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Stili di testo iOS (Large Title, Headline, Body, Footnote...). */
object IosText {
    val largeTitle = TextStyle(fontFamily = Inter, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em)
    val title1 = TextStyle(fontFamily = Inter, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    val title2 = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.016).em)
    val title3 = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.012).em)
    val headline = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em)
    val callout = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val groupHeader = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.05.em)
}

private val Scuro = darkColorScheme(
    primary = Ios.accent,
    onPrimary = Color.White,
    primaryContainer = Ios.surfaceElevated,
    onPrimaryContainer = Ios.textPrimary,
    secondary = Ios.pink,
    onSecondary = Color.White,
    background = Ios.background,
    onBackground = Ios.textPrimary,
    surface = Ios.surface,
    onSurface = Ios.textPrimary,
    surfaceVariant = Ios.surfaceElevated,
    onSurfaceVariant = Ios.textSecondary,
    surfaceContainerLow = Ios.surface,
    surfaceContainer = Ios.surface,
    surfaceContainerHigh = Ios.surfaceElevated,
    surfaceContainerHighest = Ios.surfaceElevated,
    outline = Ios.separator,
    outlineVariant = Ios.separator,
    error = Ios.red,
)

private val Tipografia = Typography(
    displayLarge = IosText.largeTitle,
    displayMedium = IosText.largeTitle,
    displaySmall = IosText.title1,
    headlineLarge = IosText.title1,
    headlineMedium = IosText.title2,
    headlineSmall = IosText.title3,
    titleLarge = IosText.title3,
    titleMedium = IosText.headline,
    titleSmall = IosText.subhead,
    bodyLarge = IosText.body,
    bodyMedium = IosText.subhead,
    bodySmall = IosText.footnote,
    labelLarge = IosText.headline,
    labelMedium = IosText.footnote,
    labelSmall = IosText.caption,
)

@Composable
fun BuoniTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scuro, typography = Tipografia, content = content)
}

/** Ogni persona ha lo stesso colore su entrambi i telefoni (i membri sono ordinati per id). */
@Composable
fun personColor(index: Int): Color = if (index == 0) Ios.accent else Ios.pink

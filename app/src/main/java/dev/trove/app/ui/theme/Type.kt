package dev.trove.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.trove.app.R

/**
 * Google Sans Flex — open-source (OFL) since 2025, the M3 Expressive brand
 * typeface. Its rounded, geometric forms echo the shape system. Official
 * static weights from Google Fonts.
 */
val GoogleSansFlex = FontFamily(
    Font(R.font.googlesansflex_regular, FontWeight.Normal),
    Font(R.font.googlesansflex_medium, FontWeight.Medium),
    Font(R.font.googlesansflex_semibold, FontWeight.SemiBold),
    Font(R.font.googlesansflex_bold, FontWeight.Bold),
)

/** Google Sans Code — open-source monospaced sibling, used for code blocks. */
val GoogleSansCode = FontFamily(
    Font(R.font.googlesanscode, FontWeight.Normal),
    Font(R.font.googlesanscode, FontWeight.Medium),
    Font(R.font.googlesanscode, FontWeight.Bold),
)

/** OpenDyslexic — reader font option designed for dyslexic readers. */
val OpenDyslexic = FontFamily(
    Font(R.font.opendyslexic_regular, FontWeight.Normal),
    Font(R.font.opendyslexic_bold, FontWeight.Bold),
    Font(R.font.opendyslexic_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.opendyslexic_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

/**
 * Material 3 Expressive type scale: larger display sizes with tighter
 * tracking for that distinctive "expressive" editorial feel, set in
 * Google Sans Flex.
 */
val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.75).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

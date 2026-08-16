package dev.trove.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

/** Muted slate seed — calm, neutral default palette. */
private const val BRAND_SEED = 0xFF5D6672.toInt()

/**
 * Trove theme — Material 3 Expressive foundations:
 * 5-tone tonal palettes built with the official HCT color algorithm,
 * expressive shapes and typography, plus Sepia (e-paper) and True OLED
 * variants.
 */
@Composable
fun TroveTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    oled: Boolean,
    sepia: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor, oled, sepia) {
        when {
            sepia -> sepiaScheme()
            darkTheme && oled -> oledScheme(darkScheme())
            darkTheme -> darkScheme()
            else -> lightScheme()
        }.let { base ->
            // Dynamic color only for the standard light/dark modes.
            if (!sepia && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) {
                    if (oled) oledScheme(dynamicDarkColorScheme(context))
                    else dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                base
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}

/** Standard (non-dynamic) schemes from a muted slate seed. */
private fun lightScheme(): ColorScheme = troveScheme(BRAND_SEED, dark = false)

private fun darkScheme(): ColorScheme = troveScheme(BRAND_SEED, dark = true)

/**
 * Builds an M3 ColorScheme from a 5-tone palette set using the standard
 * Tonal Spot variant.
 */
fun troveScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(seed),
        isDark = dark,
        contrastLevel = 0.0,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        platform = DynamicScheme.Platform.PHONE,
    )
    val p = scheme.primaryPalette
    val s = scheme.secondaryPalette
    val t = scheme.tertiaryPalette
    val n = scheme.neutralPalette
    val nv = scheme.neutralVariantPalette
    val e = scheme.errorPalette
    fun c(pal: TonalPalette, tone: Int) = Color(pal.tone(tone))

    return if (dark) {
        darkColorScheme(
            primary = c(p, 80), onPrimary = c(p, 20), primaryContainer = c(p, 30),
            onPrimaryContainer = c(p, 90), inversePrimary = c(p, 40),
            secondary = c(s, 80), onSecondary = c(s, 20), secondaryContainer = c(s, 30),
            onSecondaryContainer = c(s, 90),
            tertiary = c(t, 80), onTertiary = c(t, 20), tertiaryContainer = c(t, 30),
            onTertiaryContainer = c(t, 90),
            background = c(n, 6), onBackground = c(n, 90),
            surface = c(n, 6), onSurface = c(n, 90),
            surfaceVariant = c(nv, 30), onSurfaceVariant = c(nv, 80),
            surfaceTint = c(p, 80), inverseSurface = c(n, 90), inverseOnSurface = c(n, 20),
            error = c(e, 80), onError = c(e, 20), errorContainer = c(e, 30),
            onErrorContainer = c(e, 90),
            outline = c(nv, 60), outlineVariant = c(nv, 30), scrim = c(n, 0),
            surfaceBright = c(n, 24), surfaceDim = c(n, 6),
            surfaceContainer = c(n, 12), surfaceContainerHigh = c(n, 17),
            surfaceContainerHighest = c(n, 22), surfaceContainerLow = c(n, 10),
            surfaceContainerLowest = c(n, 4),
            primaryFixed = c(p, 90), primaryFixedDim = c(p, 80),
            onPrimaryFixed = c(p, 10), onPrimaryFixedVariant = c(p, 30),
            secondaryFixed = c(s, 90), secondaryFixedDim = c(s, 80),
            onSecondaryFixed = c(s, 10), onSecondaryFixedVariant = c(s, 30),
            tertiaryFixed = c(t, 90), tertiaryFixedDim = c(t, 80),
            onTertiaryFixed = c(t, 10), onTertiaryFixedVariant = c(t, 30),
        )
    } else {
        lightColorScheme(
            primary = c(p, 40), onPrimary = c(p, 100), primaryContainer = c(p, 90),
            onPrimaryContainer = c(p, 10), inversePrimary = c(p, 80),
            secondary = c(s, 40), onSecondary = c(s, 100), secondaryContainer = c(s, 90),
            onSecondaryContainer = c(s, 10),
            tertiary = c(t, 40), onTertiary = c(t, 100), tertiaryContainer = c(t, 90),
            onTertiaryContainer = c(t, 10),
            background = c(n, 98), onBackground = c(n, 10),
            surface = c(n, 98), onSurface = c(n, 10),
            surfaceVariant = c(nv, 90), onSurfaceVariant = c(nv, 30),
            surfaceTint = c(p, 40), inverseSurface = c(n, 20), inverseOnSurface = c(n, 95),
            error = c(e, 40), onError = c(e, 100), errorContainer = c(e, 90),
            onErrorContainer = c(e, 10),
            outline = c(nv, 50), outlineVariant = c(nv, 80), scrim = c(n, 0),
            surfaceBright = c(n, 98), surfaceDim = c(n, 87),
            surfaceContainer = c(n, 94), surfaceContainerHigh = c(n, 92),
            surfaceContainerHighest = c(n, 90), surfaceContainerLow = c(n, 96),
            surfaceContainerLowest = c(n, 100),
            primaryFixed = c(p, 90), primaryFixedDim = c(p, 80),
            onPrimaryFixed = c(p, 10), onPrimaryFixedVariant = c(p, 30),
            secondaryFixed = c(s, 90), secondaryFixedDim = c(s, 80),
            onSecondaryFixed = c(s, 10), onSecondaryFixedVariant = c(s, 30),
            tertiaryFixed = c(t, 90), tertiaryFixedDim = c(t, 80),
            onTertiaryFixed = c(t, 10), onTertiaryFixedVariant = c(t, 30),
        )
    }
}

/**
 * True OLED: keep the dark scheme's text colors but drop all surfaces to
 * pure black — only elevated elements get a whisper of gray so cards and
 * sheets still separate from the background.
 */
private fun oledScheme(dark: ColorScheme): ColorScheme = dark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0F),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF17171A),
    surfaceContainerHighest = Color(0xFF1C1C20),
    surfaceVariant = Color(0xFF1F1F24),
    outline = Color(0xFF8E8E99),
    outlineVariant = Color(0xFF2F2F36),
    scrim = Color.Black,
)

/** Sepia e-paper theme — warm paper background, dark ink text. */
private fun sepiaScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF7C6133),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCC0),
    onPrimaryContainer = Color(0xFF2E2208),
    secondary = Color(0xFF6F634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0E4CF),
    onSecondaryContainer = Color(0xFF28200F),
    tertiary = Color(0xFF5E6B4F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1EBCF),
    onTertiaryContainer = Color(0xFF1C240E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6EEDC),
    onBackground = Color(0xFF43351F),
    surface = Color(0xFFF6EEDC),
    onSurface = Color(0xFF43351F),
    surfaceVariant = Color(0xFFE7DCC4),
    onSurfaceVariant = Color(0xFF6B5D45),
    surfaceTint = Color(0xFF7C6133),
    inverseSurface = Color(0xFF3E3626),
    inverseOnSurface = Color(0xFFF8F1E2),
    inversePrimary = Color(0xFFD9C79E),
    outline = Color(0xFF8F8064),
    outlineVariant = Color(0xFFD3C6A8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6EEDC),
    surfaceDim = Color(0xFFDCCFB2),
    surfaceContainerLowest = Color(0xFFFBF7ED),
    surfaceContainerLow = Color(0xFFF1E9D6),
    surfaceContainer = Color(0xFFECE2CC),
    surfaceContainerHigh = Color(0xFFE6DCC4),
    surfaceContainerHighest = Color(0xFFE0D5BC),
    primaryFixed = Color(0xFFEBDCC0),
    primaryFixedDim = Color(0xFFD9C79E),
    onPrimaryFixed = Color(0xFF2E2208),
    onPrimaryFixedVariant = Color(0xFF5E4720),
    secondaryFixed = Color(0xFFF0E4CF),
    secondaryFixedDim = Color(0xFFD5C8AF),
    onSecondaryFixed = Color(0xFF28200F),
    onSecondaryFixedVariant = Color(0xFF554A37),
    tertiaryFixed = Color(0xFFE1EBCF),
    tertiaryFixedDim = Color(0xFFC4CFB4),
    onTertiaryFixed = Color(0xFF1C240E),
    onTertiaryFixedVariant = Color(0xFF465339),
)

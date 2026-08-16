package dev.trove.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 12-color "tag & folder" palette. Each hue provides a main color and a
 * soft container tint that works in both light and dark schemes.
 */
data class AccentColor(
    val main: Color,
    val container: Color,
    val onContainer: Color,
)

val AccentPalette = listOf(
    AccentColor(Color(0xFF3D5AFE), Color(0xFFE0E6FF), Color(0xFF1B2E9B)),   // indigo
    AccentColor(Color(0xFF00897B), Color(0xFFD6F3EE), Color(0xFF00544B)),   // teal
    AccentColor(Color(0xFFE8710A), Color(0xFFFFE3C8), Color(0xFF8A4300)),   // orange
    AccentColor(Color(0xFFD81B60), Color(0xFFFFDCE9), Color(0xFF8E0B3C)),   // pink
    AccentColor(Color(0xFF7B1FA2), Color(0xFFF0DFF9), Color(0xFF4A1065)),   // purple
    AccentColor(Color(0xFF2E7D32), Color(0xFFDDF0DC), Color(0xFF134A16)),   // green
    AccentColor(Color(0xFFC62828), Color(0xFFFBDEDE), Color(0xFF7A1212)),   // red
    AccentColor(Color(0xFF0277BD), Color(0xFFD8EEFB), Color(0xFF01466E)),   // sky
    AccentColor(Color(0xFFF9A825), Color(0xFFFFF0C2), Color(0xFF7A5200)),   // amber
    AccentColor(Color(0xFF5E35B1), Color(0xFFE9E0FB), Color(0xFF31156B)),   // deep purple
    AccentColor(Color(0xFF00838F), Color(0xFFD4F0F4), Color(0xFF004955)),   // cyan
    AccentColor(Color(0xFF6D4C41), Color(0xFFEFE5E1), Color(0xFF3E241B)),   // brown
)

val AccentColorCount: Int get() = AccentPalette.size

fun accentColor(index: Int): AccentColor = AccentPalette[index % AccentPalette.size]

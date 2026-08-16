package dev.trove.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale — the updated corner radii from the M3
 * Expressive shape update: large 20dp, extra large 32dp, plus an
 * extra-extra-large 48dp hero shape.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Extra-extra-large hero corner radius (48dp). */
val ExpressiveHeroShape = RoundedCornerShape(48.dp)

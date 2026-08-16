package dev.trove.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion physics — the official spring tokens from
 * the M3 motion specs (MDC-Android motionSpring* attributes), expressed as
 * Compose springs (dampingRatio + stiffness).
 *
 * Spatial springs overshoot slightly (damping < 1) — use for anything that
 * moves: position, size, shape, scale.
 * Effects springs are critically damped (damping = 1, no bounce) — use for
 * color and opacity changes.
 */
object ExpressiveSprings {

    /** Small components: switches, buttons, chips, cards on press. */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Small component effects: color, opacity. */
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** Partial-screen motion: bottom sheets, expandable areas. */
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** Partial-screen effects. */
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** Full-screen motion. */
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /** Full-screen effects. */
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

/** Springy scale used by pressable expressive components (spatial-fast). */
fun pressSpring(scale: Float): AnimationSpec<Float> = ExpressiveSprings.fastSpatial()

package dev.trove.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The real Material 3 Expressive "cookie" shapes, built from the exact
 * definitions in androidx material3's MaterialShapes (cookie4Sided /
 * cookie6Sided): a RoundedPolygon with per-vertex corner rounding, repeated
 * and rotated around its center.
 */
private fun cookiePolygon(reps: Int, points: List<Pair<Float, Float>>, roundings: List<Float>): RoundedPolygon {
    val np = points.size
    val vertices = FloatArray(np * reps * 2)
    val perVertexRounding = ArrayList<CornerRounding>(np * reps)
    for (i in 0 until np * reps) {
        val (x, y) = points[i % np]
        val angle = (i / np) * (2f * PI.toFloat() / reps)
        val dx = x - 0.5f
        val dy = y - 0.5f
        vertices[i * 2] = dx * cos(angle) - dy * sin(angle) + 0.5f
        vertices[i * 2 + 1] = dx * sin(angle) + dy * cos(angle) + 0.5f
        perVertexRounding.add(CornerRounding(roundings[i % np]))
    }
    return RoundedPolygon(
        vertices = vertices,
        perVertexRounding = perVertexRounding,
        centerX = 0.5f,
        centerY = 0.5f,
    ).normalized()
}

/** M3 Expressive cookie, 4-sided — the classic squarish blob. */
val Cookie4Shape: Shape = CookieShape(
    cookiePolygon(
        reps = 4,
        points = listOf(1.237f to 1.236f, 0.5f to 0.918f),
        roundings = listOf(0.258f, 0.233f),
    )
)

/** M3 Expressive cookie, 6-sided — rounder. */
val Cookie6Shape: Shape = CookieShape(
    cookiePolygon(
        reps = 6,
        points = listOf(0.723f to 0.884f, 0.5f to 1.099f),
        roundings = listOf(0.394f, 0.398f),
    )
)

/** Draws a normalized (0..1) RoundedPolygon as a Compose shape. */
private class CookieShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        polygon.cubics.forEachIndexed { i, cubic ->
            val start = Offset(cubic.anchor0X * size.width, cubic.anchor0Y * size.height)
            if (i == 0) path.moveTo(start.x, start.y)
            path.cubicTo(
                cubic.control0X * size.width, cubic.control0Y * size.height,
                cubic.control1X * size.width, cubic.control1Y * size.height,
                cubic.anchor1X * size.width, cubic.anchor1Y * size.height,
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}

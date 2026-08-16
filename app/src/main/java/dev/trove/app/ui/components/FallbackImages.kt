package dev.trove.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

private enum class Glyph { IMAGE, GLOBE }

/**
 * Hand-drawn placeholder painters — a "broken image" glyph for article
 * photos and a globe for favicons. Used as AsyncImage error/placeholder
 * slots so missing media never shows as a blank box.
 */
private class GlyphPainter(
    private val color: Color,
    private val bgColor: Color,
    private val glyph: Glyph,
) : Painter() {

    override val intrinsicSize: Size get() = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        when (glyph) {
            Glyph.IMAGE -> drawImageGlyph()
            Glyph.GLOBE -> drawGlobeGlyph()
        }
    }

    private fun DrawScope.drawImageGlyph() {
        val w = size.width
        val h = size.height
        // soft container fill
        drawRoundRect(
            color = bgColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
        )
        val ink = color.copy(alpha = 0.42f)
        // sun — filled, no strokes
        drawCircle(
            color = ink,
            radius = w * 0.085f,
            center = Offset(w * 0.70f, h * 0.32f),
        )
        // mountain — filled triangle, fully inside
        val path = Path().apply {
            moveTo(w * 0.26f, h * 0.76f)
            lineTo(w * 0.52f, h * 0.44f)
            lineTo(w * 0.78f, h * 0.76f)
            close()
        }
        drawPath(path, color = ink)
    }

    private fun DrawScope.drawGlobeGlyph() {
        val w = size.width
        val h = size.height
        // soft container fill
        drawRoundRect(
            color = bgColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.25f),
        )
        val ink = color.copy(alpha = 0.42f)
        // simple filled bookmark dot for favicons — reads clean at tiny sizes
        val bw = w * 0.44f
        val bx = (w - bw) / 2f
        val by = h * 0.20f
        val bh = h * 0.60f
        val notch = w * 0.18f
        val path = Path().apply {
            moveTo(bx, by + h * 0.05f)
            lineTo(bx + bw, by + h * 0.05f)
            lineTo(bx + bw, by + bh - notch)
            lineTo(bx + bw / 2f, by + bh)
            lineTo(bx, by + bh - notch)
            close()
        }
        drawPath(path, color = ink)
    }
}

/** Broken-image placeholder for article photos. */
@Composable
fun rememberMissingImagePainter(): Painter {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(color, bg) { GlyphPainter(color, bg, Glyph.IMAGE) }
}

/** Bookmark-dot placeholder for favicons without a usable icon. */
@Composable
fun rememberMissingFaviconPainter(): Painter {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(color, bg) { GlyphPainter(color, bg, Glyph.GLOBE) }
}

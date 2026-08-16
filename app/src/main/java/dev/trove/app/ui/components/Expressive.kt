package dev.trove.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.trove.app.ui.theme.ExpressiveSprings
import dev.trove.app.ui.theme.accentColor
import dev.trove.app.ui.theme.pressSpring

/**
 * Expressive interaction: components scale down slightly with a springy
 * overshoot while pressed.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = pressSpring(scale = pressedScale),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Expressive filled card: rounded squircle, soft container color, no border.
 * Supports tap and long-press (long-press opens the article actions).
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        shadowElevation = 0.dp,
    ) {
        // The click lives inside the surface content and is clipped to the
        // card shape, so the ripple highlight matches the rounded corners.
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .pressScale(interaction)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                ),
        ) {
            content()
        }
    }
}

/** Pill-shaped filled button with expressive motion. */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "",
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(interaction, pressedScale = 0.95f),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        interactionSource = interaction,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

/** Colorful pill chip for tags and folders. */
@Composable
fun AccentChip(
    text: String,
    colorIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    large: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val accent = accentColor(colorIndex)
    val container by animateColorAsState(
        targetValue = if (selected) accent.main else accent.container,
        animationSpec = ExpressiveSprings.fastEffects(),
        label = "chipColor",
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else accent.main,
        animationSpec = ExpressiveSprings.fastEffects(),
        label = "chipContent",
    )
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        border = if (selected) null else BorderStroke(1.dp, accent.main.copy(alpha = 0.45f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .pressScale(interaction, pressedScale = 0.93f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                )
                .padding(
                    horizontal = if (large) 16.dp else 12.dp,
                    vertical = if (large) 10.dp else 6.dp,
                ),
        ) {
            // Selected chips always show a checkmark — unmistakable choice.
            val leadingIcon = if (selected) Icons.Rounded.Check else icon
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(if (large) 18.dp else 14.dp))
                Spacer(Modifier.width(if (large) 7.dp else 5.dp))
            }
            Text(
                text,
                color = content,
                style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            )
            if (count != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    count.toString(),
                    color = content.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
    large: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = if (large) MaterialTheme.typography.headlineSmall
            else MaterialTheme.typography.titleLarge,
            fontWeight = if (large) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Real M3 Expressive "cookie" shape (MaterialShapes cookie4Sided)
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(shape = dev.trove.app.ui.theme.Cookie4Shape, color = MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(onClick = onAction, text = actionLabel, icon = Icons.Rounded.BookmarkBorder)
        }
    }
}

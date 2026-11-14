package com.widgeon.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ---------------------------------------------------------------- haptics

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics.get(context) }
}

// ------------------------------------------------------------- modifiers

/** Premium press feedback: springy scale-down while pressed. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressed: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this.scale(scale)
}

/** Standard tappable card: press scale + haptic tick on press. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    haptics: Haptics,
    pressed: Float = 0.97f,
    enabled: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Press) haptics.tick()
        }
    }
    return this
        .pressScale(source, pressed)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Staggered entrance: fades + slides up with a per-index delay. */
@Composable
fun Modifier.staggerEntrance(index: Int, key: Any? = Unit, maxDelayMs: Int = 360): Modifier {
    var visible by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay((index * 60).coerceAtMost(maxDelayMs).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "staggerAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 26f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "staggerOffset",
    )
    return this
        .alpha(alpha)
        .offset { IntOffset(0, offsetY.roundToInt()) }
}

// ------------------------------------------------------------ primitives

/** Pill-shaped primary action button with press feedback + haptics. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Haptics = rememberHaptics(),
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = { haptics.click(); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        interactionSource = source,
        modifier = modifier
            .height(52.dp)
            .pressScale(source, 0.97f),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptics: Haptics = rememberHaptics(),
) {
    val source = remember { MutableInteractionSource() }
    TextButton(
        onClick = { haptics.tick(); onClick() },
        enabled = enabled,
        shape = RoundedCornerShape(50),
        interactionSource = source,
        modifier = modifier.pressScale(source, 0.97f),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

/** Card-like surface used across screens. */
@Composable
fun WCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    corner: Int = 24,
    onClick: (() -> Unit)? = null,
    haptics: Haptics = rememberHaptics(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    val base = Modifier
        .clip(shape)
        .background(container)
        .border(1.dp, borderColor, shape)
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = if (onClick != null) base.pressable(onClick, haptics) else base,
        shape = shape,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Switch with satisfying on/off haptics. */
@Composable
fun WSwitch(checked: Boolean, onChange: (Boolean) -> Unit, haptics: Haptics = rememberHaptics()) {
    Switch(
        checked = checked,
        onCheckedChange = {
            if (it) haptics.toggleOn() else haptics.toggleOff()
            onChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Monochrome pill chip. */
@Composable
fun WChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: Haptics = rememberHaptics(),
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        tween(220), label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(220), label = "chipFg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                1.dp,
                if (selected) bg else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(50)
            )
            .pressable(onClick, haptics, pressed = 0.94f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun IconPill(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surface,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    haptics: Haptics = rememberHaptics(),
) {
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .pressScale(source, 0.9f)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
            ) { if (enabled) { haptics.tick(); onClick() } },
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

// --------------------------------------------------------------- dialogs

@Composable
fun WDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "Close",
    haptics: Haptics = rememberHaptics(),
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (dismissText != null) {
                        GhostButton(dismissText, onDismiss)
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { haptics.click(); onConfirm() },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------- tiny private helpers

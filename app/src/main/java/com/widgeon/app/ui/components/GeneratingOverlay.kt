package com.widgeon.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.widgeon.app.ai.GenStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen "AI is designing" experience: breathing rings, an orbiting
 * spark, a smooth progress bar and per-stage copy with transitions.
 */
@Composable
fun GeneratingOverlay(
    stage: GenStage,
    progress: Float,
    onCancel: () -> Unit,
    haptics: Haptics = rememberHaptics(),
) {
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(stage) { haptics.tick() }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg.copy(alpha = 0.985f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OrbArt(modifier = Modifier.size(230.dp), ink = ink)
            Spacer(Modifier.height(44.dp))

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 220f)) { it / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
                },
                label = "stageText",
            ) { s ->
                Text(
                    s.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 90f),
                label = "genProgress",
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = ink,
                trackColor = ink.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = dim,
            )

            Spacer(Modifier.height(56.dp))
            GhostButton("Cancel", onCancel)
        }
    }
}

/** Concentric breathing rings + orbiting spark + pulsing W monogram. */
@Composable
private fun OrbArt(modifier: Modifier = Modifier, ink: Color) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "breathe"
    )
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )

    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f

        // 3 breathing rings with phase offsets
        for (i in 0..2) {
            val phase = (breathe + i / 3f) % 1f
            val r = maxR * (0.32f + 0.62f * phase)
            val alpha = (1f - phase) * 0.34f
            drawCircle(
                color = ink.copy(alpha = alpha),
                radius = r,
                center = c,
                style = Stroke(width = 2.2f * (1f - phase) + 0.6f),
            )
        }

        // Rotating arc
        val arcR = maxR * 0.56f
        drawArc(
            color = ink,
            startAngle = spin,
            sweepAngle = 88f,
            useCenter = false,
            topLeft = Offset(c.x - arcR, c.y - arcR),
            size = androidx.compose.ui.geometry.Size(arcR * 2, arcR * 2),
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )

        // Orbiting spark on the arc radius
        val rad = spin * PI.toFloat() / 180f
        val spark = Offset(c.x + arcR * cos(rad), c.y + arcR * sin(rad))
        drawCircle(Color.White, radius = 7f, center = spark)
        drawCircle(ink.copy(alpha = 0.25f), radius = 16f, center = spark, style = Stroke(2f))

        // Pulsing W
        val pulse = 0.9f + 0.1f * sin(breathe * 2f * PI.toFloat()).let { if (it < 0) -it else it }
        val wPath = Path().apply {
            val s = 26f * pulse
            moveTo(c.x - s, c.y - s * 0.8f)
            lineTo(c.x - s * 0.42f, c.y + s * 0.8f)
            lineTo(c.x, c.y - s * 0.35f)
            lineTo(c.x + s * 0.42f, c.y + s * 0.8f)
            lineTo(c.x + s, c.y - s * 0.8f)
        }
        drawPath(wPath, ink, style = Stroke(width = 7f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Celebration burst shown when a generation lands: expanding halo + pop-in
 * checkmark, then [onDone] hands off to the editor.
 */
@Composable
fun SuccessBurst(
    name: String,
    onDone: () -> Unit,
    haptics: Haptics = rememberHaptics(),
) {
    LaunchedEffect(Unit) {
        haptics.success()
        kotlinx.coroutines.delay(1250)
        onDone()
    }
    val transition = rememberInfiniteTransition(label = "burst")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = EaseOutCubic), RepeatMode.Restart), label = "burstT"
    )
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground

    Box(
        Modifier
            .fillMaxSize()
            .background(bg.copy(alpha = 0.985f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(190.dp)) {
                    val phase = t
                    drawCircle(
                        ink.copy(alpha = (1f - phase) * 0.5f),
                        radius = size.minDimension / 2f * (0.25f + 0.75f * phase),
                        style = Stroke(width = 3.5f * (1f - phase) + 0.4f),
                    )
                    drawCircle(ink.copy(alpha = (1f - phase) * 0.22f), radius = size.minDimension / 2f * (0.2f + 0.6f * phase))
                }
                val pop by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
                    label = "pop",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pop)
                        .clip(CircleShape)
                        .background(ink),
                ) {
                    Checkmark(Modifier.size(44.dp), color = bg)
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "“$name” is ready",
                style = MaterialTheme.typography.titleLarge,
                color = ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Opening the editor…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Animated stroke checkmark. */
@Composable
fun Checkmark(modifier: Modifier = Modifier, color: Color) {
    val progress by transitionFloat()
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.54f)
            lineTo(size.width * 0.42f, size.height * 0.78f)
            lineTo(size.width * 0.85f, size.height * 0.26f)
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val partial = Path()
        measure.getSegment(0f, measure.length * progress, partial, true)
        drawPath(partial, color, style = Stroke(width = 9f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun transitionFloat(): androidx.compose.runtime.State<Float> {
    var v by androidx.compose.runtime.mutableFloatStateOf(0f)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40)
        v = 1f
    }
    val anim by animateFloatAsState(v, tween(560, easing = EaseOutCubic), label = "check")
    return androidx.compose.runtime.derivedStateOf { anim }
}

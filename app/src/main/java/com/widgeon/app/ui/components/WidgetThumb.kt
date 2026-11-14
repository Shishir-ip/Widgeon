package com.widgeon.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.widgets.WidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a widget design to a bitmap off the main thread and shows it with a
 * soft placeholder while rendering. Used by Home, Collection and the picker.
 */
@Composable
fun WidgetThumb(
    design: WidgetDesign,
    modifier: Modifier = Modifier,
    corner: Int = 16,
) {
    val context = LocalContext.current
    var bitmap by remember(design.id, design.version) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(design.id, design.version) {
        val wDp = (design.cols * 70 - 30).coerceAtLeast(48)
        val hDp = (design.rows * 70 - 30).coerceAtLeast(48)
        withContext(Dispatchers.Default) {
            bitmap = WidgetHost.renderDesign(context, design, wDp, hDp)
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = design.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ShimmerPlaceholder(Modifier.fillMaxSize(), corner = corner)
        }
    }
}

/** Gentle monochrome shimmer for loading states. */
@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier, corner: Int = 16) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "shimmerT",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(base)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val x = -w + (t * 2.4f * w)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, hi.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + w * 0.8f, size.height),
                ),
            )
        }
    }
}

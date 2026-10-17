package app.temposhift.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Loudness strip: played part in the accent colour, tap or drag to seek. */
@Composable
fun Waveform(
    bars: ByteArray?,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    playing: Boolean = false
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val seekNow by rememberUpdatedState(onSeek)
    val shown = dragging ?: progress
    // "animated" look: while playing, the bars around the playhead dance like a voice note
    val phase = if (animated && playing) {
        val transition = rememberInfiniteTransition(label = "waveDance")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "wavePhase"
        ).value
    } else 0f
    val grow by animateFloatAsState(
        targetValue = if (bars != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "waveGrow"
    )
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { off -> seekNow((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> dragging = (off.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { seekNow(it) }; dragging = null },
                    onDragCancel = { dragging = null },
                    onHorizontalDrag = { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                )
            }
    ) {
        val n = bars?.size ?: 90
        val barW = size.width / n
        val mid = size.height / 2f
        val minH = 3.dp.toPx()
        for (i in 0 until n) {
            val real = if (bars != null) (bars[i].toInt() and 0xFF) / 255f else 0.12f
            val v = 0.12f + (real - 0.12f) * grow
            var h = maxOf(minH, v * size.height)
            if (animated && playing) {
                val d = kotlin.math.abs(i - shown * n)
                if (d < 12f) h = (h * (1f + 0.45f * (1f - d / 12f) * kotlin.math.sin(phase + i * 0.9f))).coerceIn(minH, size.height)
            }
            val color = if ((i + 0.5f) / n <= shown) ACCENT else DIM.copy(alpha = 0.55f)
            val x = i * barW + barW / 2f
            drawLine(color, Offset(x, mid - h / 2f), Offset(x, mid + h / 2f), strokeWidth = barW * 0.6f, cap = StrokeCap.Round)
        }
    }
}

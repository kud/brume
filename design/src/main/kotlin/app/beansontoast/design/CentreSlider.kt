package app.beansontoast.design

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A slider whose rest is the middle: -1 .. 0 .. +1. The fill grows from the centre towards the
 * chosen side, so the centre reads as empty rather than half full. Never resting on colour: the
 * fill is twice the track's thickness, a centre tick stands proud of the thumb at every value,
 * and [valueText] (the words the caller writes beside it, "Centre", "Left 30%") is what TalkBack
 * reads. Left is always the physical left, in RTL too: a balance is about ears, not reading order.
 * Moves in [stops] equal steps (20 is tenths each way); arrows step by one when focused.
 */
@Composable
fun CentreSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    stops: Int = 20,
) {
    val step = 2f / stops
    fun snap(v: Float): Float {
        val s = ((v.coerceIn(-1f, 1f) / step).roundToInt() * step).coerceIn(-1f, 1f)
        return if (kotlin.math.abs(s) < step / 2) 0f else s
    }
    val current by rememberUpdatedState(snap(value))
    val emit by rememberUpdatedState(onValueChange)
    var width by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val inset = 14.dp
    val insetPx = with(LocalDensity.current) { inset.toPx() }
    val colors = MaterialTheme.colorScheme
    val fill = colors.primary
    val track = colors.outlineVariant
    val tick = colors.onSurfaceVariant
    val thumbRing = colors.surfaceContainer

    fun valueAt(x: Float, insetPx: Float): Float {
        val usable = (width - 2 * insetPx).coerceAtLeast(1f)
        return snap(((x - insetPx) / usable) * 2f - 1f)
    }
    fun move(v: Float) { if (v != current) emit(v) }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onSizeChanged { width = it.width }
            .semantics {
                contentDescription = label
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(current, -1f..1f, stops - 1)
                setProgress { target -> move(snap(target)); true }
            }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { move(snap(current - step)); true }
                    Key.DirectionRight -> { move(snap(current + step)); true }
                    else -> false
                }
            }
            .focusable()
            .draggable(
                state = rememberDraggableState { delta -> dragX += delta; move(valueAt(dragX, insetPx)) },
                orientation = Orientation.Horizontal,
                reverseDirection = false,
                startDragImmediately = true,
                onDragStarted = { at -> dragX = at.x; move(valueAt(at.x, insetPx)) },
            )
            .drawBehind {
                val insetPx = inset.toPx()
                val y = size.height / 2
                val usable = size.width - 2 * insetPx
                val centreX = insetPx + usable / 2
                val valueX = insetPx + (current + 1f) / 2f * usable
                // The track: thin and quiet, end to end.
                val thin = 4.dp.toPx()
                drawRoundRect(track, Offset(insetPx, y - thin / 2), Size(usable, thin), CornerRadius(thin / 2))
                // The fill: twice as thick, from the centre to the value, so its extent is a shape.
                val thick = 8.dp.toPx()
                if (current != 0f) {
                    val from = minOf(centreX, valueX)
                    drawRoundRect(fill, Offset(from, y - thick / 2), Size(kotlin.math.abs(valueX - centreX), thick), CornerRadius(thick / 2))
                }
                // The centre tick, taller than the thumb so it shows even when the thumb sits on it.
                val tickW = 2.dp.toPx()
                val tickH = 36.dp.toPx()
                drawRect(tick, Offset(centreX - tickW / 2, y - tickH / 2), Size(tickW, tickH))
                // The thumb: an upright pill with a ground-coloured ring, so it parts from the tick.
                val thumbW = 6.dp.toPx()
                val thumbH = 26.dp.toPx()
                val ring = 2.dp.toPx()
                drawRoundRect(thumbRing, Offset(valueX - thumbW / 2 - ring, y - thumbH / 2 - ring), Size(thumbW + 2 * ring, thumbH + 2 * ring), CornerRadius(thumbW / 2 + ring))
                drawRoundRect(fill, Offset(valueX - thumbW / 2, y - thumbH / 2), Size(thumbW, thumbH), CornerRadius(thumbW / 2))
            },
    )
}

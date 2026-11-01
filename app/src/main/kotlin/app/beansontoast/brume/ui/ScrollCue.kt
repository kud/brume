package app.beansontoast.brume.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The scroll cue (Iris, 2026-10-31, from Erwann on dev.5: « On ne comprend pas toujours qu'on
 * peut scroller les écrans »). While more lies below, the bottom edge of a scrolling column fades
 * softly into the background, so the last visible line reads as cut, not as the end. It is the
 * background fading into itself, so it carries no colour meaning, and night's dim ground comes
 * with it. Its strength follows the distance left to scroll, easing out over the last [depth]
 * and gone at the very end: it moves only with the finger, so reduced motion has nothing to
 * still. Put it before `verticalScroll` on the same state, so it draws over the viewport.
 */
@Composable
fun Modifier.scrollCue(state: ScrollState, depth: Dp = 40.dp): Modifier {
    val ground = MaterialTheme.colorScheme.background
    // The fade rises above the floating bar, so it reads where the page visibly ends.
    val above = LocalChromeBottom.current
    return drawWithContent {
        drawContent()
        val band = (depth + above).toPx().coerceAtMost(size.height / 2f)
        val strength = ((state.maxValue - state.value) / band).coerceIn(0f, 1f)
        if (strength > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground), startY = size.height - band, endY = size.height),
                topLeft = Offset(0f, size.height - band), size = Size(size.width, band), alpha = strength,
            )
        }
    }
}

package app.beansontoast.brume.ui.launch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.splashscreen.SplashScreenViewProvider
import app.beansontoast.design.rememberReducedMotion
import kotlinx.coroutines.flow.first

/*
 * The launch: the system splash shows the lockup still (Horizon over "brume", res/drawable/
 * splash_lockup.xml); once the app has drawn its first frame, the title card (TitleCard.kt) covers
 * it on its first frame, the system splash is removed underneath, and the card fades into the
 * screen already composed below. A tap only shortens the hold.
 */

/** The splash icon's box on screen (no icon background), in dp: 108 units at 288/108 dp each. */
private const val SPLASH_ICON_DP = 288f

/** Which intro a launch gets. */
enum class IntroKind {
    /** A fresh process: the full hold. */
    COLD,

    /** A live process, a new activity (after Back): the short one. */
    WARM,

    /** A recreated activity: none. */
    NONE,
}

/** Tells cold from warm: the first activity of a process is cold. One instance per process. */
class LaunchCounter {
    private var started = false

    @Synchronized
    fun kindFor(restored: Boolean): IntroKind {
        val cold = !started
        started = true
        return when {
            restored -> IntroKind.NONE
            cold -> IntroKind.COLD
            else -> IntroKind.WARM
        }
    }
}

/** Hand-over from the system splash. MainActivity passes [takeOver] to setOnExitAnimationListener. */
class LaunchIntro(val kind: IntroKind) {
    /** The splash icon's 288 dp box in window pixels, once the splash has handed over; null when there is nothing to play. */
    internal var icon by mutableStateOf<Rect?>(null)
        private set
    private var provider: SplashScreenViewProvider? = null

    fun takeOver(splash: SplashScreenViewProvider) {
        if (kind == IntroKind.NONE) {
            splash.remove()
            return
        }
        provider = splash
        val metrics = splash.view.resources.displayMetrics
        val half = SPLASH_ICON_DP * metrics.density / 2f
        // The system centres the icon; an icon view not yet laid out falls back to the splash's
        // centre, then the screen's (tutti's lesson: a warm start can hand over before layout).
        val centre = splash.iconView.centreInWindow() ?: splash.view.centreInWindow()
            ?: Offset(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        icon = Rect(centre.x - half, centre.y - half, centre.x + half, centre.y + half)
    }

    /** The overlay has drawn the lockup over the system splash: the swap is invisible. */
    internal fun covered() {
        provider?.remove()
        provider = null
    }

    internal fun finish() {
        covered()
        icon = null
    }

    companion object {
        // Process-wide: savedInstanceState is null after Back too, so it cannot tell cold from warm.
        private val launches = LaunchCounter()

        fun kindFor(restored: Boolean): IntroKind = launches.kindFor(restored)
    }
}

private fun android.view.View.centreInWindow(): Offset? {
    if (!isLaidOut || width == 0 || height == 0) return null
    val at = IntArray(2).also(::getLocationInWindow)
    return Offset(at[0] + width / 2f, at[1] + height / 2f)
}

/**
 * The overlay: the title card on its first frame, the system splash removed once that is on
 * screen, a hold, then the card's slow fade into whatever is underneath.
 */
@Composable
fun LaunchIntroOverlay(intro: LaunchIntro) {
    intro.icon ?: return
    // The board's first screen (Iris, phase 2): the title card covers the splash on its first frame,
    // holds, then fades into whatever is underneath (home, or the introduction) on SLOW.
    val reduced = rememberReducedMotion()
    var alpha by remember { mutableFloatStateOf(1f) }
    var skipped by remember { mutableStateOf(false) }
    LaunchedEffect(intro) {
        withFrameNanos { }
        withFrameNanos { }
        intro.covered()
        val hold = when { intro.kind == IntroKind.COLD && !reduced -> 1100L; intro.kind == IntroKind.COLD -> 700L; else -> 250L }
        // A tap only shortens the hold: the card still fades, it never cuts.
        kotlinx.coroutines.withTimeoutOrNull(hold) { androidx.compose.runtime.snapshotFlow { skipped }.first { it } }
        androidx.compose.animation.core.animate(1f, 0f, animationSpec = app.beansontoast.design.Motion.tween(app.beansontoast.design.Motion.SLOW)) { v, _ -> alpha = v }
        intro.finish()
    }
    TitleCard(
        Modifier.graphicsLayer { this.alpha = alpha }.pointerInput(intro) {
            // A tap skips to the fade; nothing reaches the screen underneath while the card is up.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                skipped = true
            }
        },
    )
}

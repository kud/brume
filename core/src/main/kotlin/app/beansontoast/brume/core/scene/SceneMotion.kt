package app.beansontoast.brume.core.scene

import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import kotlin.math.PI
import kotlin.math.sin

/** How a scene's middle layer lives (Sacha's motion notes, PR #3): only its opacity ever moves, never its shape. */
enum class MiddleMotion {
    /** Sleep and Settle: largely still, a faint breath of light at most. */
    STILL,
    /** Breathe: the swell's light follows the breath. */
    BREATH,
    /** Tones: the marks dissolve softly and come back, never an equaliser pulse. */
    DISSOLVE,
}

/**
 * How Sacha's scene layers move (Iris, 2026-11-01, folding in Sacha's motion notes), as plain
 * numbers so they are tested here and the app only draws them. Slow and calm, in the Endel and Tide
 * spirit: nothing loops in a way the eye can catch. Each motion is a sum of two sines whose periods
 * stand in the golden ratio, so the path comes back on itself as late as it can, with no seam.
 *  - The sky never moves, not even in the swipe: a vertical gradient shows nothing when slid, and
 *    Sacha asks for it to stay put.
 *  - Only one layer brightens at a time: the mist drifts at a fixed light, the middle alone breathes.
 */
object SceneMotion {
    /** The app redraws the drift and the breath at this rate: 20 frames a second is plenty for a drift of a few pixels a second. */
    const val FRAME_MS = 50L

    /** The mist's reach each way, in Sacha's source pixels: ±80 of his 162 px overhang. */
    const val DRIFT_PX = 80f

    /** Parallax during the scene swipe: the share of the page's travel a layer holds back. The mist, behind, holds back more. */
    const val MIST_PARALLAX = 0.22f
    const val MIDDLE_PARALLAX = 0.08f

    private const val TAU = 2 * PI
    private const val GOLDEN = 1.6180339887

    /**
     * The mist's sideways drift at [seconds], from -1 to 1 (times [DRIFT_PX]). Its two cycles run
     * between 45 and 90 s, as Sacha asks, and differ a little per scene ([seed]), so two scenes side
     * by side never move in step.
     */
    fun drift(seconds: Double, seed: Int): Float {
        val slow = 72.0 + 5.0 * (seed % 4)
        val quick = slow / GOLDEN
        val v = 0.62 * sin(TAU * seconds / slow + seed * 1.3) + 0.38 * sin(TAU * seconds / quick + seed * 2.9)
        return v.toFloat()
    }

    /** A resting breath, 0 to 1: about one swell every 12 s ([pace] stretches it), never quite the same twice. */
    fun breath(seconds: Double, seed: Int, pace: Double = 1.0): Float {
        val t = seconds / pace
        val v = 0.72 * sin(TAU * t / 12.2 + seed * 0.7) + 0.28 * sin(TAU * t / (12.2 * GOLDEN) + seed * 1.9)
        return ((v + 1) / 2).toFloat()
    }

    /**
     * The breath of the rhythm chosen for the session, 0 (out) to 1 (in), eased exactly as the
     * session's own disc is ([BreathPlan.at]), so Breathe previews the breath it will lead.
     */
    fun breath(seconds: Double, pattern: Pattern): Float {
        val within = ((seconds % pattern.breathSeconds) + pattern.breathSeconds) % pattern.breathSeconds
        return BreathPlan(pattern, 1).at(within).fullness
    }

    /** The middle layer's opacity, as a share of the scene's dimming: 1 is the art as Sacha drew it. */
    fun middleAlpha(seconds: Double, motion: MiddleMotion, seed: Int, pattern: Pattern? = null): Float = when (motion) {
        MiddleMotion.STILL -> 0.94f + 0.06f * breath(seconds, seed)
        MiddleMotion.BREATH -> 0.55f + 0.45f * (pattern?.let { breath(seconds, it) } ?: breath(seconds, seed))
        MiddleMotion.DISSOLVE -> 0.6f + 0.4f * breath(seconds, seed, pace = 1.6)
    }
}

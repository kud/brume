package app.beansontoast.brume.ui.launch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The launch intro's timing, as data (Iris, 2026-10-31, after Erwann's "Go with the launch intro").
 * Pure Kotlin, no Android or Compose, so every number here is tested on the JVM.
 *
 * The system splash shows the lockup still (Horizon over "brume"). Once the app has drawn its first
 * frame, the overlay (LaunchIntro.kt) draws the same lockup at the same place, removes the system
 * splash and plays one of these on the app's own frame clock:
 *
 *  - [Choreographies.rise], a cold start onto Listen, 800 ms: the wordmark lifts away like mist,
 *    Horizon's three lines open into the soft lines behind the Play disc, and the ground lifts from
 *    the dock upwards, so Listen shows from where the lines land.
 *  - [Choreographies.welcome], the very first launch, 800 ms: the lockup settles onto the
 *    introduction's own (Horizon over the wordmark), and the ground fades round it.
 *  - [Choreographies.lift], a warm start (300 ms), or a cold one with nowhere to land (500 ms):
 *    the wordmark lifts, the mark fades, the ground lifts.
 *  - [Choreographies.fade], reduced motion, 200 ms: opacity only, nothing moves.
 *
 * Nothing on a hot resume (the activity is still there: no splash, no intro) or a recreated activity.
 */

/** An easing: progress through a segment (0..1) to progress of its value. */
fun interface Ease {
    fun at(t: Float): Float
}

/** A CSS-style cubic Bézier easing from (0,0) to (1,1). */
class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) : Ease {
    private fun curve(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * a * u * u * t + 3f * b * u * t * t + t * t * t
    }

    override fun at(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (curve(mid, x1, x2) < t) lo = mid else hi = mid
        }
        return curve((lo + hi) / 2f, y1, y2)
    }
}

object Eases {
    val Linear = Ease { it }

    /** Unhurried in and out: things that travel. */
    val Calm = CubicBezier(0.4f, 0f, 0.2f, 1f)

    /** Starts at speed, settles: things that leave or arrive. */
    val Settle = CubicBezier(0f, 0f, 0.2f, 1f)
}

/** One keyframe: at [ms] the value is [value], reached from the previous key along [ease]. */
data class Key(val ms: Float, val value: Float, val ease: Ease = Eases.Linear)

/** A property over time. Before the first key it holds the first value, after the last the last. */
class Track(vararg keys: Key) {
    private val keys = keys.sortedBy { it.ms }

    init {
        require(this.keys.isNotEmpty())
    }

    fun at(ms: Float): Float {
        if (ms <= keys.first().ms) return keys.first().value
        for (i in 1 until keys.size) {
            val to = keys[i]
            if (ms <= to.ms) {
                val from = keys[i - 1]
                val span = to.ms - from.ms
                val u = if (span <= 0f) 1f else (ms - from.ms) / span
                return from.value + (to.value - from.value) * to.ease.at(u)
            }
        }
        return keys.last().value
    }

    val endMs: Float get() = keys.last().ms

    companion object {
        fun still(value: Float) = Track(Key(0f, value))
    }
}

/** Where the lockup goes. */
enum class Landing {
    /** Listen's dock: the lines become the mist behind the Play disc. */
    MIST,

    /** The introduction's first page: the lockup becomes its own. */
    WELCOME,

    /** Nothing to land on: it lifts in place. */
    NOWHERE,
}

/**
 * The picture at one moment, every value 0..1 unless said otherwise.
 *
 * [ground] is the splash ground's opacity; [lift] how far the ground has lifted from the bottom of
 * the screen to the top. [word] and [mark] are the lockup's opacities, [rise] how far the wordmark
 * has risen, in the splash icon's units. [travel] is how far the lockup has gone towards its
 * landing; [outer] the two mist lines Horizon does not bring.
 */
data class LaunchPose(
    val ground: Float = 1f,
    val lift: Float = 0f,
    val word: Float = 1f,
    val rise: Float = 0f,
    val mark: Float = 1f,
    val travel: Float = 0f,
    val outer: Float = 0f,
) {
    companion object {
        /** The system splash's own picture. */
        val Resting = LaunchPose()
    }
}

/** A choreography: a track per pose value, and where it lands. */
class Choreography(
    val name: String,
    val durationMs: Float,
    val landing: Landing,
    private val ground: Track = Track.still(1f),
    private val lift: Track = Track.still(0f),
    private val word: Track = Track.still(1f),
    private val rise: Track = Track.still(0f),
    private val mark: Track = Track.still(1f),
    private val travel: Track = Track.still(0f),
    private val outer: Track = Track.still(0f),
) {
    fun pose(ms: Float): LaunchPose = LaunchPose(
        ground = ground.at(ms), lift = lift.at(ms), word = word.at(ms), rise = rise.at(ms),
        mark = mark.at(ms), travel = travel.at(ms), outer = outer.at(ms),
    )

    override fun toString() = name
}

object Choreographies {
    /** Cold start onto Listen. The ground starts lifting once the lines are on their way. */
    val rise = Choreography(
        name = "rise", durationMs = 800f, landing = Landing.MIST,
        word = Track(Key(0f, 1f), Key(60f, 1f), Key(420f, 0f, Eases.Settle)),
        rise = Track(Key(0f, 0f), Key(60f, 0f), Key(480f, 5f, Eases.Settle)),
        travel = Track(Key(0f, 0f), Key(140f, 0f), Key(660f, 1f, Eases.Calm)),
        outer = Track(Key(0f, 0f), Key(420f, 0f), Key(700f, 1f, Eases.Settle)),
        // The lines hand over to the real mist underneath, already showing where the ground has lifted.
        mark = Track(Key(0f, 1f), Key(620f, 1f), Key(800f, 0f, Eases.Calm)),
        lift = Track(Key(0f, 0f), Key(240f, 0f), Key(780f, 1f, Eases.Calm)),
    )

    /** The very first launch: the lockup settles onto the introduction's, then hands over to it. */
    val welcome = Choreography(
        name = "welcome", durationMs = 800f, landing = Landing.WELCOME,
        travel = Track(Key(0f, 0f), Key(120f, 0f), Key(620f, 1f, Eases.Calm)),
        ground = Track(Key(0f, 1f), Key(420f, 1f), Key(800f, 0f, Eases.Calm)),
        word = Track(Key(0f, 1f), Key(640f, 1f), Key(800f, 0f)),
        mark = Track(Key(0f, 1f), Key(640f, 1f), Key(800f, 0f)),
    )

    /** In place: the wordmark lifts away, the mark fades, the ground lifts. */
    fun lift(durationMs: Float) = Choreography(
        name = "lift-${durationMs.toInt()}", durationMs = durationMs, landing = Landing.NOWHERE,
        word = Track(Key(0f, 1f), Key(durationMs * 0.6f, 0f, Eases.Settle)),
        rise = Track(Key(0f, 0f), Key(durationMs * 0.8f, 4f, Eases.Settle)),
        mark = Track(Key(0f, 1f), Key(durationMs * 0.75f, 0f, Eases.Settle)),
        lift = Track(Key(0f, 0f), Key(durationMs, 1f, Eases.Calm)),
    )

    val warm = lift(300f)
    val coldInPlace = lift(500f)

    /** Reduced motion: everything fades together, nothing moves. */
    val fade = Choreography(
        name = "fade", durationMs = 200f, landing = Landing.NOWHERE,
        ground = Track(Key(0f, 1f), Key(200f, 0f, Eases.Settle)),
        word = Track(Key(0f, 1f), Key(200f, 0f, Eases.Settle)),
        mark = Track(Key(0f, 1f), Key(200f, 0f, Eases.Settle)),
    )
}

/** Which intro a launch gets. */
enum class IntroKind {
    /** A fresh process: the full intro. */
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

/**
 * Picks the choreography, or none. [landing] is what is on screen under the overlay: the first
 * launch lands on the introduction only when cold (a warm start there just lifts).
 */
fun choreographyFor(kind: IntroKind, reduced: Boolean, landing: Landing): Choreography? = when {
    kind == IntroKind.NONE -> null
    reduced -> Choreographies.fade
    kind == IntroKind.WARM -> Choreographies.warm
    landing == Landing.WELCOME -> Choreographies.welcome
    landing == Landing.MIST -> Choreographies.rise
    else -> Choreographies.coldInPlace
}

/**
 * One of Horizon's lines, simplified to what the morph needs, in the mark's units: from ([x0], [y0])
 * to ([x1], [y1]), [thick] at its widest, tapered to points.
 */
data class MarkLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val thick: Float)

/** ic_splash_mark's three lines, top to bottom (the outlines in [Lockup.MARK_PATHS], read off by hand). */
val MARK_LINES = listOf(
    MarkLine(22f, 52.1f, 86f, 49f, 4.6f),
    MarkLine(30f, 59.6f, 70f, 59.6f, 2.6f),
    MarkLine(40f, 66.4f, 65f, 66.6f, 1.5f),
)

/** Horizon's lines land on the mist's middle three; the outer two (0 and 4) fade in. */
const val MIST_FIRST_LANDING = 1

/** A tapered line's thickness profile along it: pointed at both ends, full in the middle. */
fun taper(s: Float): Float = sin(PI.toFloat() * s.coerceIn(0f, 1f)).let { if (it <= 0f) 0f else Math.pow(it.toDouble(), 0.7).toFloat() }

/** One of the mist's long soft lines, in pixels of the mist's own box. */
data class WaveLine(
    val y: Float,
    val amplitude: Float,
    val wavelength: Float,
    val phase: Float,
    val from: Float,
    val to: Float,
    /** Opacity on top of the mist's colour. */
    val alpha: Float,
) {
    fun yAt(x: Float): Float = y + amplitude * sin(2f * PI.toFloat() * x / wavelength + phase)
}

/** The mist's lines (ui/Theme.kt `Mist`) in a [w] x [h] box. [strength] lifts them while sound plays. */
fun mistLines(w: Float, h: Float, strength: Float): List<WaveLine> {
    val lines = 5
    return (0 until lines).map { i ->
        val t = i / (lines - 1f)
        WaveLine(
            y = h * (0.15f + 0.7f * t), amplitude = h * 0.06f, wavelength = w * (0.55f + 0.12f * i),
            phase = i * 1.7f, from = w * (0.04f * (i % 3)), to = w * (1f - 0.05f * ((i + 1) % 3)),
            alpha = (0.10f + 0.22f * strength) * (1f - 0.5f * abs(t - 0.5f) * 2f),
        )
    }
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 0..1 across [edge0]..[edge1], smoothly. */
fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

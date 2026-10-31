package app.beansontoast.brume.core.sound

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.tanh

/**
 * The whole sound, as samples: two layers (one noise, one ambience), their levels, the fade-in, the
 * pause and "Too loud" fades, the sleep timer and the left/right [Balance]. The app only pumps [render] into an AudioTrack.
 *
 * Threading: the UI [post]s commands from any thread; the render thread applies them at the start of
 * each block. The sleep timer counts rendered frames, so Doze can never make it late.
 */
class SoundEngine(val rate: Int, private val seed: Long = 1L) {

    sealed interface Command {
        data class Noise(val colour: NoiseColour?) : Command
        data class Nature(val ambience: Ambience?) : Command
        data class NoiseLevel(val level: Float) : Command
        data class NatureLevel(val level: Float) : Command
        /** Sounds (noise and ambience) or the tone pattern: the other group ramps to silence. */
        data class Play(val mode: Mode) : Command
        data class Tones(val settings: ToneSettings) : Command
        data class ToneLevel(val level: Float) : Command
        /** Left/right balance for every layer, sounds and tones alike; see [Balance]. */
        data class Balance(val balance: Float) : Command
        /** Starts (or restarts) the sleep timer from now; null turns it off. */
        data class Timer(val minutes: Int?) : Command
        data object Start : Command
        data object Pause : Command
        data object TooLoud : Command
    }

    /** The tone pattern plays alone: noise under it would cover the very tones it is made of. */
    enum class Mode { SOUNDS, TONES }
    enum class State { STOPPED, PLAYING, FADING_OUT }
    enum class StopReason { PAUSED, TIMER, TOO_LOUD }

    private val commands = ConcurrentLinkedQueue<Command>()

    @Volatile var state = State.STOPPED; private set
    @Volatile var stopReason: StopReason? = null; private set
    /** Frames rendered while sounding, for the listening log. */
    @Volatile var playedFrames = 0L; private set
    @Volatile var timerRemainingFrames: Long? = null; private set

    internal val tonesGainNow get() = tonesGain
    internal val leftGainNow get() = leftGain
    internal val rightGainNow get() = rightGain

    private var layerCount = 0L
    private var noise: Source? = null
    private var nature: Source? = null
    private var noiseTarget = 0f; private var noiseGain = 0f
    private var natureTarget = 0f; private var natureGain = 0f
    private var noiseLevel = LevelRules.DEFAULT_LEVEL
    private var natureLevel = LevelRules.DEFAULT_LEVEL
    private var mode = Mode.SOUNDS
    private var tones: ToneSource? = null
    private var tonesTarget = 0f; private var tonesGain = 0f
    private var tonesLevel = TonePattern.DEFAULT_LEVEL
    // Per-channel balance gains, each in 0..1, ramped like the layer gains so a change never clicks.
    private var leftTarget = 1f; private var leftGain = 1f
    private var rightTarget = 1f; private var rightGain = 1f

    private val rampStep = (1.0 / (rate * LevelRules.GAIN_RAMP_SECONDS)).toFloat()
    private val fadeInFrames = (rate * LevelRules.FADE_IN_SECONDS).toLong()
    private var fadeInAt = 0L
    private var master = 0f
    private var outStep = 0f
    private var pendingReason: StopReason? = null

    private var timerTotal = 0L
    private var timerElapsed = 0L
    private var timerFade = 0L

    fun post(command: Command) { commands.add(command) }

    private fun apply(command: Command) {
        when (command) {
            is Command.Noise -> { noise = command.colour?.let { noiseSource(it, rate, seed + ++layerCount) }; noiseGain = 0f; aim() }
            is Command.Nature -> { nature = command.ambience?.let { ambienceSource(it, rate, seed + ++layerCount) }; natureGain = 0f; aim() }
            is Command.NoiseLevel -> { noiseLevel = LevelRules.clampLevel(command.level); aim() }
            is Command.NatureLevel -> { natureLevel = LevelRules.clampLevel(command.level); aim() }
            is Command.Play -> { mode = command.mode; aim() }
            is Command.Tones -> {
                val source = tones
                if (source == null) tones = ToneSource(rate, seed + ++layerCount, command.settings) else source.configure(command.settings)
                aim()
            }
            is Command.ToneLevel -> { tonesLevel = LevelRules.clampLevel(command.level); aim() }
            is Command.Balance -> { leftTarget = Balance.left(command.balance); rightTarget = Balance.right(command.balance) }
            is Command.Timer -> {
                val minutes = command.minutes
                if (minutes == null) { timerTotal = 0; timerRemainingFrames = null } else {
                    timerTotal = minutes * 60L * rate
                    timerElapsed = 0
                    timerFade = minOf((rate * LevelRules.SLEEP_FADE_SECONDS).toLong(), timerTotal / 2)
                    timerRemainingFrames = timerTotal
                }
            }
            Command.Start -> {
                if (state != State.PLAYING) {
                    state = State.PLAYING; stopReason = null; pendingReason = null
                    fadeInAt = 0; master = 0f
                }
            }
            Command.Pause -> fadeOut(LevelRules.PAUSE_FADE_SECONDS, StopReason.PAUSED)
            Command.TooLoud -> fadeOut(LevelRules.GAIN_RAMP_SECONDS, StopReason.TOO_LOUD)
        }
    }

    private fun aim() {
        val sounds = mode == Mode.SOUNDS
        noiseTarget = if (noise == null || !sounds) 0f else LevelRules.gainFor(noiseLevel)
        natureTarget = if (nature == null || !sounds) 0f else LevelRules.gainFor(natureLevel)
        tonesTarget = if (tones == null || sounds) 0f else LevelRules.gainFor(tonesLevel)
    }

    private fun fadeOut(seconds: Double, reason: StopReason) {
        if (state == State.STOPPED) { stopReason = reason; return }
        state = State.FADING_OUT
        pendingReason = reason
        outStep = (master / (rate * seconds)).toFloat().coerceAtLeast(1e-6f)
    }

    /** Fills [out] with [frames] interleaved stereo frames. Allocates nothing unless a command arrived. */
    fun render(out: FloatArray, frames: Int) {
        while (true) apply(commands.poll() ?: break)
        for (i in 0 until frames) {
            if (state == State.STOPPED) { out[2 * i] = 0f; out[2 * i + 1] = 0f; continue }

            if (state == State.PLAYING && fadeInAt < fadeInFrames) {
                fadeInAt++
                master = ((1 - cos(PI * fadeInAt / fadeInFrames)) / 2).toFloat()
            } else if (state == State.FADING_OUT) {
                master -= outStep
                if (master <= SILENT) { master = 0f; stop(pendingReason ?: StopReason.PAUSED) }
            }

            var timerGain = 1f
            var timerDone = false
            if (timerTotal > 0 && state != State.STOPPED) {
                timerElapsed++
                val remaining = timerTotal - timerElapsed
                timerRemainingFrames = remaining
                if (remaining < timerFade) timerGain = ((1 - cos(PI * remaining / timerFade)) / 2).toFloat()
                timerDone = remaining <= 0
            }

            noiseGain = toward(noiseGain, noiseTarget)
            natureGain = toward(natureGain, natureTarget)
            tonesGain = toward(tonesGain, tonesTarget)
            leftGain = toward(leftGain, leftTarget)
            rightGain = toward(rightGain, rightTarget)
            val n = noise?.next() ?: 0f
            val a = nature?.next() ?: 0f
            // The tone layer advances only while it can be heard; it resumes under the 20 ms gain ramp.
            val t = if (tonesGain > 0f) tones?.next() ?: 0f else 0f
            val y = if (state == State.STOPPED) 0f else limit(n * noiseGain + a * natureGain + t * tonesGain) * master * timerGain
            // Every source is mono: one signal, then the balance stage splits it to the two channels.
            out[2 * i] = y * leftGain; out[2 * i + 1] = y * rightGain
            if (state != State.STOPPED) playedFrames++
            if (timerDone) { timerTotal = 0; timerRemainingFrames = null; stop(StopReason.TIMER) }
        }
    }

    private fun stop(reason: StopReason) {
        state = State.STOPPED; stopReason = reason; master = 0f
    }

    private fun toward(current: Float, target: Float): Float =
        if (current < target) minOf(target, current + rampStep) else maxOf(target, current - rampStep)

    /** What the engine is asking for right now, before the signal: for tests and the "never louder" rule. */
    internal fun effectiveGain(): Float {
        val timer = if (timerTotal > 0 && timerElapsed < timerTotal) {
            val remaining = timerTotal - timerElapsed
            if (remaining < timerFade) ((1 - cos(PI * remaining / timerFade)) / 2).toFloat() else 1f
        } else 1f
        return if (state == State.STOPPED) 0f else master * timer * (noiseGain + natureGain + tonesGain)
    }

    companion object {
        private const val SILENT = 1e-4f
        private const val KNEE = 0.5f
        private const val ROOM = 0.45f

        /** Linear to the knee (-6 dBFS), then a soft tanh shoulder that never reaches full scale. */
        fun limit(x: Float): Float {
            val m = abs(x)
            if (m <= KNEE) return x
            return sign(x) * (KNEE + ROOM * tanh((m - KNEE) / ROOM))
        }
    }
}

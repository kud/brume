package app.beansontoast.brume.core.sound

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.tanh

/**
 * The whole sound, as samples: the mix (any number of layers, noises and recordings, each sound at
 * most once, each with its own level and fading in on its own), the headroom that keeps a stack of
 * layers from clipping, the fade-in, the pause and "Too loud" fades, the sleep timer and the left/right [Balance]. The app only pumps [render] into an AudioTrack.
 *
 * Threading: the UI [post]s commands from any thread; the render thread applies them at the start of
 * each block. The sleep timer counts rendered frames, so Doze can never make it late.
 */
class SoundEngine(val rate: Int, private val seed: Long = 1L) {

    sealed interface Command {
        /**
         * Adds a layer, or changes the tone of one already there. It fades in from silence over
         * [LevelRules.FADE_IN_SECONDS] whatever else plays. A recording needs its decoded [loop];
         * without one it is ignored.
         */
        data class Add(val sound: Sound, val level: Float, val tone: Float = Tone.DEFAULT, val loop: Loop? = null) : Command
        /** Fades a layer out over [LevelRules.REMOVE_FADE_SECONDS], then drops it. */
        data class Remove(val sound: Sound) : Command
        data class Level(val sound: Sound, val level: Float) : Command
        data class ToneOf(val sound: Sound, val tone: Float) : Command
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

    private class Layer(val sound: Sound, val source: Source, var level: Float) {
        var target = 0f; var gain = 0f
        /** 0..1: the layer's own cosine fade-in, then its fade-out on removal. */
        var fade = 0f; var fadeAt = 0L
        var removing = false
    }

    /** Touched only on the render thread. Never shrinks while rendering: dropped layers go at the next block. */
    private val layers = ArrayList<Layer>()
    /** The sounds in the mix as the render thread last saw them, for tests and the UI's sanity checks. */
    @Volatile var sounds: List<Sound> = emptyList(); private set
    private var trimTarget = 1f; private var trim = 1f
    private var mode = Mode.SOUNDS
    private var tones: ToneSource? = null
    private var tonesTarget = 0f; private var tonesGain = 0f
    private var tonesLevel = TonePattern.DEFAULT_LEVEL
    // Per-channel balance gains, each in 0..1, ramped like the layer gains so a change never clicks.
    private var leftTarget = 1f; private var leftGain = 1f
    private var rightTarget = 1f; private var rightGain = 1f

    private val rampStep = (1.0 / (rate * LevelRules.GAIN_RAMP_SECONDS)).toFloat()
    private val fadeInFrames = (rate * LevelRules.FADE_IN_SECONDS).toLong()
    private val removeFrames = (rate * LevelRules.REMOVE_FADE_SECONDS).toLong()
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
            is Command.Add -> add(command)
            is Command.Remove -> layers.firstOrNull { it.sound == command.sound && !it.removing }?.let {
                it.removing = true; it.fadeAt = 0L; aim()
            }
            is Command.Level -> layers.firstOrNull { it.sound == command.sound }?.let { it.level = LevelRules.clampLevel(command.level); aim() }
            is Command.ToneOf -> (layers.firstOrNull { it.sound == command.sound }?.source as? TonedNoise)?.setTone(command.tone)
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

    private fun add(command: Command.Add) {
        val existing = layers.firstOrNull { it.sound == command.sound }
        if (existing != null && !existing.removing) {
            (existing.source as? TonedNoise)?.setTone(command.tone)
            return
        }
        val source = when (val sound = command.sound) {
            is Sound.Noise -> TonedNoise(sound.colour, rate, seed + ++layerCount, command.tone)
            is Sound.Recorded -> LoopSource(command.loop ?: return, rate, seed + ++layerCount)
            is Sound.Band -> bandSource(sound.centre, sound.widthTenths, rate, seed + ++layerCount)
        }
        // A layer still fading out is replaced by the new one, which starts from silence.
        if (existing != null) layers.remove(existing)
        layers.add(Layer(command.sound, source, LevelRules.clampLevel(command.level)))
        sounds = layers.filterNot { it.removing }.map { it.sound }
        aim()
    }

    private fun aim() {
        val playSounds = mode == Mode.SOUNDS
        var sum = 0f
        for (layer in layers) {
            // A layer on its way out keeps its level: its own fade takes it down, never a 20 ms cut.
            layer.target = if (!playSounds) 0f else LevelRules.gainFor(layer.level)
            sum += layer.target
        }
        // Headroom: the layers together never ask for more than one layer at full level. Below that
        // nothing changes; above it every layer comes down together, keeping the balance between them.
        trimTarget = if (sum > HEADROOM) HEADROOM / sum else 1f
        tonesTarget = if (tones == null || playSounds) 0f else LevelRules.gainFor(tonesLevel)
        sounds = layers.filterNot { it.removing }.map { it.sound }
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

            trim = toward(trim, trimTarget)
            tonesGain = toward(tonesGain, tonesTarget)
            leftGain = toward(leftGain, leftTarget)
            rightGain = toward(rightGain, rightTarget)
            var mixed = 0f
            for (l in layers.indices) {
                val layer = layers[l]
                layer.gain = toward(layer.gain, layer.target)
                if (layer.removing) {
                    if (layer.fadeAt < removeFrames) layer.fadeAt++
                    layer.fade = minOf(layer.fade, ((1 + cos(PI * layer.fadeAt / removeFrames)) / 2).toFloat())
                } else if (layer.fadeAt < fadeInFrames && state != State.STOPPED) {
                    layer.fadeAt++
                    layer.fade = ((1 - cos(PI * layer.fadeAt / fadeInFrames)) / 2).toFloat()
                }
                // Every layer runs on even while silent, so its place in the loop or the noise moves on.
                val x = layer.source.next()
                mixed += x * layer.gain * layer.fade
            }
            // The tone layer advances only while it can be heard; it resumes under the 20 ms gain ramp.
            val t = if (tonesGain > 0f) tones?.next() ?: 0f else 0f
            val y = if (state == State.STOPPED) 0f else limit(mixed * trim + t * tonesGain) * master * timerGain
            // Every source is mono: one signal, then the balance stage splits it to the two channels.
            out[2 * i] = y * leftGain; out[2 * i + 1] = y * rightGain
            if (state != State.STOPPED) playedFrames++
            if (timerDone) { timerTotal = 0; timerRemainingFrames = null; stop(StopReason.TIMER) }
        }
        if (layers.any { it.removing && it.fadeAt >= removeFrames }) {
            layers.removeAll { it.removing && it.fadeAt >= removeFrames }
            aim()
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
        val mixed = layers.sumOf { (it.gain * it.fade).toDouble() }.toFloat()
        return if (state == State.STOPPED) 0f else master * timer * (mixed * trim + tonesGain)
    }

    companion object {
        private const val SILENT = 1e-4f
        /** The most the layers may ask for together: one layer at full level. */
        val HEADROOM = LevelRules.gainFor(1f)
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

package app.beansontoast.brume.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import app.beansontoast.brume.data.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Why playback last stopped on its own, shown once under the controls. Never a colour. */
enum class Status { TOO_LOUD, ROUTE, FOCUS, TIMER }

data class PlayerUi(
    val noise: NoiseColour? = NoiseColour.PINK,
    val ambience: Ambience? = null,
    val noiseLevel: Float = LevelRules.DEFAULT_LEVEL,
    val ambienceLevel: Float = LevelRules.DEFAULT_LEVEL,
    val playing: Boolean = false,
    val timerMinutes: Int? = null,
    val timerRemainingMinutes: Int? = null,
    val status: Status? = null,
) {
    val hasSound get() = noise != null || ambience != null
}

/**
 * The app side of the sound: holds the user's choices, drives [SoundEngine] and starts the
 * foreground service that pumps it. It never touches the system volume.
 */
class Player(private val context: Context, private val journal: Journal, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    val engine = SoundEngine(
        rate = context.getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000,
        seed = System.nanoTime(),
    )

    private val _ui = MutableStateFlow(load())
    val ui: StateFlow<PlayerUi> = _ui

    private var sessionStartedAt = 0L
    private var sessionStartFrames = 0L
    @Volatile private var pendingEnd: EndedBy? = null
    private var ticker: Job? = null

    init {
        val ui = _ui.value
        engine.post(Command.Noise(ui.noise)); engine.post(Command.Nature(ui.ambience))
        engine.post(Command.NoiseLevel(ui.noiseLevel)); engine.post(Command.NatureLevel(ui.ambienceLevel))
    }

    fun selectNoise(colour: NoiseColour?) { change { it.copy(noise = colour) }; engine.post(Command.Noise(colour)) }
    fun selectAmbience(ambience: Ambience?) { change { it.copy(ambience = ambience) }; engine.post(Command.Nature(ambience)) }
    fun setNoiseLevel(level: Float) { change { it.copy(noiseLevel = LevelRules.clampLevel(level)) }; engine.post(Command.NoiseLevel(level)) }
    fun setAmbienceLevel(level: Float) { change { it.copy(ambienceLevel = LevelRules.clampLevel(level)) }; engine.post(Command.NatureLevel(level)) }

    fun setTimer(minutes: Int?) {
        change { it.copy(timerMinutes = minutes, timerRemainingMinutes = if (it.playing) minutes else null) }
        if (_ui.value.playing) engine.post(Command.Timer(minutes))
    }

    fun toggle() = if (_ui.value.playing) pause(EndedBy.USER) else play()

    fun play() {
        val ui = _ui.value
        if (!ui.hasSound) return
        pendingEnd = null
        engine.post(Command.Timer(ui.timerMinutes))
        engine.post(Command.Start)
        if (!ui.playing) { sessionStartedAt = System.currentTimeMillis(); sessionStartFrames = engine.playedFrames }
        _ui.update { it.copy(playing = true, status = null, timerRemainingMinutes = it.timerMinutes) }
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        startTicker()
    }

    fun pause(reason: EndedBy) {
        if (!_ui.value.playing) return
        pendingEnd = reason
        engine.post(Command.Pause)
    }

    /** Cuts to silence at once, and lowers each layer one step for the next start. */
    fun tooLoud() {
        pendingEnd = EndedBy.TOO_LOUD
        engine.post(Command.TooLoud)
        change { it.copy(noiseLevel = LevelRules.afterTooLoud(it.noiseLevel), ambienceLevel = LevelRules.afterTooLoud(it.ambienceLevel)) }
        engine.post(Command.NoiseLevel(_ui.value.noiseLevel)); engine.post(Command.NatureLevel(_ui.value.ambienceLevel))
    }

    /** Called on the main thread by the service once the engine has gone silent. */
    fun onEngineStopped() {
        if (engine.state != SoundEngine.State.STOPPED || !_ui.value.playing) return
        val ended = when (engine.stopReason) {
            SoundEngine.StopReason.TIMER -> EndedBy.TIMER
            SoundEngine.StopReason.TOO_LOUD -> EndedBy.TOO_LOUD
            else -> pendingEnd ?: EndedBy.USER
        }
        val ui = _ui.value
        val seconds = (engine.playedFrames - sessionStartFrames) / engine.rate
        val session = ListeningSession(
            startedAt = sessionStartedAt, seconds = seconds,
            noise = ui.noise?.name, noiseLevel = ui.noise?.let { ui.noiseLevel },
            ambience = ui.ambience?.name, ambienceLevel = ui.ambience?.let { ui.ambienceLevel },
            endedBy = ended,
        )
        scope.launch { journal.log(session) }
        val status = when (ended) {
            EndedBy.TOO_LOUD -> Status.TOO_LOUD
            EndedBy.ROUTE_CHANGE -> Status.ROUTE
            EndedBy.FOCUS_LOSS -> Status.FOCUS
            EndedBy.TIMER -> Status.TIMER
            EndedBy.USER -> null
        }
        _ui.update { it.copy(playing = false, timerRemainingMinutes = null, status = status) }
        ticker?.cancel()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                val frames = engine.timerRemainingFrames
                _ui.update { it.copy(timerRemainingMinutes = frames?.let { f -> ceil(f / engine.rate / 60.0).toInt() }) }
            }
        }
    }

    private fun change(transform: (PlayerUi) -> PlayerUi) {
        _ui.update(transform)
        val ui = _ui.value
        prefs.edit()
            .putString(NOISE, ui.noise?.name ?: NONE).putString(AMBIENCE, ui.ambience?.name ?: NONE)
            .putFloat(NOISE_LEVEL, ui.noiseLevel).putFloat(AMBIENCE_LEVEL, ui.ambienceLevel)
            .putInt(TIMER, ui.timerMinutes ?: 0)
            .apply()
    }

    private fun load(): PlayerUi {
        val defaults = PlayerUi()
        fun noise(name: String?) = if (name == NONE) null else NoiseColour.entries.firstOrNull { it.name == name } ?: defaults.noise
        fun ambience(name: String?) = if (name == NONE) null else Ambience.entries.firstOrNull { it.name == name }
        return PlayerUi(
            noise = noise(prefs.getString(NOISE, null)),
            ambience = ambience(prefs.getString(AMBIENCE, NONE)),
            noiseLevel = prefs.getFloat(NOISE_LEVEL, defaults.noiseLevel),
            ambienceLevel = prefs.getFloat(AMBIENCE_LEVEL, defaults.ambienceLevel),
            timerMinutes = prefs.getInt(TIMER, 0).takeIf { it > 0 },
        )
    }

    private companion object {
        const val NONE = "none"
        const val NOISE = "noise"; const val AMBIENCE = "ambience"
        const val NOISE_LEVEL = "noise_level"; const val AMBIENCE_LEVEL = "ambience_level"
        const val TIMER = "timer"
    }
}

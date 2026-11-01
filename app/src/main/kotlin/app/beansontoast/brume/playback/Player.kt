package app.beansontoast.brume.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.journal.ListeningSession
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.Loop
import app.beansontoast.brume.core.sound.Mix
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.Tone
import app.beansontoast.brume.core.sound.Balance
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.core.sound.TonePattern
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.data.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    /** The mix: any number of layers, each sound at most once, in the order they were added. */
    val mix: List<MixLayer> = listOf(MixLayer(Sound.Noise(NoiseColour.PINK))),
    /** Each layer's level, for the output in use. Per output device, on this phone only: never synced. */
    val levels: Map<Sound, Float> = emptyMap(),
    val playing: Boolean = false,
    val timerMinutes: Int? = null,
    val timerRemainingMinutes: Int? = null,
    val status: Status? = null,
    val mode: Mode = Mode.SOUNDS,
    val tones: ToneSettings = ToneSettings(),
    val toneLevel: Float = TonePattern.DEFAULT_LEVEL,
    /** -1 left only .. 0 centre .. +1 right only; see [Balance]. Per output device, never synced. */
    val balance: Float = Balance.CENTRE,
    /** Where the sound plays now; null until the first read. */
    val output: Output? = null,
) {
    /** The two-layer form the renders and older screens were written against. */
    constructor(
        noise: NoiseColour?, ambience: Ambience?,
        noiseLevel: Float = LevelRules.DEFAULT_LEVEL, ambienceLevel: Float = LevelRules.DEFAULT_LEVEL,
        playing: Boolean = false, timerMinutes: Int? = null, timerRemainingMinutes: Int? = null, status: Status? = null,
        mode: Mode = Mode.SOUNDS, tones: ToneSettings = ToneSettings(), toneLevel: Float = TonePattern.DEFAULT_LEVEL,
        balance: Float = Balance.CENTRE, output: Output? = null,
    ) : this(
        listOfNotNull(noise?.let { MixLayer(Sound.Noise(it)) }, ambience?.let { MixLayer(Sound.Recorded(it)) }),
        listOfNotNull(noise?.let { Sound.Noise(it) to noiseLevel }, ambience?.let { Sound.Recorded(it) to ambienceLevel }).toMap(),
        playing, timerMinutes, timerRemainingMinutes, status, mode, tones, toneLevel, balance, output,
    )

    val sounds: List<Sound> get() = mix.map { it.sound }
    fun level(sound: Sound): Float = levels[sound] ?: LevelRules.DEFAULT_LEVEL
    fun tone(sound: Sound): Float = mix.firstOrNull { it.sound == sound }?.tone ?: Tone.DEFAULT
    /** The first noise and the first recording, for the art and the texture that draw one of each. */
    val noise: NoiseColour? get() = sounds.firstNotNullOfOrNull { (it as? Sound.Noise)?.colour }
    val ambience: Ambience? get() = sounds.firstNotNullOfOrNull { (it as? Sound.Recorded)?.ambience }
    val hasSound get() = mode == Mode.TONES || mix.isNotEmpty()
}

/**
 * The app side of the sound: holds the user's choices, drives [SoundEngine] and starts the
 * foreground service that pumps it. It never touches the system volume.
 */
class Player(private val context: Context, private val journal: Journal, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    /** Balance and levels per output device (ADR 0002). On this phone only: levels never sync. */
    private val outputs = context.getSharedPreferences("outputs", Context.MODE_PRIVATE)
    /** A device that arrived while sound played: its settings wait for the stop. */
    private var pendingOutput: Output? = null

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
        ui.mix.forEach { post(it, ui.level(it.sound)) }
        engine.post(Command.Tones(ui.tones)); engine.post(Command.ToneLevel(ui.toneLevel)); engine.post(Command.Play(ui.mode))
        engine.post(Command.Balance(ui.balance))
    }

    fun setBalance(balance: Float) {
        change { it.copy(balance = Balance.snap(balance)) }
        engine.post(Command.Balance(_ui.value.balance))
    }

    /**
     * The output changed. Its own balance and levels come back, or, for a device never seen, the
     * current balance with each level at most the soft default. Never while sound plays: a level only
     * changes on a user action, so they wait for the stop (the service pauses on any device change).
     */
    fun onOutput(output: Output) {
        val was = _ui.value.output
        if (was?.key == output.key) return
        if (was == null) { _ui.update { it.copy(output = output) }; save(output); return }
        if (_ui.value.playing) { pendingOutput = output; return }
        useOutput(output)
    }

    private fun useOutput(output: Output) {
        val key = output.key
        val known = outputs.contains("$key|balance")
        fun level(name: String, current: Float) =
            if (known) LevelRules.clampLevel(outputs.getFloat("$key|$name", current)) else minOf(current, LevelRules.DEFAULT_LEVEL)
        change {
            it.copy(
                output = output,
                levels = it.sounds.associateWith { s -> deviceLevel(key, s) ?: if (known) it.level(s) else minOf(it.level(s), LevelRules.DEFAULT_LEVEL) },
                toneLevel = level("tone", it.toneLevel),
                balance = if (known) Balance.snap(outputs.getFloat("$key|balance", it.balance)) else it.balance,
            )
        }
        val ui = _ui.value
        ui.sounds.forEach { engine.post(Command.Level(it, ui.level(it))) }
        engine.post(Command.ToneLevel(ui.toneLevel)); engine.post(Command.Balance(ui.balance))
    }

    /**
     * A sound's level stored for one output device. Before the mixer each device kept one "noise"
     * and one "ambience" level; a sound with no level of its own takes its old slot's.
     */
    private fun deviceLevel(device: String, sound: Sound): Float? {
        val own = "$device|${sound.key}"
        val legacy = "$device|" + if (sound is Sound.Noise) "noise" else "ambience"
        return when {
            outputs.contains(own) -> outputs.getFloat(own, LevelRules.DEFAULT_LEVEL)
            outputs.contains(legacy) -> outputs.getFloat(legacy, LevelRules.DEFAULT_LEVEL)
            else -> null
        }?.let(LevelRules::clampLevel)
    }

    private fun save(output: Output?) {
        val key = output?.key ?: return
        val ui = _ui.value
        val edit = outputs.edit()
        ui.levels.forEach { (sound, level) -> edit.putFloat("$key|${sound.key}", level) }
        edit.putFloat("$key|tone", ui.toneLevel).putFloat("$key|balance", ui.balance).apply()
    }

    fun selectMode(mode: Mode) { change { it.copy(mode = mode) }; engine.post(Command.Play(mode)) }
    fun setToneLevel(level: Float) { change { it.copy(toneLevel = LevelRules.clampLevel(level)) }; engine.post(Command.ToneLevel(level)) }
    fun setPitch(hz: Int) = tones { it.copy(pitch = TonePattern.clampPitch(hz)) }
    fun setTrim(tone: Int, trim: Float) = tones { t -> t.copy(trims = t.trims.mapIndexed { i, v -> if (i == tone) trim.coerceIn(0f, 1f) else v }) }
    fun setMatch(match: Boolean) = tones { it.copy(match = match) }

    private fun tones(transform: (ToneSettings) -> ToneSettings) {
        val before = _ui.value.tones
        change { it.copy(tones = transform(it.tones)) }
        val after = _ui.value.tones
        if (after.pitch != before.pitch || after.trims != before.trims) prefs.edit().putLong(TONES_AT, System.currentTimeMillis()).apply()
        engine.post(Command.Tones(_ui.value.tones))
    }

    /** When pitch or trims last changed here, epoch ms; 0 if never. The later change wins in sync. */
    val tonesAt: Long get() = prefs.getLong(TONES_AT, 0L)

    /**
     * Takes pitch and trims chosen on another phone. Never while playing: a trim is a level, and a
     * level never changes without a user action here. Returns false when skipped; the next sync after
     * the sound stops brings them in.
     */
    fun applySyncedTones(pitch: Int, trims: List<Float>, at: Long): Boolean {
        if (_ui.value.playing || trims.size != TonePattern.TONES) return false
        change { it.copy(tones = it.tones.copy(pitch = TonePattern.clampPitch(pitch), trims = trims.map { t -> t.coerceIn(0f, 1f) })) }
        prefs.edit().putLong(TONES_AT, at).apply()
        engine.post(Command.Tones(_ui.value.tones))
        return true
    }

    /** Recordings decoded for the mix, shared by the engine's layers. Dropped when their layer goes. */
    private val loops = java.util.concurrent.ConcurrentHashMap<Ambience, Loop>()

    /** Hands a layer to the engine; a recording is decoded first, off the main thread, then fades in. */
    private fun post(layer: MixLayer, level: Float) {
        when (val sound = layer.sound) {
            is Sound.Noise -> engine.post(Command.Add(sound, level, layer.tone))
            is Sound.Recorded -> scope.launch(Dispatchers.Default) {
                val loop = loops[sound.ambience] ?: runCatching { Loops.decode(context, sound.ambience) }.getOrNull()?.also { loops[sound.ambience] = it }
                // Still wanted once decoded, at the level it has now: a slider moved meanwhile wins.
                if (loop != null && _ui.value.sounds.contains(sound)) engine.post(Command.Add(sound, _ui.value.level(sound), loop = loop))
            }
        }
    }

    /** A new layer, at this output's level for it, or at most the soft default: adding never startles. */
    fun add(sound: Sound) {
        if (sound in _ui.value.sounds) return
        val level = _ui.value.output?.key?.let { deviceLevel(it, sound) } ?: prefs.getFloat(LEVEL + sound.key, LevelRules.DEFAULT_LEVEL).coerceAtMost(LevelRules.DEFAULT_LEVEL)
        change { it.copy(mix = it.mix + MixLayer(sound), levels = it.levels + (sound to level)) }
        post(MixLayer(sound), level)
    }

    fun remove(sound: Sound) {
        change { it.copy(mix = it.mix.filterNot { l -> l.sound == sound }, levels = it.levels - sound) }
        engine.post(Command.Remove(sound))
        (sound as? Sound.Recorded)?.let { loops.remove(it.ambience) }
        pauseIfSilent()
    }

    fun setLevel(sound: Sound, level: Float) {
        if (sound !in _ui.value.sounds) return
        val l = LevelRules.clampLevel(level)
        change { it.copy(levels = it.levels + (sound to l)) }
        engine.post(Command.Level(sound, l))
    }

    fun setTone(sound: Sound, tone: Float) {
        val t = Tone.snap(tone)
        change { it.copy(mix = it.mix.map { l -> if (l.sound == sound) l.copy(tone = t) else l }) }
        engine.post(Command.ToneOf(sound, t))
    }

    /** A scene's mix comes back: what is not in it fades out, what is new fades in, the rest plays on. */
    fun setMix(layers: List<MixLayer>) {
        val now = _ui.value.sounds
        now.filterNot { s -> layers.any { it.sound == s } }.forEach(::remove)
        layers.forEach { layer ->
            if (layer.sound !in _ui.value.sounds) add(layer.sound)
            if (layer.tone != _ui.value.tone(layer.sound)) setTone(layer.sound, layer.tone)
        }
    }

    /** Unchoosing the last layer while playing ends the session, so the app never "plays" silence. */
    private fun pauseIfSilent() { if (_ui.value.playing && !_ui.value.hasSound) pause(EndedBy.USER) }

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
        change {
            it.copy(levels = it.levels.mapValues { (_, l) -> LevelRules.afterTooLoud(l) }, toneLevel = LevelRules.afterTooLoud(it.toneLevel))
        }
        _ui.value.levels.forEach { (s, l) -> engine.post(Command.Level(s, l)) }
        engine.post(Command.ToneLevel(_ui.value.toneLevel))
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
        // The log has no tone column yet: a tone session goes in the sound slot as TONE_PATTERN, with
        // its level and never its pitch, so the schema (and every stored row) stays as it is.
        val tones = ui.mode == Mode.TONES
        val noises = ui.sounds.filterIsInstance<Sound.Noise>()
        val recorded = ui.sounds.filterIsInstance<Sound.Recorded>()
        val session = ListeningSession(
            startedAt = sessionStartedAt, seconds = seconds,
            // The log keeps its two columns: every noise in the first, every recording in the second,
            // joined by "+", each with the loudest level of its group.
            noise = if (tones) TONE_PATTERN else noises.joinToString("+") { it.colour.name }.ifEmpty { null },
            noiseLevel = if (tones) ui.toneLevel else noises.maxOfOrNull { ui.level(it) },
            ambience = if (tones) null else recorded.joinToString("+") { it.ambience.name }.ifEmpty { null },
            ambienceLevel = if (tones) null else recorded.maxOfOrNull { ui.level(it) },
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
        pendingOutput?.let { pendingOutput = null; useOutput(it) }
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
        val edit = prefs.edit()
        ui.levels.forEach { (s, l) -> edit.putFloat(LEVEL + s.key, l) }
        edit.putString(MIX, Mix(ui.mix).encode())
        edit.putInt(TIMER, ui.timerMinutes ?: 0)
            .putString(MODE, ui.mode.name).putFloat(TONE_LEVEL, ui.toneLevel).putInt(PITCH, ui.tones.pitch)
            .putString(TRIMS, ui.tones.trims.joinToString(","))
            .putFloat(BALANCE, ui.balance)
            .apply()
        // While a new device waits for the stop, what is shown still belongs to the old one.
        if (pendingOutput == null) save(ui.output)
    }

    private fun load(): PlayerUi {
        val defaults = PlayerUi()
        // Before the mixer: one noise and one nature sound, each with one level. They become the first
        // two layers (surf as the recorded sea), keeping their levels; then the old keys are left alone.
        val mix = Mix.decode(prefs.getString(MIX, null))?.layers ?: run {
            val noise = prefs.getString(NOISE, null).let { n -> if (n == NONE) null else NoiseColour.entries.firstOrNull { it.name == n } ?: NoiseColour.PINK }
            val ambience = Ambience.stored(prefs.getString(AMBIENCE, NONE))
            listOfNotNull(noise?.let { MixLayer(Sound.Noise(it)) }, ambience?.let { MixLayer(Sound.Recorded(it)) })
        }
        fun legacy(sound: Sound) = prefs.getFloat(if (sound is Sound.Noise) NOISE_LEVEL else AMBIENCE_LEVEL, LevelRules.DEFAULT_LEVEL)
        return PlayerUi(
            mix = mix,
            levels = mix.associate { it.sound to LevelRules.clampLevel(prefs.getFloat(LEVEL + it.sound.key, legacy(it.sound))) },
            timerMinutes = prefs.getInt(TIMER, 0).takeIf { it > 0 },
            mode = Mode.entries.firstOrNull { it.name == prefs.getString(MODE, null) } ?: defaults.mode,
            toneLevel = prefs.getFloat(TONE_LEVEL, defaults.toneLevel),
            balance = Balance.snap(prefs.getFloat(BALANCE, defaults.balance)),
            // The match tone is never restored: a start always plays the pattern the screen shows.
            tones = ToneSettings(
                pitch = TonePattern.clampPitch(prefs.getInt(PITCH, TonePattern.DEFAULT_PITCH)),
                trims = prefs.getString(TRIMS, null)?.split(",")?.mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }
                    ?.takeIf { it.size == TonePattern.TONES } ?: defaults.tones.trims,
            ),
        )
    }

    private companion object {
        const val NONE = "none"
        const val NOISE = "noise"; const val AMBIENCE = "ambience"
        const val MIX = "mix"; const val LEVEL = "level|"
        const val NOISE_LEVEL = "noise_level"; const val AMBIENCE_LEVEL = "ambience_level"
        const val TIMER = "timer"
        const val MODE = "mode"; const val TONE_LEVEL = "tone_level"; const val PITCH = "tone_pitch"; const val TRIMS = "tone_trims"; const val TONES_AT = "tones_at"
        const val BALANCE = "balance"
        const val TONE_PATTERN = "TONE_PATTERN"
    }
}

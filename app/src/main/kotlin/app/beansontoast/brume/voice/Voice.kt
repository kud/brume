package app.beansontoast.brume.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import app.beansontoast.brume.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The guide's voice (Erwann, 2026-11-01): pre-written lines read by the phone's own text-to-speech,
 * the offline system engine. It needs no permission and nothing from the network. It speaks the
 * language the words are in (`voice_language`: French with the French strings, else English),
 * slowly, at a neutral pitch, and only as loud as the session ([say]'s volume, the bell's rule).
 *
 * It is mixed over whatever sound plays: it asks for no audio focus and ducks nothing, so the
 * player never hears about it. If there is no engine, or no voice for the language, [state] says
 * [State.MISSING] and every line is dropped: the words are on screen anyway, and the settings row
 * says so once.
 */
class Voice(context: Context) {
    enum class State { IDLE, CHECKING, READY, MISSING }

    private val app = context.applicationContext
    private val language = context.getString(R.string.voice_language)
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private var tts: TextToSpeech? = null
    private var waiting: Waiting? = null
    private var next = 0

    private class Waiting(val text: String, val volume: Float, val at: Long)

    /** Starts the engine, once; cheap to call again. The first line may wait for it a moment. */
    fun prepare() {
        if (_state.value != State.IDLE) return
        _state.value = State.CHECKING
        tts = runCatching { TextToSpeech(app) { status -> ready(status) } }.getOrNull()
        if (tts == null) _state.value = State.MISSING
    }

    private fun ready(status: Int) {
        val engine = tts
        if (engine == null || status != TextToSpeech.SUCCESS) return missing()
        val locale = if (language == "fr") Locale.FRENCH else Locale.ENGLISH
        val set = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) return missing()
        runCatching {
            engine.setSpeechRate(SPEECH_RATE)
            engine.setPitch(1f)
            engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        }
        _state.value = State.READY
        // A line asked for while the engine woke is said if it is still timely, else dropped.
        waiting?.takeIf { SystemClock.elapsedRealtime() - it.at < LATE_MS }?.let { speak(it.text, it.volume, flush = true) }
        waiting = null
    }

    private fun missing() {
        _state.value = State.MISSING
        waiting = null
        runCatching { tts?.shutdown() }
        tts = null
    }

    /**
     * Says [text] at [volume] (0 to 1, of the media volume; never above the session's level).
     * [flush] cuts whatever is still being said, so a breathing cue lands on its beat; meditation
     * lines queue instead, and are spaced so they never need to.
     */
    fun say(text: String, volume: Float, flush: Boolean) {
        when (_state.value) {
            State.READY -> speak(text, volume, flush)
            State.CHECKING -> waiting = Waiting(text, volume, SystemClock.elapsedRealtime())
            else -> Unit
        }
    }

    private fun speak(text: String, volume: Float, flush: Boolean) {
        if (volume <= 0f) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        runCatching { tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "brume-${next++}") }
    }

    /** Stops speaking at once: the session ended. The engine stays ready for the next one. */
    fun hush() {
        waiting = null
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        waiting = null
        runCatching { tts?.shutdown() }
        tts = null
        _state.value = State.IDLE
    }

    private companion object {
        /** Slower than the engine's 1.0: unhurried, still natural. */
        const val SPEECH_RATE = 0.85f
        const val LATE_MS = 1500L
    }
}

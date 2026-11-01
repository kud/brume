package app.beansontoast.brume.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.beansontoast.brume.core.sound.Bell
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Rings the meditation bell, synthesised in `:core` (no audio file), on a short track of its own,
 * so it sounds with or without a background sound playing and never touches the sound's levels.
 * The caller passes [Bell.gain], which never exceeds the session's level; the bell swells in.
 */
class Bells(private val rate: Int = 48_000) {
    private val ringing = CopyOnWriteArrayList<AudioTrack>()

    fun ring(gain: Float) {
        if (gain <= 0f) return
        Thread {
            val samples = Bell.render(rate, gain)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(samples.size * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@Thread
            ringing += track
            runCatching {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                Thread.sleep((Bell.SECONDS * 1000).toLong() + 200)
            }
            ringing -= track
            runCatching { track.stop() }
            track.release()
        }.apply { isDaemon = true; name = "brume-bell" }.start()
    }

    /** Silences any bell still ringing: the session ended. */
    fun stop() { ringing.forEach { runCatching { it.pause(); it.flush() } } }
}

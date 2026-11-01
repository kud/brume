package app.beansontoast.brume.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.beansontoast.brume.core.finder.Probe
import app.beansontoast.brume.core.finder.ProbeVoice

/**
 * Plays the « Mon acouphène » search on a short streaming track of its own, apart from the mixer and
 * its listening log: a search is not a listening session. The voice in `:core` holds every rule
 * (fade-in from silence, the ceiling, Too loud); this only pumps it. The thread ends once silent.
 */
class ProbePlayer(private val rate: Int = 48_000) {
    private val voice = ProbeVoice(rate)
    @Volatile private var thread: Thread? = null
    @Volatile private var cut = true

    /** What to play; null goes quiet. A level above zero after silence starts with the fade-in. */
    fun set(probe: Probe?) {
        voice.set(probe)
        if (probe == null || probe.level <= 0f) { if (probe == null) { voice.stop(); cut = true }; return }
        if (cut) { cut = false; voice.start() }
        synchronized(this) { if (thread == null) pump() }
    }

    fun tooLoud() { cut = true; voice.tooLoud() }

    fun close() { cut = true; voice.stop() }

    /** Decides to end and forgets the thread in one step, so a [set] arriving then starts a new one. */
    private fun keepGoing(): Boolean = synchronized(this) {
        if (cut && voice.silent) { thread = null; false } else true
    }

    private fun pump() {
        thread = Thread {
            val frames = 1024
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(frames * 2 * 4 * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()
            if (track == null) { synchronized(this) { thread = null }; return@Thread }
            val buffer = FloatArray(frames * 2)
            runCatching {
                track.play()
                while (keepGoing()) {
                    voice.render(buffer, frames)
                    track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                }
            }
            runCatching { track.stop() }
            track.release()
            synchronized(this) { if (thread === Thread.currentThread()) thread = null }
        }.apply { isDaemon = true; name = "brume-probe" }.also { it.start() }
    }
}

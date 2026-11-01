package app.beansontoast.brume.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.RawRes
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.Loop
import java.nio.ByteOrder

/**
 * Decodes a recorded ambience (Opus in Ogg, res/raw, built by tools/sounds/build_loops.py) to mono
 * 16-bit PCM, once, so the engine can loop it sample by sample: MediaPlayer's own looping leaves a gap.
 * About 7 MB a layer for a 75 s loop at 48 kHz.
 */
object Loops {
    @RawRes fun Ambience.raw(): Int = when (this) {
        Ambience.RAIN -> R.raw.sound_rain
        Ambience.RAIN_TERRACE -> R.raw.sound_rain_terrace
        Ambience.STORM -> R.raw.sound_storm
        Ambience.WIND -> R.raw.sound_wind
        Ambience.SEA -> R.raw.sound_sea
        Ambience.STREAM -> R.raw.sound_stream
        Ambience.BIRDS -> R.raw.sound_birds
        Ambience.NIGHT -> R.raw.sound_night
        Ambience.FIRE -> R.raw.sound_fire
        Ambience.CAFE -> R.raw.sound_cafe
        Ambience.TRAIN -> R.raw.sound_train
    }

    fun decode(context: Context, ambience: Ambience): Loop {
        val extractor = MediaExtractor()
        context.resources.openRawResourceFd(ambience.raw()).use { fd -> extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        try {
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            var out = ShortArray(((durationUs * rate / 1_000_000L) + rate).toInt().coerceAtLeast(rate))
            var size = 0
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        val shorts = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = (info.size / 2) / channels
                        if (size + frames > out.size) out = out.copyOf(maxOf(out.size * 2, size + frames))
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get(f * channels + c)
                            out[size++] = (sum / channels).toShort()
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally { codec.stop(); codec.release() }
            // The loop is exactly as long as the file says: any encoder padding past the end would be a seam.
            val exact = (durationUs * rate / 1_000_000L).toInt()
            if (exact in 1 until size) size = exact
            return Loop(out.copyOf(size), rate)
        } finally { extractor.release() }
    }
}

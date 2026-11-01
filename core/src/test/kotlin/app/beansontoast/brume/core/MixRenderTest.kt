package app.beansontoast.brume.core

import app.beansontoast.brume.core.sound.Loop
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.SoundEngine.Command
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders 20 s of brown + rain + café through the engine, offline, for listening: runs only with
 * BRUME_MIX_DIR=<dir> holding rain.s16 and cafe.s16 (mono 48 kHz, decoded from res/raw); writes mix.f32 (stereo).
 */
class MixRenderTest {
    @Test fun render() {
        val dir = System.getenv("BRUME_MIX_DIR")?.let(::File)
        assumeTrue(dir != null)
        fun loop(name: String): Loop {
            val b = ByteBuffer.wrap(File(dir, "$name.s16").readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return Loop(ShortArray(b.remaining()).also { b.get(it) }, RATE)
        }
        val e = SoundEngine(RATE, 7).apply {
            post(Command.Add(BROWN, 0.45f, tone = -0.3f)); post(Command.Add(RAIN, 0.6f, loop = loop("rain")))
            post(Command.Add(app.beansontoast.brume.core.sound.Sound.Recorded(app.beansontoast.brume.core.sound.Ambience.CAFE), 0.4f, loop = loop("cafe")))
            post(Command.Start)
        }
        val block = FloatArray(960)
        val out = ByteBuffer.allocate(20 * RATE * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(20 * RATE / 480) { e.render(block, 480); block.forEach { out.putFloat(it) } }
        File(dir!!, "mix.f32").writeBytes(out.array())
    }
}

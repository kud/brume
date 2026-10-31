package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.playback.Status
import app.beansontoast.brume.ui.AboutScreen
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.ListenComfort
import app.beansontoast.brume.ui.TonesScreen
import app.beansontoast.brume.ui.Tab
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Local design renders, invented data only (pastille's RedesignRenders). Run only with
 * BRUME_RENDERS=<dir>: `BRUME_RENDERS=/tmp/shots ./gradlew --offline :app:testDebugUnitTest --tests '*Renders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class Renders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val today = LocalDate.of(2026, 1, 20)
    private val invented = listOf(
        CheckIn("2026-01-20", 5, 3, "Calm evening, rain at low level."),
        CheckIn("2026-01-19", 6, 5),
        CheckIn("2026-01-18", 4, 2, "Quiet day."),
        CheckIn("2026-01-17", 7, 6, "Loud restaurant at lunch."),
    )

    private val idle = PlayerUi(NoiseColour.PINK, Ambience.RAIN, 0.3f, 0.45f)
    private val playing = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.35f, 0.25f, playing = true, timerMinutes = 45, timerRemainingMinutes = 38)

    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f)

    @Test fun render() = when (shot) {
        "listen-idle-night" -> shoot(dark = true, night = true, Tab.LISTEN) { ListenComfort(idle, ListenActions()) }
        "listen-playing-night" -> shoot(dark = true, night = true, Tab.LISTEN) { ListenComfort(playing, ListenActions()) }
        "listen-idle-day" -> shoot(dark = false, night = false, Tab.LISTEN) { ListenComfort(idle, ListenActions()) }
        "listen-playing-day" -> shoot(dark = false, night = false, Tab.LISTEN) { ListenComfort(playing, ListenActions()) }
        "listen-empty-dark" -> shoot(dark = true, night = false, Tab.LISTEN) { ListenComfort(PlayerUi(noise = null, ambience = null), ListenActions()) }
        "listen-stopped-dark" -> shoot(dark = true, night = false, Tab.LISTEN) { ListenComfort(idle.copy(status = Status.TOO_LOUD), ListenActions()) }
        "listen-tones-night" -> shoot(dark = true, night = true, Tab.TONES) { TonesScreen(tones.copy(playing = true), ListenActions()) }
        "listen-tones-day" -> shoot(dark = false, night = false, Tab.TONES) { TonesScreen(tones, ListenActions()) }
        "journal-day" -> shoot(dark = false, night = false, Tab.JOURNAL) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
        "journal-night" -> shoot(dark = true, night = true, Tab.JOURNAL) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
        "journal-empty-dark" -> shoot(dark = true, night = false, Tab.JOURNAL) { JournalScreen(emptyList(), 0, today, null, { _, _, _ -> }, {}) }
        "about-night" -> shoot(dark = true, night = true, Tab.ABOUT) { AboutScreen(true, {}, "0.1.0") }
        "listen-playing-open-day" -> shoot(dark = false, night = false, Tab.LISTEN) { ListenComfort(playing, ListenActions()) }
        "about-day" -> shoot(dark = false, night = false, Tab.ABOUT) { AboutScreen(true, {}, "0.1.0") }
        else -> error(shot)
    }

    private fun shoot(dark: Boolean, night: Boolean, tab: Tab, content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = dark, night = night) { BrumeScaffold(tab, {}) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "brume-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "listen-idle-night", "listen-playing-night", "listen-idle-day", "listen-playing-day", "listen-empty-dark", "listen-stopped-dark", "listen-tones-night", "listen-tones-day",
            "journal-day", "journal-night", "journal-empty-dark", "about-night", "about-day", "listen-playing-open-day",
        ).map { arrayOf<Any>(it) }
    }
}

/** A plain PNG writer, not Bitmap.compress, to keep one native path out of a JVM that aborts easily. */
internal fun writePng(bitmap: Bitmap, file: File) {
    val w = bitmap.width; val h = bitmap.height
    val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    val raw = ByteArrayOutputStream()
    DeflaterOutputStream(raw).use { d ->
        val row = ByteArray(1 + w * 4)
        for (y in 0 until h) {
            row[0] = 0
            for (x in 0 until w) {
                val a = px[y * w + x]; val i = 1 + x * 4
                row[i] = (a shr 16).toByte(); row[i + 1] = (a shr 8).toByte(); row[i + 2] = a.toByte(); row[i + 3] = (a ushr 24).toByte()
            }
            d.write(row)
        }
    }
    file.parentFile?.mkdirs()
    DataOutputStream(file.outputStream().buffered()).use { o ->
        o.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val hd = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) } }
        fun chunk(t: String, data: ByteArray) {
            val tb = t.toByteArray(); o.writeInt(data.size); o.write(tb); o.write(data)
            o.writeInt(CRC32().apply { update(tb); update(data) }.value.toInt())
        }
        chunk("IHDR", hd.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
    }
}

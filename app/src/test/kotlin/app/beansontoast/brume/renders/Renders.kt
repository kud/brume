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
import app.beansontoast.brume.playback.Output
import app.beansontoast.brume.playback.OutputKind
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.playback.Status
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.sync.SyncProblem
import app.beansontoast.brume.sync.SyncState
import app.beansontoast.brume.ui.SyncActions
import app.beansontoast.brume.ui.SyncSettings
import app.beansontoast.brume.ui.AboutScreen
import app.beansontoast.brume.ui.LanguageChoice
import app.beansontoast.brume.ui.LanguageSettings
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.ListenComfort
import app.beansontoast.brume.ui.TonesScreen
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.CalmScreen
import app.beansontoast.brume.ui.Meditation
import app.beansontoast.brume.ui.MeditationSession
import app.beansontoast.brume.ui.MeditationSetup
import app.beansontoast.brume.ui.Practice
import app.beansontoast.brume.ui.BreathSession
import app.beansontoast.brume.ui.BreathSetup
import app.beansontoast.brume.ui.ModuleSettings
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
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
        "balance-listen-night" -> shoot(dark = true, night = true, Tab.LISTEN) { ListenComfort(playing.copy(balance = -0.3f, output = Output(OutputKind.BLUETOOTH, "WH-1000XM4")), ListenActions()) }
        "balance-listen-day" -> shoot(dark = false, night = false, Tab.LISTEN) { ListenComfort(idle.copy(balance = 1f, output = Output(OutputKind.WIRED, null)), ListenActions()) }
        "balance-tones-night" -> shoot(dark = true, night = true, Tab.TONES) { TonesScreen(tones.copy(playing = true, balance = 0.6f, output = Output(OutputKind.BLUETOOTH, "Sleep Buds")), ListenActions()) }
        "balance-tones-day" -> shoot(dark = false, night = false, Tab.TONES) { TonesScreen(tones.copy(output = Output(OutputKind.SPEAKER, null)), ListenActions()) }
        "journal-day" -> shoot(dark = false, night = false, Tab.JOURNAL) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
        "journal-night" -> shoot(dark = true, night = true, Tab.JOURNAL) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
        "journal-empty-dark" -> shoot(dark = true, night = false, Tab.JOURNAL) { JournalScreen(emptyList(), 0, today, null, { _, _, _ -> }, {}) }
        "about-night" -> shoot(dark = true, night = true, Tab.ABOUT) { AboutScreen(true, {}, "0.1.0", language = LanguageChoice("") {}) }
        "listen-playing-open-day" -> shoot(dark = false, night = false, Tab.LISTEN) { ListenComfort(playing, ListenActions()) }
        "about-day" -> shoot(dark = false, night = false, Tab.ABOUT) { AboutScreen(true, {}, "0.1.0", language = LanguageChoice("fr") {}) }
        "about-language-night" -> shoot(dark = true, night = true, Tab.ABOUT) { Column(Modifier.padding(horizontal = 20.dp)) { LanguageSettings(LanguageChoice("en") {}) } }
        "about-language-day" -> shoot(dark = false, night = false, Tab.ABOUT) { Column(Modifier.padding(horizontal = 20.dp)) { LanguageSettings(LanguageChoice("") {}) } }
        "balance-centre-night" -> shoot(dark = true, night = true, Tab.LISTEN) { ListenComfort(playing.copy(balance = 0f, output = Output(OutputKind.WIRED, null)), ListenActions()) }
        "sync-night" -> shoot(dark = true, night = true, Tab.ABOUT) { syncShot() }
        "sync-day" -> shoot(dark = false, night = false, Tab.ABOUT) { syncShot() }
        "sync-off-day" -> shoot(dark = false, night = false, Tab.ABOUT) { Column(Modifier.padding(horizontal = 20.dp)) { SyncSettings(SyncState(), null, SyncActions()) } }
        "sync-problem-night" -> shoot(dark = true, night = true, Tab.ABOUT) {
            Column(Modifier.padding(horizontal = 20.dp)) { SyncSettings(SyncState("Brume", true, true, null, SyncProblem.UNREACHABLE), null, SyncActions()) }
        }
        "screens-listen" -> shoot(dark = true, night = true, Tab.LISTEN) { ListenComfort(idle, ListenActions()) }
        "screens-breath" -> shoot(dark = true, night = true, Tab.CALM) { calm(BreathSetup(), soundName = "Pink noise") }
        "screens-tones" -> shoot(dark = true, night = true, Tab.TONES) { TonesScreen(tones, ListenActions()) }
        "screens-journal" -> shoot(dark = true, night = true, Tab.JOURNAL) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
        "screens-about" -> shoot(dark = true, night = true, Tab.ABOUT) { AboutScreen(true, {}, "0.1.0", language = LanguageChoice("") {}) }
        "modules-night" -> shoot(dark = true, night = true, Tab.ABOUT) { Column(Modifier.padding(horizontal = 20.dp)) { ModuleSettings(Modules(), {}) } }
        "modules-day" -> shoot(dark = false, night = false, Tab.ABOUT, Modules(tones = false)) { Column(Modifier.padding(horizontal = 20.dp)) { ModuleSettings(Modules(tones = false), {}) } }
        "breath-setup-night" -> shoot(dark = true, night = true, Tab.CALM) { calm(BreathSetup(), soundName = "Pink noise") }
        "breath-setup-day" -> shoot(dark = false, night = false, Tab.CALM) { calm(BreathSetup(Pattern.FOUR_SEVEN_EIGHT, 2, sound = true), soundName = "Rain") }
        "breath-setup-nosound-day" -> shoot(dark = false, night = false, Tab.CALM, Modules(tones = false, meditation = false)) { calm(BreathSetup(Pattern.EVEN, 10), Modules(tones = false, meditation = false)) }
        "breath-in-night" -> bare(dark = true, night = true) { session(Pattern.LONG_OUT, 5, 2.6) }
        "breath-hold-night" -> bare(dark = true, night = true) { session(Pattern.FOUR_SEVEN_EIGHT, 5, 7.0) }
        "breath-out-night" -> bare(dark = true, night = true) { session(Pattern.LONG_OUT, 5, 7.5) }
        "breath-in-day" -> bare(dark = false, night = false) { session(Pattern.EVEN, 2, 3.4, night = false) }
        "breath-out-day" -> bare(dark = false, night = false) { session(Pattern.EVEN, 2, 61.0 + 5.0 + 2.5, night = false) }
        "breath-still-night" -> bare(dark = true, night = true) { session(Pattern.LONG_OUT, 5, 2.6, still = true) }
        "breath-done-night" -> bare(dark = true, night = true) { session(Pattern.LONG_OUT, 2, 120.0) }
        "calm-meditate-night" -> shoot(dark = true, night = true, Tab.CALM) { calm(practice = Practice.MEDITATE, soundName = "Pink noise") }
        "calm-meditate-day" -> shoot(dark = false, night = false, Tab.CALM) { calm(practice = Practice.MEDITATE, med = MeditationSetup(Meditation.TIMER, 15, sound = true), soundName = "Rain") }
        "calm-only-meditate-day" -> shoot(dark = false, night = false, Tab.CALM, Modules(breath = false)) { calm(modules = Modules(breath = false), med = MeditationSetup(Meditation.BODY_SCAN), soundName = null) }
        "med-sounds-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.SOUNDS), 80.0) }
        "med-sounds-day" -> bare(dark = false, night = false) { med(MeditationSetup(Meditation.SOUNDS), 80.0, night = false) }
        "med-body-first-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.BODY_SCAN), 12.0) }
        "med-breath-still-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.BREATH), 140.0, still = true) }
        "med-timer-settle-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.TIMER, 10), 12.0) }
        "med-timer-middle-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.TIMER, 10), 260.0) }
        "med-timer-return-day" -> bare(dark = false, night = false) { med(MeditationSetup(Meditation.TIMER, 10), 590.0, night = false) }
        "med-done-night" -> bare(dark = true, night = true) { med(MeditationSetup(Meditation.SOUNDS), 300.0) }
        "modules-calm-night" -> shoot(dark = true, night = true, Tab.ABOUT) { Column(Modifier.padding(horizontal = 20.dp)) { ModuleSettings(Modules(), {}) } }
        "modules-calm-day" -> shoot(dark = false, night = false, Tab.ABOUT, Modules(breath = false)) { Column(Modifier.padding(horizontal = 20.dp)) { ModuleSettings(Modules(breath = false), {}) } }
        else -> if (shot.startsWith("breath-frame-")) bare(dark = true, night = true) {
            session(Pattern.LONG_OUT, 2, shot.removePrefix("breath-frame-").toInt() * 0.5)
        } else error(shot)
    }

    @Composable private fun syncShot() = Column(Modifier.padding(horizontal = 20.dp)) {
        SyncSettings(SyncState("Brume", hasFolder = true, auto = true, lastSyncedAt = 1_768_941_600_000), "Imported. Days added or updated: 4. Listening sessions added: 9.", SyncActions(), java.time.ZoneOffset.UTC)
    }

    @Composable private fun calm(
        breath: BreathSetup = BreathSetup(), modules: Modules = Modules(), practice: Practice = Practice.BREATHE,
        med: MeditationSetup = MeditationSetup(), soundName: String? = "Pink noise",
    ) = CalmScreen(modules, practice, {}, breath, med, soundName, {}, {}, {}, {})

    @Composable private fun med(setup: MeditationSetup, at: Double, still: Boolean = false, night: Boolean = true) =
        MeditationSession(setup.copy(haptics = false), 0L, night, onEnd = {}, elapsed = at, still = still)

    @Composable private fun session(pattern: Pattern, minutes: Int, at: Double, still: Boolean = false, night: Boolean = true) =
        BreathSession(BreathPlan(pattern, minutes), 0L, night, haptics = false, onEnd = {}, elapsed = at, still = still)

    private fun bare(dark: Boolean, night: Boolean, content: @Composable () -> Unit) = capture { BrumeTheme(dark = dark, night = night) { content() } }

    private fun shoot(dark: Boolean, night: Boolean, tab: Tab, modules: Modules = Modules(), content: @Composable () -> Unit) =
        capture { BrumeTheme(dark = dark, night = night) { BrumeScaffold(tab, {}, modules) { content() } } }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
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
            "sync-night", "sync-day", "sync-off-day", "sync-problem-night",
            "balance-listen-night", "balance-listen-day", "balance-tones-night", "balance-tones-day",
            "about-language-night", "about-language-day", "balance-centre-night",
            "screens-listen", "screens-breath", "screens-tones", "screens-journal", "screens-about",
            "modules-night", "modules-day", "breath-setup-night", "breath-setup-day", "breath-setup-nosound-day",
            "breath-in-night", "breath-hold-night", "breath-out-night", "breath-in-day", "breath-out-day", "breath-still-night", "breath-done-night",
            "calm-meditate-night", "calm-meditate-day", "calm-only-meditate-day", "med-sounds-night", "med-sounds-day", "med-body-first-night",
            "med-breath-still-night", "med-timer-settle-night", "med-timer-middle-night", "med-timer-return-day", "med-done-night",
            "modules-calm-night", "modules-calm-day",
        ).plus((0 until 20).map { "breath-frame-%02d".format(it) }).map { arrayOf<Any>(it) }
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

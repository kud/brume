package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.AboutScreen
import app.beansontoast.brume.ui.BreathSetup
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.CalmScreen
import app.beansontoast.brume.ui.LanguageChoice
import app.beansontoast.brume.ui.LearnScreen
import app.beansontoast.brume.ui.TonesHowScreen
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.LearnCardScreen
import app.beansontoast.brume.ui.LearnTopicScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.ListenComfort
import app.beansontoast.brume.ui.MeditationSetup
import app.beansontoast.brume.ui.Practice
import app.beansontoast.brume.ui.SettingsScreen
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.TonesScreen
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Direct tabs (Erwann, 2026-10-31), in French at 360x780, night and day, on invented data.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*DirectRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class DirectRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val today = LocalDate.of(2026, 1, 20)
    private val week = listOf(
        CheckIn("2026-01-19", 6, 5, "Repas bruyant à midi.\n#caffeine #stress"),
        CheckIn("2026-01-18", 4, 2, "Journée calme."),
        CheckIn("2026-01-17", 7, 6),
    )
    private val noted = listOf(CheckIn("2026-01-20", 5, 3, "Soirée calme, pluie basse.\n#poor-sleep")) + week
    private val idle = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.45f)
    private val playing = idle.copy(playing = true, timerMinutes = 45, timerRemainingMinutes = 38)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f)

    @Test fun render() {
        val night = shot.endsWith("-night")
        val dark = night || shot.endsWith("-dark")
        val name = shot.removeSuffix("-night").removeSuffix("-day")
        when (name) {
            "listen" -> tab(dark, night, Tab.LISTEN) { ListenComfort(idle, ListenActions()) }
            "listen-playing" -> tab(dark, night, Tab.LISTEN) { ListenComfort(playing, ListenActions()) }
            "calm" -> tab(dark, night, Tab.CALM) { CalmScreen(Modules(), Practice.BREATHE, {}, BreathSetup(), MeditationSetup(), "Bruit brun", {}, {}, {}, {}) }
            "tones" -> tab(dark, night, Tab.TONES) { TonesScreen(tones, ListenActions()) }
            "tones-how" -> tab(dark, night, Tab.TONES) { TonesHowScreen {} }
            "journal" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(week, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
            "journal-saved" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(noted, 4 * 3600 + 25 * 60, today, "Enregistré", { _, _, _ -> }, {}) }
            "learn" -> tab(dark, night, Tab.LEARN) { LearnScreen() }
            "learn-library" -> tab(dark, night, Tab.LEARN) { LearnScreen(startPage = 1) }
            "learn-loudness" -> tab(dark, night, Tab.LEARN) { LearnScreen(startPage = 2, still = true) }
            "learn-doctor-sheet" -> tab(dark, night, Tab.LEARN) { LearnScreen(doctorOpen = true) }
            "learn-topic" -> tab(dark, night, Tab.LEARN) { LearnTopicScreen(3, {}, {}) }
            "card-coffee" -> tab(dark, night, Tab.LEARN) { LearnCardScreen(Learn.card("c3_1")!!, "Retour", {}, onNext = {}) }
            "settings" -> tab(dark, night, Tab.LEARN) { SettingsScreen(true, {}, {}, language = LanguageChoice("fr") {}) }
            "about" -> tab(dark, night, Tab.LEARN) { AboutScreen("0.2.0") }
            else -> error(shot)
        }
    }

    private fun tab(dark: Boolean, night: Boolean, tab: Tab, content: @Composable () -> Unit) = capture {
        BrumeTheme(dark = dark, night = night) { BrumeScaffold(tab, {}, Modules()) { content() } }
    }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "direct-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "listen", "listen-playing", "calm", "tones", "tones-how", "journal", "journal-saved",
            "learn", "learn-library", "learn-loudness", "learn-doctor-sheet", "learn-topic", "card-coffee", "settings", "about",
        ).flatMap { listOf("$it-night", "$it-day") }.map { arrayOf<Any>(it) }
    }
}

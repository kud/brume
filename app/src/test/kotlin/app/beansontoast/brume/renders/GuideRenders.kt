package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.home.LastSound
import app.beansontoast.brume.core.home.Suggest
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
import app.beansontoast.brume.ui.HomeScreen
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
 * The guide (direction B, built for real), in French at 360x780, night and day, on invented data.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*GuideRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class GuideRenders(private val shot: String) {
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
            "home" -> tab(dark, night, Tab.HOME) { home(night, idle) }
            "home-first" -> tab(dark, night, Tab.HOME) { home(night, PlayerUi(noise = null, ambience = null)) }
            "home-playing" -> tab(dark, night, Tab.HOME) { home(night, playing) }
            "controls" -> tab(dark, night, Tab.HOME) { ListenComfort(idle, ListenActions(), onBack = {}) }
            "calm" -> tab(dark, night, Tab.CALM) { CalmScreen(Modules(), Practice.BREATHE, {}, BreathSetup(), MeditationSetup(), "Bruit brun", {}, {}, {}, {}) }
            "tones-1", "tones-2", "tones-3", "tones-4" ->
                tab(dark, night, Tab.TONES) { TonesScreen(tones, ListenActions(), step = name.last().digitToInt(), night = night) }
            "journal-q1" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(week, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
            "journal-q3" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(week, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}, startQuestion = 3) }
            "journal-done" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(noted, 4 * 3600 + 25 * 60, today, "Enregistré", { _, _, _ -> }, {}) }
            "about" -> tab(dark, night, Tab.ABOUT) { AboutScreen("0.2.0") }
            "settings" -> tab(dark, night, Tab.ABOUT) { SettingsScreen(true, {}, {}) }
            "learn-topic" -> tab(dark, night, Tab.ABOUT) { LearnTopicScreen(3, {}, {}) }
            "card-coffee" -> tab(dark, night, Tab.HOME) { LearnCardScreen(Learn.card("c3_1")!!, "Retour", {}, step = 6) }
            "card-time" -> tab(dark, night, Tab.ABOUT) { LearnCardScreen(Learn.card("c5_3")!!, "Retour", {}, onNext = {}) }
            "card-sound" -> tab(dark, night, Tab.ABOUT) { LearnCardScreen(Learn.card("c2_3")!!, "Retour", {}, onNext = {}) }
            "card-heavy" -> tab(dark, night, Tab.ABOUT) { LearnCardScreen(Learn.card("c6_3")!!, "Retour", {}, onNext = {}) }
            else -> error(shot)
        }
    }

    @Composable private fun home(night: Boolean, ui: PlayerUi) = HomeScreen(
        night = night, ui = ui, suggestion = Suggest.of(night, LastSound(ui.noise, ui.ambience, ui.timerMinutes)), step = 6,
        actions = ListenActions(), onPlay = {}, onOtherSound = {}, onCard = { _, _ -> }, tonesStep = 2,
    )

    private fun tab(dark: Boolean, night: Boolean, tab: Tab, content: @Composable () -> Unit) = capture {
        BrumeTheme(dark = dark, night = night) { BrumeScaffold(tab, {}, Modules(), night = night) { content() } }
    }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "guide-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "home", "home-first", "home-playing", "controls", "calm", "tones-1", "tones-2", "tones-3", "tones-4",
            "journal-q1", "journal-q3", "journal-done", "about", "settings", "learn-topic",
            "card-coffee", "card-time", "card-sound", "card-heavy",
        ).flatMap { listOf("$it-night", "$it-day") }.map { arrayOf<Any>(it) }
    }
}

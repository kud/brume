package app.beansontoast.brume.renders

import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.ui.Meditation
import app.beansontoast.brume.ui.MeditationSession
import app.beansontoast.brume.ui.BreathSession
import app.beansontoast.brume.ui.MiniPlayer
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.AboutScreen
import app.beansontoast.brume.ui.BreathSetup
import app.beansontoast.brume.ui.BreatheScene
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.CalmScreen
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.LanguageChoice
import app.beansontoast.brume.ui.LearnScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.MeditationSetup
import app.beansontoast.brume.ui.Practice
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.SceneScreen
import app.beansontoast.brume.ui.SettingsScreen
import app.beansontoast.brume.ui.SoundsScreen
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.TonesScreen
import app.beansontoast.brume.ui.scenes
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
 * The scene (Erwann chose « A avec les scènes de C », 2026-10-31): every screen in French at
 * 360x780, night and day, on invented data, plus a swipe from Dormir to Se poser and a volume slide.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*SceneRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class SceneRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val today = LocalDate.of(2026, 1, 20)
    private val week = listOf(
        CheckIn("2026-01-19", 6, 5, "Repas bruyant à midi.\n#caffeine #stress"),
        CheckIn("2026-01-18", 4, 2, "Journée calme."),
        CheckIn("2026-01-17", 7, 6),
    )
    private val idle = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.45f, timerMinutes = 45)
    private val playing = idle.copy(playing = true, timerRemainingMinutes = 38)
    private val louder = playing.copy(noiseLevel = 0.6f, ambienceLevel = 0.75f)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f)
    private val scenes = Modules().scenes()
    private val breathe = BreatheScene(minutes = 5, soundName = "Bruit brun")

    @Test fun render() {
        val night = shot.endsWith("-night")
        val dark = night || shot.endsWith("-dark")
        val name = shot.removeSuffix("-night").removeSuffix("-day")
        when {
            name == "sleep-playing" -> scene(dark, night, Scene.SLEEP, playing)
            name == "settle" -> scene(dark, night, Scene.SETTLE, idle)
            name == "settle-empty" -> scene(dark, night, Scene.SETTLE, PlayerUi(noise = null))
            name == "breathe" -> scene(dark, night, Scene.BREATHE, idle)
            name == "tones" -> scene(dark, night, Scene.TONES, tones)
            name == "volume" -> scene(dark, night, Scene.SLEEP, louder)
            name.startsWith("swipe") -> {
                // Frames from Dormir to Se poser (a negative offset draws wrongly under Robolectric, so the last is the settled page): the pager's own offset, as a finger would leave it.
                val f = name.removePrefix("swipe").toInt()
                val fraction = listOf(0f, 0.15f, 0.3f, 0.45f, 0f, 0f, 0f)[f]
                scene(dark, night, if (f < 4) Scene.SLEEP else Scene.SETTLE, playing, fraction)
            }
            name == "sounds" -> tab(dark, night, Tab.LISTEN) { SoundsScreen(playing, ListenActions(), onBack = {}, hint = true) }
            name == "tones-setup" -> tab(dark, night, Tab.LISTEN) { TonesScreen(tones, ListenActions()) }
            name == "breathe-setup" -> tab(dark, night, Tab.LISTEN) { CalmScreen(Modules(), Practice.BREATHE, {}, BreathSetup(), MeditationSetup(), "Bruit brun", {}, {}, {}, {}) }
            name.startsWith("breathe-") && name.removePrefix("breathe-").uppercase() in Pattern.entries.map { it.name } -> {
                val p = Pattern.valueOf(name.removePrefix("breathe-").uppercase())
                tab(dark, night, Tab.LISTEN) { SceneScreen(scenes, Scene.BREATHE, {}, idle, ListenActions(), breathe = BreatheScene(minutes = 5, cycles = p.cycles, pattern = p, soundName = "Brun + Pluie"), night = night) }
            }
            name == "meditate-setup" -> tab(dark, night, Tab.LISTEN) { CalmScreen(Modules(), Practice.MEDITATE, {}, BreathSetup(), MeditationSetup(Meditation.SOUNDS, 10), "Brun + Pluie", {}, {}, {}, {}) }
            name == "timer-setup" -> tab(dark, night, Tab.LISTEN) { CalmScreen(Modules(), Practice.MEDITATE, {}, BreathSetup(), MeditationSetup(Meditation.TIMER, 15, sound = true), "Brun + Pluie", {}, {}, {}, {}) }
            name == "meditate-start" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.SOUNDS, 5), 0L, night, {}, elapsed = 2.0) }
            name == "meditate-dimmed" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.SOUNDS, 5), 0L, night, {}, elapsed = 140.0) }
            name == "timer-dimmed" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.TIMER, 10), 0L, night, {}, elapsed = 260.0) }
            name == "sigh-top" -> bare(dark, night) { BreathSession(BreathPlan(Pattern.SIGH, 2), 0L, night, haptics = false, onEnd = {}, elapsed = 2.5) }
            name == "mini-journal" -> tab(dark, night, Tab.JOURNAL, mini = true) { JournalScreen(week, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
            name == "mini-learn" -> tab(dark, night, Tab.LEARN, mini = true) { LearnScreen() }
            name == "journal" -> tab(dark, night, Tab.JOURNAL) { JournalScreen(week, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
            name == "learn" -> tab(dark, night, Tab.LEARN) { LearnScreen() }
            name == "settings" -> tab(dark, night, Tab.LEARN) { SettingsScreen(true, {}, {}, language = LanguageChoice("fr") {}) }
            name == "about" -> tab(dark, night, Tab.LEARN) { AboutScreen("0.2.0") }
            else -> error(shot)
        }
    }

    private fun scene(dark: Boolean, night: Boolean, scene: Scene, ui: PlayerUi, peek: Float = 0f) = tab(dark, night, Tab.LISTEN) {
        SceneScreen(scenes, scene, {}, ui, ListenActions(), breathe = breathe, night = night, peek = peek)
    }

    private fun tab(dark: Boolean, night: Boolean, tab: Tab, mini: Boolean = false, content: @Composable () -> Unit) = capture {
        BrumeTheme(dark = dark, night = night) {
            BrumeScaffold(tab, {}, Modules(), mini = if (mini) ({ MiniPlayer("Brun + Pluie") {} }) else null) { content() }
        }
    }

    private fun bare(dark: Boolean, night: Boolean, content: @Composable () -> Unit) = capture { BrumeTheme(dark = dark, night = night) { content() } }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "scene-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = (listOf(
            "sleep-playing", "settle", "settle-empty", "breathe", "tones", "volume",
            "sounds", "tones-setup", "breathe-setup", "journal", "learn", "settings", "about",
            "breathe-long_out", "breathe-sigh", "breathe-box", "breathe-four_seven_eight", "sigh-top",
            "meditate-setup", "meditate-start", "meditate-dimmed", "timer-setup", "timer-dimmed", "mini-journal", "mini-learn",
        ).flatMap { listOf("$it-night", "$it-day") } + listOf(0, 1, 2, 3, 6).map { "swipe$it-night" }).map { arrayOf<Any>(it) }
    }
}

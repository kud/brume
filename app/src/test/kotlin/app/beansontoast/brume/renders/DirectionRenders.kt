package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BreatheScreen
import app.beansontoast.brume.ui.BreathSession
import app.beansontoast.brume.ui.BreathSetup
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.FnFrame
import app.beansontoast.brume.ui.HomeScreen
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.Land
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.MiniPlayer
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.SoloScene
import app.beansontoast.brume.ui.fns
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
 * The misty-landscape direction, phase 1 (Iris, 2026-11-01): home, Respirer's orb, Se poser and a ✕
 * screen, in French at 360x780, on invented data.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*DirectionRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class DirectionRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val mix = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.45f)
    private val box = BreathSetup(technique = Technique.BOX)

    @Test fun render() {
        val night = shot.endsWith("-night")
        when (shot.removeSuffix("-night").removeSuffix("-day")) {
            "home" -> capture(night) { HomeScreen(Modules().fns(), onOpen = {}) }
            "home-mini" -> capture(night) { HomeScreen(Modules().fns(), onOpen = {}, mini = { MiniPlayer("Bruit brun et pluie", onPause = {}) }) }
            "home-modules-off" -> capture(night) { HomeScreen(Modules(tones = false, meditation = false).fns(), onOpen = {}) }
            "breathe" -> capture(night) { FnFrame(onClose = {}, title = "Respirer") { BreatheScreen(box, {}, {}, {}, previewAt = 1.5) } }
            "breath-in" -> capture(night) { BreathSession(box.plan(), 0L, night, haptics = false, onEnd = {}, elapsed = 2.0, still = false) }
            "breath-hold" -> capture(night) { BreathSession(box.plan(), 0L, night, haptics = false, onEnd = {}, elapsed = 5.0, still = false) }
            "breath-out" -> capture(night) { BreathSession(box.plan(), 0L, night, haptics = false, onEnd = {}, elapsed = 8.0, still = false) }
            "settle" -> capture(night) { FnFrame(onClose = {}, title = "Se poser") { SoloScene(Scene.SETTLE, mix, ListenActions()) } }
            "settle-playing" -> capture(night) { FnFrame(onClose = {}, title = "Se poser") { SoloScene(Scene.SETTLE, mix.copy(playing = true), ListenActions()) } }
            "journal" -> capture(night) {
                FnFrame(onClose = {}, title = "Journal", land = Land.SKY) {
                    JournalScreen(listOf(CheckIn("2026-01-19", 6, 5, "Repas bruyant à midi."), CheckIn("2026-01-18", 4, 2)), 3 * 3600, LocalDate.of(2026, 1, 20), null, { _, _, _ -> }, {})
                }
            }
            // Phase 2: the screens inside the frame, which owns the title.
            "mixer" -> capture(night) { FnFrame(null, "Sons", onBack = {}) { app.beansontoast.brume.ui.SoundsScreen(mix.copy(playing = true), ListenActions(), onBack = {}) } }
            "mixer-add" -> capture(night) { FnFrame(null, "Sons", onBack = {}) { app.beansontoast.brume.ui.SoundsScreen(mix, ListenActions(), onBack = {}, sheet = app.beansontoast.brume.ui.SoundsSheet.ADD) } }
            "learn" -> capture(night) { FnFrame(onClose = {}, title = "Comprendre", land = Land.SKY) { app.beansontoast.brume.ui.LearnScreen(still = true) } }
            "topic" -> capture(night) { FnFrame(null, "Le son", onBack = {}, land = Land.SKY) { app.beansontoast.brume.ui.LearnTopicScreen(1, {}, {}) } }
            "card" -> capture(night) { FnFrame(null, "Le son", onBack = {}, land = Land.SKY) { app.beansontoast.brume.ui.LearnCardScreen(app.beansontoast.brume.core.learn.Learn.cards.first(), backLabel = "", onBack = {}) } }
            "about" -> capture(night) { FnFrame(null, "À propos", onBack = {}, land = Land.SKY) { app.beansontoast.brume.ui.AboutScreen(version = "0.1.0") } }
            // Phase 3 (Iris): Méditer around the resting orb, its options sheet, and the session.
            "meditate" -> capture(night) {
                FnFrame(onClose = {}, title = "Méditer") { app.beansontoast.brume.ui.MeditateScreen(app.beansontoast.brume.ui.MeditationSetup(), "Bruit brun", app.beansontoast.brume.voice.Voice.State.IDLE, {}, {}, previewAt = 4.0) }
            }
            "meditate-options" -> capture(night) {
                FnFrame(onClose = {}, title = "Méditer") { app.beansontoast.brume.ui.MeditateScreen(app.beansontoast.brume.ui.MeditationSetup(), "Bruit brun", app.beansontoast.brume.voice.Voice.State.IDLE, {}, {}, previewAt = 4.0, options = true) }
            }
            "med-session" -> capture(night) { app.beansontoast.brume.ui.MeditationSession(app.beansontoast.brume.ui.MeditationSetup(), 0L, night, onEnd = {}, elapsed = 2.0, still = false) }
            "tones" -> capture(night) { FnFrame(onClose = {}, title = "Tons", land = Land.SKY) { app.beansontoast.brume.ui.TonesScreen(PlayerUi(mode = app.beansontoast.brume.core.sound.SoundEngine.Mode.TONES), ListenActions()) } }
            "settings" -> capture(night) { FnFrame(onClose = {}, title = "Réglages", land = Land.SKY) { app.beansontoast.brume.ui.SettingsScreen(true, {}, {}, language = app.beansontoast.brume.ui.LanguageChoice("fr") {}) } }
            "settings-sheet" -> capture(night) { FnFrame(onClose = {}, title = "Réglages", land = Land.SKY) { app.beansontoast.brume.ui.SettingsScreen(true, {}, {}, language = app.beansontoast.brume.ui.LanguageChoice("fr") {}, sheet = app.beansontoast.brume.ui.SettingsSheet.MODULES) } }
            "finder" -> capture(night) { FnFrame(onClose = {}, title = "Mon acouphène", land = Land.SKY) { app.beansontoast.brume.ui.FinderScreen(emptyList(), app.beansontoast.brume.ui.FinderActions(), onBack = {}) } }
            // Phase 3: a saved profile, invented; the frame's corner reads ‹ and there is no back row.
            "finder-profile" -> capture(night) {
                val p = app.beansontoast.brume.core.finder.TinnitusProfile("demo", "Sifflement",
                    character = app.beansontoast.brume.core.finder.Character.TONE,
                    matches = listOf(app.beansontoast.brume.core.finder.PitchMatch("m1", "2026-01-18", 4000, app.beansontoast.brume.core.finder.Character.TONE, false)))
                FnFrame(onClose = {}, title = "Mon acouphène", land = Land.SKY) {
                    app.beansontoast.brume.ui.FinderScreen(listOf(p), app.beansontoast.brume.ui.FinderActions(), onBack = {}, start = app.beansontoast.brume.ui.FinderPlace.Profile("demo"))
                }
            }
            "intro-card" -> capture(night) { app.beansontoast.brume.ui.launch.TitleCard() }
            else -> error(shot)
        }
    }

    private fun capture(night: Boolean, content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = night, night = night) { content() } }
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
            "home-day", "home-night", "home-mini-day", "home-mini-night", "home-modules-off-day",
            "breathe-day", "breathe-night", "breath-in-day", "breath-hold-day", "breath-out-day", "breath-hold-night",
            "settle-day", "settle-playing-night", "journal-day",
        ) + listOf("intro-card", "mixer", "mixer-add", "learn", "topic", "card", "about", "meditate", "meditate-options", "med-session", "tones", "settings", "settings-sheet", "finder", "finder-profile", "journal")
            .flatMap { listOf("$it-day", "$it-night") }.filter { it != "journal-day" }
    }
}

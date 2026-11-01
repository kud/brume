package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BreatheScene
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.SceneScreen
import app.beansontoast.brume.ui.Tab
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
 * The polish pass on dev.15 (Iris, 2026-11-01): Erwann's five phone screenshots, one for one, in
 * English at night on a 411x891 screen (his phone), on invented data and a fresh install.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*PolishRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class PolishRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val scenes = Modules().scenes()
    private val empty = PlayerUi(noise = null)
    private val suggested = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.3f, timerMinutes = 45, playing = true, timerRemainingMinutes = 45)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 4000), toneLevel = 0.1f)

    @Test fun render() = when (shot) {
        // A first visit still shows the swipe hint; once he has swiped, it is gone.
        "1-sleep-empty" -> tab(Tab.LISTEN) { SceneScreen(scenes, Scene.SLEEP, {}, empty, ListenActions(), night = true) }
        "2-settle-empty" -> tab(Tab.LISTEN) { SceneScreen(scenes, Scene.SETTLE, {}, empty, ListenActions(), night = true, swipeHint = false) }
        "3-breathe" -> tab(Tab.LISTEN) { SceneScreen(scenes, Scene.BREATHE, {}, empty, ListenActions(), breathe = BreatheScene(minutes = 5), night = true, swipeHint = false) }
        "4-tones" -> tab(Tab.LISTEN) { SceneScreen(scenes, Scene.TONES, {}, tones, ListenActions(), night = true, swipeHint = false) }
        "6-sleep-after-tap" -> tab(Tab.LISTEN) { SceneScreen(scenes, Scene.SLEEP, {}, suggested, ListenActions(), night = true, swipeHint = false) }
        "5-journal" -> tab(Tab.JOURNAL) { JournalScreen(emptyList(), 0, LocalDate.of(2026, 11, 1), null, { _, _, _ -> }, {}) }
        else -> error(shot)
    }

    /** The French names on his screen width: Se poser and Respirer must still sit in the row. */
    @Config(qualifiers = "fr-w411dp-h891dp-xxhdpi")
    @Test fun renderFrench() {
        if (shot != "2-settle-empty") return
        tab(Tab.LISTEN, "fr-") { SceneScreen(scenes, Scene.SETTLE, {}, empty, ListenActions(), night = true) }
    }

    private fun tab(tab: Tab, prefix: String = "", content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = true, night = true) { BrumeScaffold(tab, {}, Modules()) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "$prefix$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf("1-sleep-empty", "2-settle-empty", "3-breathe", "4-tones", "5-journal", "6-sleep-after-tap").map { arrayOf<Any>(it) }
    }
}

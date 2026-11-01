package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
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
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.SceneArtCache
import app.beansontoast.brume.ui.SceneScreen
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.art
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

/**
 * Sacha's scene art in place (Iris, 2026-11-01), in English at his phone's 1080 x 2340: each scene
 * in night hours (35%), by day in the dark theme (70%) and in the light theme (line art in ink),
 * then drift frames (a fixed clock, every 2 s) and swipe frames for the parallax, on invented data.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*SceneArtRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp-xxhdpi")
class SceneArtRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() {
        assumeTrue(System.getenv("BRUME_RENDERS") != null)
        Scene.entries.forEach { s -> s.art()?.let { SceneArtCache.loadAll(compose.activity, it) } }
    }

    private val playing = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.45f, timerMinutes = 45, playing = true, timerRemainingMinutes = 38)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f)
    private val breathe = BreatheScene(minutes = 5, soundName = "Brown noise")

    @Test fun render() {
        val parts = shot.split("-")
        when (parts[0]) {
            "drift" -> draw(Scene.SLEEP, night = false, dark = true, clock = parts[1].toLong() * 2000)
            "breath" -> draw(Scene.BREATHE, night = false, dark = true, clock = parts[1].toLong() * 1000)
            "swipe" -> draw(Scene.SLEEP, night = false, dark = true, clock = 0L, peek = parts[1].toInt() / 20f)
            else -> {
                val scene = Scene.valueOf(parts[0].uppercase())
                draw(scene, night = parts[1] == "night", dark = parts[1] != "light", clock = 0L)
            }
        }
    }

    private fun draw(scene: Scene, night: Boolean, dark: Boolean, clock: Long, peek: Float = 0f) {
        val ui = if (scene == Scene.TONES) tones else playing
        compose.setContent {
            BrumeTheme(dark = dark, night = night) {
                BrumeScaffold(Tab.LISTEN, {}, Modules()) {
                    SceneScreen(Modules().scenes(), scene, {}, ui, ListenActions(), breathe = breathe, night = night, peek = peek,
                        swipeHint = false, sceneClock = { clock })
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "art-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = (
            Scene.entries.flatMap { s -> listOf("night", "day", "light").map { "${s.name.lowercase()}-$it" } } +
                (0 until 30).map { "drift-%02d".format(it) } +
                (0 until 20).map { "breath-%02d".format(it) } +
                (0..9).map { "swipe-$it" }
            ).map { arrayOf<Any>(it) }
    }
}

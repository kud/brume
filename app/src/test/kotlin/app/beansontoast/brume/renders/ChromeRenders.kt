package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BreatheScene
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.LearnScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.MiniPlayer
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
import java.time.LocalDate

/**
 * The chrome (Iris, 2026-11-01, from Erwann on dev.19): the art edge to edge, the floating bar,
 * the mini player docked on it, and the art still and veiled behind Journal and Learn. English at
 * his phone's 1080 x 2340, on invented data. A 24 dp status bar and a 24 dp gesture bar are given
 * as real window insets, and drawn as a mock (a clock and a handle) so the edges can be judged.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*ChromeRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h780dp-xxhdpi")
class ChromeRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() {
        assumeTrue(System.getenv("BRUME_RENDERS") != null)
        Scene.entries.forEach { s -> s.art()?.let { SceneArtCache.loadAll(compose.activity, it) } }
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
    }

    private val playing = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.3f, 0.45f, timerMinutes = 45, playing = true, timerRemainingMinutes = 38)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f)
    private val breathe = BreatheScene(minutes = 5, soundName = "Brown noise")
    private val today = LocalDate.of(2026, 1, 20)
    private val invented = listOf(
        CheckIn("2026-01-19", 6, 5),
        CheckIn("2026-01-18", 4, 2, "Quiet day."),
        CheckIn("2026-01-17", 7, 6, "Loud restaurant at lunch."),
    )

    @Test fun render() {
        val parts = shot.split("-")
        val night = parts.last() == "night"
        when (parts[0]) {
            "sleep" -> listen(Scene.SLEEP, playing, night)
            "breathe" -> listen(Scene.BREATHE, playing, night)
            "tones" -> listen(Scene.TONES, tones, night)
            "journal" -> reading(Tab.JOURNAL, night) { JournalScreen(invented, 4 * 3600 + 25 * 60, today, null, { _, _, _ -> }, {}) }
            "learn" -> reading(Tab.LEARN, night, scrolled = parts[1] == "scrolled") { LearnScreen() }
        }
    }

    private fun listen(scene: Scene, ui: PlayerUi, night: Boolean) = capture(night) {
        BrumeTheme(dark = true, night = night) {
            BrumeScaffold(Tab.LISTEN, {}, Modules(), bleed = true) {
                SceneScreen(Modules().scenes(), scene, {}, ui, ListenActions(), breathe = breathe, night = night, swipeHint = false, sceneClock = { 0L })
            }
        }
    }

    private fun reading(tab: Tab, night: Boolean, scrolled: Boolean = false, content: @Composable () -> Unit) = capture(night, scrolled) {
        BrumeTheme(dark = true, night = night) {
            BrumeScaffold(tab, {}, Modules(), mini = { MiniPlayer("Brown noise + Rain") {} }, backdrop = Scene.SLEEP, night = night) { content() }
        }
    }

    private fun capture(night: Boolean, scrolled: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.runOnUiThread {
            val v = compose.activity.window.decorView
            val d = v.resources.displayMetrics.density
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * d).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * d).toInt()))
                .build()
            ViewCompat.dispatchApplyWindowInsets(v, bars)
        }
        compose.waitForIdle()
        if (scrolled) { compose.onRoot().performTouchInput { swipeUp() }; compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)); mockBars(Canvas(it), v.resources.displayMetrics.density) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "chrome-$shot.png"))
    }

    /** A mock clock and gesture handle where the system draws them, so the edge-to-edge art can be judged. */
    private fun mockBars(c: Canvas, d: Float) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3E6EA.toInt(); textSize = 14 * d }
        c.drawText("16:26", 20 * d, 17 * d, ink)
        val w = c.width.toFloat(); val h = c.height.toFloat()
        c.drawRoundRect(RectF(w / 2 - 54 * d, h - 12 * d, w / 2 + 54 * d, h - 8 * d), 2 * d, 2 * d, ink)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "sleep-night", "sleep-day", "breathe-night", "tones-night",
            "journal-night", "journal-day", "learn-top-night", "learn-scrolled-night",
        ).map { arrayOf<Any>(it) }
    }
}

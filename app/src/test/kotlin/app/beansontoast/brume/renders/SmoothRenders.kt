package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.Modules
import app.beansontoast.brume.ui.BreatheScene
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.JournalScreen
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.MiniPlayer
import app.beansontoast.brume.ui.Practice
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.SceneScreen
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.scenes
import app.beansontoast.brume.playback.PlayerUi
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Frame sequences of the calm transitions (Iris, 2026-11-01, docs/motion.md): the mini player
 * growing into the floating bar, and Breathe's practice switching to Meditate with the rhythms
 * fading in their own room. One frame every 80 ms of the composition clock, night, French, 360x780.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*SmoothRenders*'`.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class SmoothRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun miniPlayerJoinsTheBar() {
        var playing by mutableStateOf(false)
        compose.setContent {
            BrumeTheme(dark = true, night = true) {
                BrumeScaffold(Tab.JOURNAL, {}, Modules(), mini = if (playing) ({ MiniPlayer("Brun + Pluie") {} }) else null) {
                    JournalScreen(emptyList(), 0, LocalDate.of(2026, 11, 1), null, { _, _, _ -> }, {})
                }
            }
        }
        frames("mini") { playing = true }
    }

    @Test fun breatheToMeditate() {
        var practice by mutableStateOf(Practice.BREATHE)
        compose.setContent {
            BrumeTheme(dark = true, night = true) {
                BrumeScaffold(Tab.LISTEN, {}, Modules(), bleed = true) {
                    SceneScreen(Modules().scenes(), Scene.BREATHE, {}, PlayerUi(), ListenActions(), swipeHint = false,
                        breathe = BreatheScene(practice = practice), sceneClock = { 0L })
                }
            }
        }
        frames("practice") { practice = Practice.MEDITATE }
    }

    private fun frames(name: String, change: () -> Unit) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        shot("$name-00")
        compose.runOnUiThread(change)
        for (i in 1..10) {
            compose.mainClock.advanceTimeBy(80)
            shot("$name-%02d".format(i))
        }
    }

    private fun shot(name: String) {
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        val dir = File(System.getenv("BRUME_RENDERS")!!, "frames").apply { mkdirs() }
        File(dir, "smooth-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

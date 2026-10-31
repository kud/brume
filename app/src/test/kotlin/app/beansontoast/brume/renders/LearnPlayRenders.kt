package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.learn.Verdict
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.DecibelExplorer
import app.beansontoast.brume.ui.LearnScreen
import app.beansontoast.brume.ui.QuizDeck
import app.beansontoast.brume.ui.Tab
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
 * Comprendre as play (Iris, 2026-10-31): the decibel dial at set levels, night and day; dial frames
 * for the GIF; the quiz's front and back; and the tab's new top. French, 360x780, motion still.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*LearnPlayRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class LearnPlayRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun render() {
        val night = shot.endsWith("-night")
        val name = shot.removeSuffix("-night").removeSuffix("-day")
        when {
            name.startsWith("dial") -> page(night) { DecibelExplorer(initial = name.removePrefix("dial").toInt(), still = true) }
            name.startsWith("frame") -> page(night) { DecibelExplorer(initial = name.removePrefix("frame").toInt(), still = true) }
            name == "quiz-front" -> page(night) { QuizDeck(still = true) }
            name == "quiz-back" -> page(night) { QuizDeck(still = true, seededGuesses = mapOf(0 to Verdict.TRUE), seededFlipped = setOf(0)) }
            name == "quiz-back-true" -> page(night) { QuizDeck(still = true, startPage = 1, seededGuesses = mapOf(1 to Verdict.TRUE), seededFlipped = setOf(1)) }
            name == "learn-top" -> tab(night) { LearnScreen(still = true) }
            else -> error(shot)
        }
    }

    private fun page(night: Boolean, content: @Composable () -> Unit) = tab(night) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) { content() }
    }

    private fun tab(night: Boolean, content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = night, night = night) { BrumeScaffold(Tab.LEARN, {}, Modules()) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "learnplay-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = (
            listOf(20, 60, 85, 90, 100, 120, 135).map { "dial$it" } + listOf("quiz-front", "quiz-back", "quiz-back-true", "learn-top")
            ).flatMap { listOf("$it-night", "$it-day") }
            .plus((20..130 step 5).map { "frame$it-night" })
            .map { arrayOf<Any>(it) }
    }
}

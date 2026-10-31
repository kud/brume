package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.teach.AAbout
import app.beansontoast.brume.teach.AJournal
import app.beansontoast.brume.teach.ALearnCard
import app.beansontoast.brume.teach.AListen
import app.beansontoast.brume.teach.ATones
import app.beansontoast.brume.teach.BJournal
import app.beansontoast.brume.teach.BLearn
import app.beansontoast.brume.teach.BTones
import app.beansontoast.brume.teach.BTonight
import app.beansontoast.brume.ui.BrumeTheme
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
 * The teach exploration (Iris, 2026-10-31): directions A and B, at night, in French, on invented
 * data. `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*TeachRenders*'`
 * writes <dir>/a/ and <dir>/b/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class TeachRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun render() = when (shot) {
        "a/1-listen" -> shoot(0, a) { AListen() }
        "a/2-tones" -> shoot(2, a) { ATones() }
        "a/3-journal" -> shoot(3, a) { AJournal() }
        "a/4-learn" -> shoot(4, a) { AAbout() }
        "a/5-learn-card" -> shoot(4, a) { ALearnCard() }
        "b/1-listen" -> shoot(0, b) { BTonight() }
        "b/2-tones" -> shoot(2, b) { BTones() }
        "b/3-journal" -> shoot(3, b) { BJournal() }
        "b/4-learn" -> shoot(0, b) { BLearn() }
        else -> error(shot)
    }

    private val a = listOf("Écouter", "Calme", "Tons", "Journal", "À propos")
    private val b = listOf("Ce soir", "Calme", "Tons", "Journal", "À propos")
    private val icons = listOf(R.drawable.ic_horizon, R.drawable.ic_breath, R.drawable.ic_tones, R.drawable.ic_journal, R.drawable.ic_info)

    /** BrumeScaffold's bar, with the labels a direction gives it. Selection: indicator and weight. */
    private fun shoot(selected: Int, labels: List<String>, content: @Composable () -> Unit) = capture {
        BrumeTheme(dark = true, night = true) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                            labels.forEachIndexed { i, label ->
                                NavigationBarItem(i == selected, {}, { Icon(painterResource(icons[i]), null) },
                                    label = { Text(label, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) })
                            }
                        }
                    }
                },
            ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) { content() } }
        }
    }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "a/1-listen", "a/2-tones", "a/3-journal", "a/4-learn", "a/5-learn-card",
            "b/1-listen", "b/2-tones", "b/3-journal", "b/4-learn",
        ).map { arrayOf<Any>(it) }
    }
}

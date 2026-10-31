package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.PlayingView
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * The Horizon icon, the splash and the playing view, on invented data.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*BrandRenders*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class BrandRenders {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val out get() = File(System.getenv("BRUME_RENDERS")!!)

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    /** [px] is the visible icon; the 108 layer overhangs it by 18 on each side, as on a launcher. */
    private fun icon(px: Int, mask: String, mono: Boolean): Bitmap {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(maskPath(px.toFloat(), mask))
        val off = px * 18 / 72
        fun layer(id: Int, tint: Int? = null) = ctx.getDrawable(id)!!.apply {
            setBounds(-off, -off, px + off, px + off); tint?.let { colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        }.draw(c)
        if (mono) { c.drawColor(0xFFD5E3F2.toInt()); layer(R.drawable.ic_launcher_monochrome, 0xFF1B2B3A.toInt()) }
        else { layer(R.drawable.ic_launcher_background); layer(R.drawable.ic_launcher_foreground) }
        return bmp
    }

    private fun maskPath(s: Float, mask: String) = Path().apply {
        if (mask == "circle") addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
        else { // squircle: a superellipse, n = 4
            for (i in 0..360) {
                val a = Math.toRadians(i.toDouble()); val cs = kotlin.math.cos(a); val sn = kotlin.math.sin(a)
                val x = s / 2 + s / 2 * Math.signum(cs) * abs(cs).pow(0.5); val y = s / 2 + s / 2 * Math.signum(sn) * abs(sn).pow(0.5)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    }

    @Test fun icons() {
        for (dp in listOf(48, 24)) for (scale in listOf(1, 3)) {
            val px = dp * scale
            for (mask in listOf("circle", "squircle")) writePng(icon(px, mask, false), File(out, "icon/icon-$dp-dp-x$scale-$mask.png"))
            writePng(icon(px, "circle", true), File(out, "icon/icon-$dp-dp-x$scale-mono.png"))
        }
        writePng(icon(432, "squircle", false), File(out, "icon/icon-large-squircle.png"))
        writePng(icon(432, "circle", true), File(out, "icon/icon-large-mono.png"))
    }

    private fun splash(night: Boolean) {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        val ctx = compose.activity
        val w = 1080; val h = 2340; val icon = 720 // 240dp at xxhdpi, as the platform sizes it
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (night) 0xFF000000.toInt() else ctx.getColor(R.color.dawn_ground))
        ctx.getDrawable(R.drawable.ic_splash_mark)!!.apply { setBounds((w - icon) / 2, (h - icon) / 2, (w + icon) / 2, (h + icon) / 2) }.draw(c)
        writePng(bmp, File(out, "icon/splash-${if (night) "night" else "day"}.png"))
    }
    @Test fun splashDay() = splash(false)
    @Test fun splashNight() = splash(true)

    private val comfort = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.35f, 0.25f, playing = true, timerMinutes = 45, timerRemainingMinutes = 38)
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)),
        toneLevel = 0.2f, playing = true, timerMinutes = 90, timerRemainingMinutes = 72)

    private fun shoot(file: String) {
        compose.waitForIdle()
        val bmp = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bmp, File(out, "ux/play/$file.png"))
    }

    /** Still frames a minute apart, then a strip of 24 frames five seconds apart (the GIF runs 25x fast). */
    private fun play(name: String, ui: PlayerUi, night: Boolean, frames: List<Float>) {
        var t by mutableFloatStateOf(frames.first())
        compose.setContent { BrumeTheme(dark = night, night = night) { PlayingView(ui, night, {}, {}, time = t) } }
        frames.forEachIndexed { i, f -> t = f; shoot("$name-%02d".format(i)) }
    }

    @Test fun playNight() = play("brown-rain-night", comfort, true, listOf(0f, 60f, 120f))
    @Test fun playDay() = play("brown-rain-day", comfort, false, listOf(0f, 60f))
    @Test fun playTones() = play("tones-night", tones, true, listOf(0f))
    @Test fun playPaused() = play("pink-paused-night", PlayerUi(NoiseColour.PINK, null, playing = false, timerRemainingMinutes = null), true, listOf(30f))
    @Test fun playStrip() = play("strip/frame", comfort, true, (0 until 24).map { it * 5f })
}

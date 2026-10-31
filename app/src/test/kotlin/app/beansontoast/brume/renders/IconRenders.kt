package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.test.core.app.ApplicationProvider
import app.beansontoast.brume.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The traced UI icon set (Sacha's board, 2026-10-31) at 24, 20 and 16 dp, on the night and day
 * grounds, tinted the way the UI tints them. Run with BRUME_RENDERS=<dir>, like the other renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class IconRenders {
    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val icons = listOf(
        "tones" to R.drawable.ic_tones, "journal" to R.drawable.ic_journal, "info" to R.drawable.ic_info,
        "lock" to R.drawable.ic_lock, "check" to app.beansontoast.design.R.drawable.ic_check,
        "play" to R.drawable.ic_play, "pause" to R.drawable.ic_pause, "remove" to R.drawable.ic_remove,
        "minus" to R.drawable.ic_minus, "plus" to R.drawable.ic_plus, "back" to R.drawable.ic_back,
        "open" to R.drawable.ic_open, "too-loud" to R.drawable.ic_too_loud, "export" to R.drawable.ic_export,
        "import" to R.drawable.ic_import, "folder-sync" to R.drawable.ic_folder_sync, "horizon" to R.drawable.ic_horizon,
    )

    private fun sheet(name: String, ground: Int, ink: Int, muted: Int) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = 3 // xxhdpi
        val cols = 6
        val cellW = 124 * d; val cellH = 84 * d
        val rows = (icons.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cellW, rows * cellH + 24 * d, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(ground)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 11f * d }
        c.drawText("Brume UI icons · traced from Sacha's board · 24 / 20 / 16 dp · $name", 12f * d, 16f * d, text)
        icons.forEachIndexed { i, (label, id) ->
            val x0 = (i % cols) * cellW + 12 * d
            val y0 = (i / cols) * cellH + 24 * d
            c.drawText(label, x0.toFloat(), (y0 + 14 * d).toFloat(), text)
            var x = x0
            listOf(24, 20, 16).forEach { dp ->
                val s = dp * d
                val top = y0 + 24 * d + (24 - dp) * d / 2
                ctx.getDrawable(id)!!.mutate().apply {
                    colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
                    setBounds(x, top, x + s, top + s)
                }.draw(c)
                x += s + 12 * d
            }
        }
        writePng(bmp, File(System.getenv("BRUME_RENDERS")!!, "icons-sheet-$name.png"))
    }

    @Test fun night() = sheet("night", 0xFF0B0D10.toInt(), 0xFFE3E6EA.toInt(), 0xFF9AA3AD.toInt())
    @Test fun day() = sheet("day", 0xFFF5F6F8.toInt(), 0xFF1A1D21.toInt(), 0xFF5A626B.toInt())
}

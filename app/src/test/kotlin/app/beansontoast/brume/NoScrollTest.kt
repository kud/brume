package app.beansontoast.brume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Brume never scrolls (Erwann: « un truc vraiment carré, un peu snap »; the no-scroll pass,
 * 2026-11-01): a screen fits its frame, or pages through the pagers and steppers (`HorizontalPager`
 * with `PagerWords` or `PageStepper`, ui/Sheet.kt), which move a page at a time. A free scroll in
 * `src/main` fails here; FitTest checks that the screens fit.
 */
class NoScrollTest {
    private val scroll = Regex("""\b(verticalScroll|horizontalScroll)\s*\(|\b(LazyColumn|LazyRow|LazyVerticalGrid|LazyHorizontalGrid)\s*[({]""")

    /** Each line of [source] that scrolls freely, as "name:line: text". Comments and imports never count. */
    private fun scrolls(name: String, source: String): List<String> = source.lines().mapIndexedNotNull { i, line ->
        val code = line.substringBefore("//").trim()
        if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("import ")) null
        else if (scroll.containsMatchIn(code)) "$name:${i + 1}: $code" else null
    }

    private val sources = listOf("src/main", "src/github", "src/fdroid").map(::File).filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test fun noScreenScrollsFreely() {
        // Guard the input: an empty set of files would pass for any rule.
        assertTrue("found ${sources.size} Kotlin files", sources.size > 30)
        assertTrue("the scan reads the screens", sources.any { it.name == "Screens.kt" })
        val found = sources.flatMap { scrolls(it.path, it.readText()) }
        assertEquals("free scrolling in the app:\n" + found.joinToString("\n"), emptyList<String>(), found)
    }

    @Test fun theScanFindsAPlantedScroll() {
        val planted = """
            import androidx.compose.foundation.verticalScroll
            // A comment may say verticalScroll(state) without counting.
            fun Page() {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { }
                Row(Modifier.horizontalScroll (state)) { }
                LazyColumn { }
                HorizontalPager(pager) { }
            }
        """.trimIndent()
        assertEquals(listOf(4, 5, 6), scrolls("planted", planted).map { it.split(':')[1].toInt() })
    }
}

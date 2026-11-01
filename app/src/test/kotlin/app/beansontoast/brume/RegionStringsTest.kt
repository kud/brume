package app.beansontoast.brume

import app.beansontoast.brume.core.region.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The country table lives in `:core`, its sentences in string resources keyed `region_<key>`. This
 * pins them together in both languages: every line has its words, every number has its slot, and
 * no sentence spells out a number the table doesn't hold (a hard-coded 3114 is what this replaced).
 */
class RegionStringsTest {
    private val entry = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    private fun strings(dir: String) = entry.findAll(File("src/main/res/$dir/strings.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }
    private val locales = listOf("values", "values-fr").associateWith { strings(it) }
    private val slot = Regex("%(\\d+)[$]s")

    @Test fun `every line has its words and one slot per number, in both languages`() {
        locales.forEach { (dir, map) ->
            val keys = map.keys.filter { it.startsWith("region_") && it.removePrefix("region_").substringBefore('_') in setOf("uk", "fr", "other") }
            assertEquals(dir, Regions.all.map { "region_${it.key}" }.toSet(), keys.toSet())
            Regions.all.forEach { r ->
                val text = map.getValue("region_${r.key}")
                val slots = slot.findAll(text).map { it.groupValues[1].toInt() }.toSet()
                assertEquals("$dir ${r.key} slots", (1..r.phones.size).toSet(), slots)
            }
        }
    }

    @Test fun `no help sentence spells out a phone number of its own`() {
        val number = Regex("(?<![\\d.:–-])\\d{3,}(?: \\d{2,})*(?![\\d.:–])")
        assertTrue(number.containsMatchIn("call 3114 now"))
        locales.forEach { (dir, map) ->
            map.filterKeys { it.startsWith("region_") || it.startsWith("learn_c6_") || it.startsWith("learn_c7_") || it.startsWith("doctor_") }
                .forEach { (k, text) ->
                    val stripped = text.replace(slot, "").replace(Regex("\\d+ ?dB|\\d+:\\d+|\\d+–\\d+|£\\d+|€\\d+|\\d+ (?:à|to) \\d+|\\d+ (?:h|hours|séances|sessions)"), "")
                    assertTrue("$dir $k: \"$text\"", !number.containsMatchIn(stripped))
                }
        }
    }
}

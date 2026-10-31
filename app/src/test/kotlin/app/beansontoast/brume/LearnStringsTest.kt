package app.beansontoast.brume

import app.beansontoast.brume.core.learn.Learn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Learn catalogue lives in `:core`, its words in string resources keyed by card id. This pins
 * the two together in both languages, and scans every card's words for claims on its own, so a
 * new card can't slip past by living outside the strings GuardsTest knows about.
 */
class LearnStringsTest {
    private val entry = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    private fun strings(dir: String) = entry.findAll(File("src/main/res/$dir/strings.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }
    private val locales = listOf("values", "values-fr").associateWith { strings(it) }

    private val claims = Regex(
        "\\b(treat\\w*|cure[sd]?|curing|therap\\w*|relie(f|ve|ves|ving)|heal(s|ing)?|clinically|proven|reduces tinnitus|" +
            "trait(e|er|ement)s?|guéri\\w*|thérap\\w*|soulag\\w*|cliniquement)\\b",
        RegexOption.IGNORE_CASE,
    )

    @Test fun `every card has its words in both languages, and nothing else claims the learn_ prefix`() {
        val expected = Learn.cards.flatMap { c -> listOfNotNull("learn_${c.id}_title", "learn_${c.id}_body", if (c.tryThis) "learn_${c.id}_try" else null) } +
            (1..Learn.TOPICS).map { "learn_topic_$it" }
        locales.forEach { (dir, map) ->
            val learn = map.keys.filter { Regex("learn_(c\\d.*|topic_\\d+)").matches(it) }
            assertEquals(dir, expected.toSet(), learn.toSet())
            expected.forEach { assertTrue("$dir $it is empty", map.getValue(it).isNotBlank()) }
        }
    }

    @Test fun `no card makes a claim`() {
        assertTrue(claims.containsMatchIn("A therapy that relieves"))
        assertTrue(claims.containsMatchIn("Un traitement qui guérit"))
        val cardKeys = Learn.cards.flatMap { c -> listOf("title", "body", "try").map { "learn_${c.id}_$it" } }
        var scanned = 0
        locales.forEach { (dir, map) ->
            cardKeys.mapNotNull { k -> map[k]?.let { k to it } }.forEach { (k, text) ->
                scanned++
                assertTrue("$dir $k: \"$text\"", !claims.containsMatchIn(text))
            }
            Learn.cards.forEach { assertTrue("${it.id} sources", !claims.containsMatchIn(it.sources)) }
        }
        assertTrue("scanned $scanned", scanned >= Learn.cards.size * 4)
    }
}

package app.beansontoast.brume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two promises checked on the source itself: Brume never touches the system volume or the network,
 * and no string makes a health claim. Each guard first proves it can still fail.
 */
class GuardsTest {
    private val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val stringFiles = File("src/main/res").listFiles { f -> f.name.startsWith("values") }!!
        .map { File(it, "strings.xml") }.filter { it.exists() }

    private val forbiddenCode = listOf(
        "setStreamVolume", "adjustStreamVolume", "adjustSuggestedStreamVolume", "adjustVolume(",
        "java.net.", "javax.net.", "HttpURLConnection", "okhttp", "android.permission.INTERNET",
    )

    private fun codeHits(text: String) = forbiddenCode.filter { it in text }

    @Test fun `no source raises the volume or reaches the network`() {
        assertTrue("no Kotlin sources found", sources.size >= 5)
        assertEquals(listOf("setStreamVolume"), codeHits("audio.setStreamVolume(STREAM_MUSIC, 15, 0)"))
        sources.forEach { assertEquals("${it.path}", emptyList<String>(), codeHits(it.readText())) }
    }

    // Wellness wording (evidence.md §16, experience.md §8). The disclaimer may say what Brume is not.
    private val claims = Regex(
        "\\b(treat|treats|treating|treatment|cure|cures|cured|curing|therapy|therapies|therapeutic|relieve|relieves|relief|heal|heals|healing|" +
            "clinically|proven|reduces tinnitus|your tinnitus will|" +
            "traite|traiter|traitement|guérit|guérir|guérison|thérapie|thérapeutique|soulage|soulager|soulagement|cliniquement)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val entry = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    private val allowed = setOf("about_disclaimer")

    /**
     * The one proper noun the claim words may sit inside: the NHS service's official name (Erwann,
     * 2026-11-01: « Garde le nom officiel NHS Talking Therapies »). Exact case, whole name, removed
     * before the scan, so "therapy" or "therapies" anywhere else is still caught.
     */
    private val properNouns = Regex("\\bNHS Talking Therapies\\b")

    private fun claimIn(text: String) = claims.containsMatchIn(properNouns.replace(text, ""))

    private fun strings(file: File) = entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun `no string makes a claim`() {
        assertTrue("both locales present", stringFiles.size >= 2)
        assertTrue(claims.containsMatchIn("This treatment works"))
        assertTrue(claims.containsMatchIn("Un soulagement durable"))
        stringFiles.forEach { file ->
            strings(file).filterKeys { it !in allowed }.forEach { (name, text) ->
                assertTrue("$file $name: \"$text\"", !claimIn(text))
            }
        }
    }

    @Test fun `only the exact name NHS Talking Therapies is let through`() {
        assertTrue(!claimIn("refer yourself to NHS Talking Therapies, without a GP"))
        assertTrue(!claimIn("au service NHS Talking Therapies"))
        listOf(
            "NHS Talking Therapies is a therapy",
            "NHS Talking Therapies and other therapies",
            "Talking therapies",
            "NHS talking therapies",
            "NHS Talking Therapy",
            "Talking Therapies",
            "A therapy for tinnitus",
        ).forEach { assertTrue(it, claimIn(it)) }
        // The real strings do name it, so the exception is in use, not dead.
        assertTrue(stringFiles.all { "NHS Talking Therapies" in it.readText() })
    }

    @Test fun `every string exists in English and French`() {
        val en = strings(File("src/main/res/values/strings.xml")).keys
        val fr = strings(File("src/main/res/values-fr/strings.xml")).keys
        assertTrue(en.size > 30)
        assertEquals(en, fr)
    }
}

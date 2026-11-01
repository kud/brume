package app.beansontoast.brume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Promises checked on the source itself: Brume never touches the system volume; `src/main` and the
 * fdroid build never reach the network, and in the github build only the updater does; no string
 * makes a health claim. Each guard first proves it can still fail.
 */
class GuardsTest {
    private fun kotlin(dir: String) = File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private val sources = kotlin("src/main/kotlin") + kotlin("src/fdroid/kotlin")
    private val githubSources = kotlin("src/github/kotlin")
    private val resRoots = listOf("src/main/res", "src/github/res")
    private val stringFiles = resRoots.flatMap { root ->
        File(root).listFiles { f -> f.name.startsWith("values") }!!.map { File(it, "strings.xml") }.filter { it.exists() }
    }

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

    /**
     * The github build may use the network, only to check and download Brume's own releases: the
     * HTTP code lives in its `update/` package (Updater, through Hosts.ALLOWED), and nowhere else.
     */
    @Test fun `in the github build only the updater reaches the network`() {
        assertTrue("no github sources found", githubSources.size >= 3)
        val network = listOf("java.net.", "javax.net.", "HttpURLConnection", "okhttp")
        val reaching = githubSources.filter { f -> network.any { it in f.readText() } }.map { it.invariantSeparatorsPath }
        assertTrue("the updater no longer found", reaching.any { it.endsWith("/update/Updater.kt") })
        reaching.forEach { assertTrue("$it reaches the network outside update/", "/brume/update/" in it) }
        githubSources.forEach { assertTrue("${it.path} must not change the volume", codeHits(it.readText()).none { "Volume" in it }) }
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
        assertTrue("both locales present in main and github", stringFiles.size >= 4)
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
        assertTrue(stringFiles.filter { "src/main" in it.invariantSeparatorsPath }.all { "NHS Talking Therapies" in it.readText() })
    }

    @Test fun `every string exists in English and French`() {
        resRoots.forEach { root ->
            val en = strings(File("$root/values/strings.xml")).keys
            val fr = strings(File("$root/values-fr/strings.xml")).keys
            assertTrue(root, en.size > 20)
            assertEquals(root, en, fr)
        }
    }

    // « Mon acouphène » (2026-11-01): finding and describing, never testing or diagnosing, and no programme.
    private val clinical = Regex(
        "\\b(test|tests|testing|measure|measurement|detect|detection|assessment|diagnos\\w*|programme|program|" +
            "examen|mesure\\w*|détect\\w*|évaluation|diagnostic\\w*)\\b|\\bCR\\b|notch|encoche",
        RegexOption.IGNORE_CASE,
    )

    @Test fun `the tinnitus journey finds and describes, never tests or diagnoses`() {
        assertTrue(clinical.containsMatchIn("A hearing test"))
        assertTrue(clinical.containsMatchIn("Un diagnostic"))
        var seen = 0
        listOf("values", "values-fr").forEach { dir ->
            strings(File("src/main/res/$dir/strings.xml")).filterKeys { it.startsWith("finder_") || it.startsWith("sound_band") }.forEach { (name, text) ->
                seen++
                assertTrue("$dir $name: \"$text\"", !clinical.containsMatchIn(text))
            }
        }
        assertTrue("the guard read the journey's strings", seen > 150)
    }
}

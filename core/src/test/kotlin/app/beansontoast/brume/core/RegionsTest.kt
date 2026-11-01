package app.beansontoast.brume.core

import app.beansontoast.brume.core.region.Country
import app.beansontoast.brume.core.region.Need
import app.beansontoast.brume.core.region.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegionsTest {
    private val urgentNeeds = setOf(Need.EMERGENCY, Need.TEXT_EMERGENCY, Need.URGENT, Need.CRISIS)

    @Test fun `every line was checked within the last twelve months, and not in the future`() {
        val today = LocalDate.now()
        assertTrue(Regions.all.isNotEmpty())
        Regions.all.forEach {
            assertTrue("${it.key} checked ${it.checked}: older than a year, check it again", !it.checked.isBefore(today.minusMonths(12)))
            assertTrue("${it.key} checked in the future", !it.checked.isAfter(today))
        }
    }

    @Test fun `every country answers the urgent needs`() {
        (Regions.supported + Country.OTHER).forEach { country ->
            listOf(Need.EMERGENCY, Need.URGENT, Need.CRISIS, Need.SUDDEN_HEARING, Need.CARE, Need.ASSOCIATION, Need.EARPLUGS).forEach { need ->
                assertTrue("$country $need", Regions.lines(country, listOf(need)).isNotEmpty())
            }
        }
        Regions.supported.forEach { assertTrue("$it has its own emergency number", Regions.lines(it, listOf(Need.EMERGENCY)).single().phones.isNotEmpty()) }
    }

    @Test fun `numbers are digits and spaces, keys are unique`() {
        val shape = Regex("\\+?[0-9]+( [0-9]+)*")
        Regions.all.flatMap { it.phones }.forEach { assertTrue(it.shown, shape.matches(it.shown)) }
        assertEquals(Regions.all.size, Regions.all.map { it.key }.toSet().size)
    }

    @Test fun `the country picks the numbers`() {
        assertEquals(listOf("999"), Regions.lines(Country.UK, listOf(Need.EMERGENCY)).single().phones.map { it.shown })
        assertEquals(listOf("3114"), Regions.lines(Country.FR, listOf(Need.CRISIS)).single().phones.map { it.shown })
        assertEquals(listOf("112"), Regions.lines(Country.OTHER, listOf(Need.EMERGENCY)).single().phones.map { it.shown })
        // The fallback has no text route: none is shown rather than a wrong one.
        assertTrue(Regions.lines(Country.OTHER, listOf(Need.TEXT_EMERGENCY)).isEmpty())
    }

    @Test fun `a French speaker in the UK also gets France Acouphenes, dialled from abroad, and nothing urgent`() {
        val also = Regions.alsoInYourLanguage(Country.UK, "fr")
        assertEquals(listOf("+33142050146"), also.flatMap { it.phones }.map { it.dial })
        assertTrue(Regions.alsoInYourLanguage(Country.FR, "fr").isEmpty())
        assertTrue(Regions.alsoInYourLanguage(Country.FR, "en").isEmpty())
        assertTrue(Regions.alsoInYourLanguage(Country.OTHER, "fr").isNotEmpty())
        Regions.supported.plus(Country.OTHER).forEach { c ->
            listOf("fr", "en").flatMap { Regions.alsoInYourLanguage(c, it) }.forEach {
                assertTrue("${it.key} is urgent", it.need !in urgentNeeds)
                it.phones.forEach { p -> assertTrue("${it.key} dials from abroad", p.dial.startsWith("+")) }
            }
        }
    }

    @Test fun `the guess prefers the SIM, then the region, then the fallback`() {
        assertEquals(Country.UK, Regions.guess("gb", "FR"))
        assertEquals(Country.OTHER, Regions.guess("us", "FR"))
        assertEquals(Country.FR, Regions.guess("", "FR"))
        assertEquals(Country.FR, Regions.guess(null, "fr"))
        assertEquals(Country.OTHER, Regions.guess(null, ""))
        assertEquals(Country.UK, Country.fromIso("UK"))
        assertEquals(Country.OTHER, Country.fromIso(null))
    }

    @Test fun `the red flags carry sudden hearing loss, emergency and crisis everywhere`() {
        (Regions.supported + Country.OTHER).forEach { c ->
            assertEquals(Regions.redFlags, Regions.lines(c, Regions.redFlags).map { it.need })
        }
    }
}

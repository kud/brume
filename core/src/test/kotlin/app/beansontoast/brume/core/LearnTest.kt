package app.beansontoast.brume.core

import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.NoteTags
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.learn.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnTest {
    @Test fun `ids are unique and every topic has cards`() {
        assertEquals(Learn.cards.size, Learn.cards.map { it.id }.toSet().size)
        (1..Learn.TOPICS).forEach { assertTrue("topic $it", Learn.topic(it).isNotEmpty()) }
        assertTrue(Learn.cards.all { it.topic in 1..Learn.TOPICS && it.sources.isNotBlank() })
    }

    @Test fun `unverified cards stay out`() {
        assertEquals(null, Learn.card("c5_8"))
    }

    @Test fun `every tag leads to a card`() {
        Tag.entries.forEach { assertTrue(it.name, Learn.card(it.card) != null) }
    }

    @Test fun `the path takes one step a day and comes round`() {
        val n = Learn.cards.size
        assertEquals(1, Learn.step(100, 100))
        assertEquals(2, Learn.step(100, 101))
        assertEquals(1, Learn.step(100, 100L + n))
        assertEquals(1, Learn.step(100, 90))
        assertEquals("c1_1", Learn.atStep(1).id)
    }

    @Test fun `tags ride on the note's last line and come back`() {
        val note = NoteTags.compose("Loud bar at lunch.", setOf(Tag.STRESS, Tag.CAFFEINE))
        assertEquals("Loud bar at lunch.\n#caffeine #stress", note)
        assertEquals(NoteTags.Parts("Loud bar at lunch.", setOf(Tag.CAFFEINE, Tag.STRESS)), NoteTags.parse(note))
        assertEquals("#alcohol", NoteTags.compose("  ", setOf(Tag.ALCOHOL)))
        assertEquals(NoteTags.Parts("", setOf(Tag.ALCOHOL)), NoteTags.parse("#alcohol"))
    }

    @Test fun `a note that only mentions a hash is left alone`() {
        assertEquals(NoteTags.Parts("Room #4 was loud", emptySet()), NoteTags.parse("Room #4 was loud"))
        assertEquals(NoteTags.Parts("Coffee #caffeine maybe", emptySet()), NoteTags.parse("Coffee #caffeine maybe"))
        assertEquals(NoteTags.Parts("", emptySet()), NoteTags.parse(""))
    }

    @Test fun `the tag line always fits`() {
        val note = NoteTags.compose("x".repeat(CheckIn.NOTE_MAX), Tag.entries.toSet())
        assertTrue(note.length <= CheckIn.NOTE_MAX)
        assertEquals(Tag.entries.toSet(), NoteTags.parse(note).tags)
        CheckIn("2026-01-20", 1, 1, note)
    }
}

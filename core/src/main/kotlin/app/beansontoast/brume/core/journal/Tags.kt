package app.beansontoast.brume.core.journal

import app.beansontoast.brume.core.learn.Tag

/**
 * Journal tags ride in the note, as tokens on its last line ("#caffeine #stress"), so they sync
 * and export with no schema change and stay readable in the JSON. The note is still the person's
 * own words; the app shows the tags apart and never infers anything from them.
 */
object NoteTags {
    data class Parts(val text: String, val tags: Set<Tag>)

    fun parse(note: String): Parts {
        val lines = note.trimEnd().lines()
        val last = lines.lastOrNull().orEmpty().trim()
        val words = last.split(' ').filter { it.isNotEmpty() }
        val tags = words.mapNotNull { w -> Tag.entries.firstOrNull { it.token == w } }
        if (words.isEmpty() || tags.size != words.size) return Parts(note.trim(), emptySet())
        return Parts(lines.dropLast(1).joinToString("\n").trim(), tags.toSet())
    }

    /** The note to store: the text, cut so the tag line always fits in [CheckIn.NOTE_MAX], then the tags. */
    fun compose(text: String, tags: Set<Tag>): String {
        val tagLine = Tag.entries.filter { it in tags }.joinToString(" ") { it.token }
        val clean = text.trim()
        if (tagLine.isEmpty()) return clean.take(CheckIn.NOTE_MAX)
        val room = CheckIn.NOTE_MAX - tagLine.length - 1
        val body = clean.take(room).trim()
        return if (body.isEmpty()) tagLine else "$body\n$tagLine"
    }
}

package app.beansontoast.brume.core.sync

import app.beansontoast.brume.core.export.CheckInV1
import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.SessionV1
import app.beansontoast.brume.core.export.SettingsV1
import java.time.Instant

/**
 * Folder sync (atelier ADR 0001, pastille's pattern): this phone and the shared file are both
 * versions of the same journal, and merging them never loses a row.
 *
 * - Check-ins: one per day. A day only one side has is kept; a day both have keeps the later save
 *   (`updatedAt`). A row with no time (a file from before sync) counts as oldest, so an old export
 *   fills gaps and never overwrites a day this phone has.
 * - Sessions: an append-only log, so the union, each session once.
 * - Settings: per group (night hours; tone pitch and trims together; the module switches together), the later change wins.
 *
 * Every tie is broken on the values themselves, never on which side is "local", so two phones that
 * merge the same pair reach the same file. Brume never deletes a check-in, so no base or tombstone
 * is needed yet; the day it can, this grows pastille's three-way base.
 */
object SyncMerge {
    private val checkInOrder = compareBy<CheckInV1>({ it.updatedAt ?: 0L }, { it.loudness }, { it.bother }, { it.note })

    fun merge(local: ExportFile, other: ExportFile, exportedAt: String, appVersion: String): ExportFile = ExportFile(
        exportedAt = exportedAt,
        appVersion = appVersion,
        checkIns = (local.checkIns + other.checkIns).groupBy { it.day }.values.map { it.maxWith(checkInOrder) }.sortedBy { it.day },
        sessions = (local.sessions + other.sessions).distinct().sortedWith(sessionOrder),
        settings = mergeSettings(local.settings, other.settings),
    )

    private val sessionOrder = compareBy<SessionV1>({ runCatching { Instant.parse(it.startedAt) }.getOrNull() }, { it.toString() })

    private fun mergeSettings(a: SettingsV1?, b: SettingsV1?): SettingsV1? {
        if (a == null || b == null) return a ?: b
        val night = listOf(a, b).filter { it.nightHours != null }
            .maxWithOrNull(compareBy({ it.nightHoursAt ?: 0L }, { it.nightHours }))
        val tones = listOf(a, b).filter { it.tonePitch != null && it.toneTrims != null }
            .maxWithOrNull(compareBy({ it.tonesAt ?: 0L }, { it.tonePitch }, { it.toneTrims.toString() }))
        val modules = listOf(a, b).filter { it.modules != null }
            .maxWithOrNull(compareBy({ it.modulesAt ?: 0L }, { it.modules.toString() }))
        return SettingsV1(
            nightHours = night?.nightHours, nightHoursAt = night?.nightHoursAt,
            tonePitch = tones?.tonePitch, toneTrims = tones?.toneTrims, tonesAt = tones?.tonesAt,
            modules = modules?.modules, modulesAt = modules?.modulesAt,
        )
    }

    /** True when the two files hold the same journal and settings, whoever wrote them and when. */
    fun sameContent(a: ExportFile, b: ExportFile): Boolean =
        a.copy(exportedAt = "", appVersion = "") == b.copy(exportedAt = "", appVersion = "")

    /** What this phone must take from [merged] to match it. */
    data class Changes(
        val checkIns: List<CheckInV1>,
        val sessions: List<SessionV1>,
        /** Night hours and its time, when it changed. */
        val nightHours: Pair<Boolean, Long>?,
        /** Tone pitch, trims and their time, when they changed. */
        val tones: Triple<Int, List<Float>, Long>?,
        /** The module switches and their time, when they changed. */
        val modules: Pair<Map<String, Boolean>, Long>? = null,
    ) {
        val isEmpty get() = checkIns.isEmpty() && sessions.isEmpty() && nightHours == null && tones == null && modules == null
    }

    fun changesFor(local: ExportFile, merged: ExportFile): Changes {
        val mine = local.checkIns.toSet()
        val logged = local.sessions.toSet()
        val s = merged.settings
        val l = local.settings
        return Changes(
            checkIns = merged.checkIns.filter { it !in mine },
            sessions = merged.sessions.filter { it !in logged },
            nightHours = s?.nightHours?.takeIf { s.nightHours != l?.nightHours || s.nightHoursAt != l?.nightHoursAt }
                ?.let { it to (s.nightHoursAt ?: 0L) },
            tones = if (s?.tonePitch != null && s.toneTrims != null &&
                (s.tonePitch != l?.tonePitch || s.toneTrims != l?.toneTrims || s.tonesAt != l?.tonesAt)
            ) Triple(s.tonePitch, s.toneTrims, s.tonesAt ?: 0L) else null,
            modules = s?.modules?.takeIf { s.modules != l?.modules || s.modulesAt != l?.modulesAt }
                ?.let { it to (s.modulesAt ?: 0L) },
        )
    }
}

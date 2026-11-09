package com.invictus.abhyas.ui.library

import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.pyq.PaperMeta
import com.invictus.abhyas.domain.pack.PaperNaming

/** One installed question paper / bank as the Library shows it. */
data class PaperRow(
    val bank: BankEntity,
    val title: String,
    /** Physics, Chemistry, Biology, Maths, or the raw subject text. */
    val subjectGroup: String,
    /** PYQ, Theory or Other. */
    val type: String,
    val coaching: String?,
    val year: Int?,
    val completed: Boolean,
    /** Attempt id of an unfinished (not submitted) try of this paper, if any. */
    val inProgressAttemptId: Long? = null,
)

/** Which papers have a submitted attempt, and which one has an unfinished attempt right now. */
data class PaperStatus(
    val submitted: Set<String> = emptySet(),
    val inProgress: Map<String, Long> = emptyMap(),
)

/** Filters chosen in the Library filter sheet. Null / false = no restriction. */
data class PaperFilter(
    val subject: String? = null,
    val type: String? = null,
    val completedOnly: Boolean = false,
    val coaching: String? = null,
) {
    val active: Boolean get() = subject != null || type != null || completedOnly || coaching != null
    val count: Int get() = listOf(subject != null, type != null, completedOnly, coaching != null).count { it }

    fun matches(r: PaperRow): Boolean =
        (subject == null || r.subjectGroup == subject) &&
            (type == null || r.type == type) &&
            (!completedOnly || r.completed) &&
            (coaching == null || r.coaching.equals(coaching, ignoreCase = true))
}

object PaperRows {
    val SUBJECTS = listOf("Physics", "Chemistry", "Biology")
    const val TYPE_PYQ = "PYQ"
    const val TYPE_THEORY = "Theory"
    const val TYPE_OTHER = "Other"

    fun subjectGroup(raw: String?): String {
        val s = raw?.trim().orEmpty()
        val l = s.lowercase()
        return when {
            l.contains("phys") -> "Physics"
            l.contains("chem") -> "Chemistry"
            l.contains("bio") || l.contains("botan") || l.contains("zoolog") -> "Biology"
            l.contains("math") -> "Maths"
            s.isEmpty() -> "Mixed"
            else -> s
        }
    }

    fun typeOf(kind: String?): String = when (kind?.trim()?.uppercase()) {
        "PYQ", "PYQS" -> TYPE_PYQ
        "THEORY" -> TYPE_THEORY
        else -> TYPE_OTHER
    }

    fun build(banks: List<BankEntity>, meta: Map<String, PaperMeta>, completed: Set<String>, status: PaperStatus = PaperStatus()): List<PaperRow> =
        banks.map { b ->
            val m = meta[b.id]
            PaperRow(
                bank = b,
                title = PaperNaming.pretty(b.title),
                subjectGroup = subjectGroup(m?.subject ?: b.subject),
                type = typeOf(m?.kind ?: b.kind),
                coaching = m?.coaching?.takeIf { it.isNotBlank() },
                year = m?.year,
                completed = (b.id in completed || b.id in status.submitted) && b.id !in status.inProgress,
                inProgressAttemptId = status.inProgress[b.id],
            )
        }.sortedWith(
            // Unfinished papers first, then newest year, then by name.
            compareByDescending<PaperRow> { it.inProgressAttemptId != null }
                .thenByDescending { it.year ?: Int.MIN_VALUE }
                .thenBy { it.title.lowercase() },
        )

    /** Orders question ids "bank:1", "bank:2", ... "bank:10" by their number instead of as text. */
    fun paperOrder(ids: List<String>): List<String> {
        val tail = Regex("(\\d+)$")
        return ids.sortedWith(compareBy<String>({ tail.find(it)?.value?.toLongOrNull() ?: Long.MAX_VALUE }, { it }))
    }
}

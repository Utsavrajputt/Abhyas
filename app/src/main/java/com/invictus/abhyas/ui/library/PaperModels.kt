package com.invictus.abhyas.ui.library

import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.data.pyq.PaperMeta

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

    fun build(banks: List<BankEntity>, meta: Map<String, PaperMeta>, completed: Set<String>): List<PaperRow> =
        banks.map { b ->
            val m = meta[b.id]
            PaperRow(
                bank = b,
                title = b.title,
                subjectGroup = subjectGroup(m?.subject ?: b.subject),
                type = typeOf(m?.kind ?: b.kind),
                coaching = m?.coaching?.takeIf { it.isNotBlank() },
                year = m?.year,
                completed = b.id in completed,
            )
        }.sortedWith(compareByDescending<PaperRow> { it.year ?: Int.MIN_VALUE }.thenBy { it.title.lowercase() })

    /** Orders question ids "bank:1", "bank:2", ... "bank:10" by their number instead of as text. */
    fun paperOrder(ids: List<String>): List<String> {
        val tail = Regex("(\\d+)$")
        return ids.sortedWith(compareBy<String>({ tail.find(it)?.value?.toLongOrNull() ?: Long.MAX_VALUE }, { it }))
    }
}

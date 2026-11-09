package com.invictus.abhyas.domain.pack

/**
 * Turns a raw file-style paper name such as `NEET_2016_Phase_1_Question_Paper_with_Solutions_Code_A_P_W_`
 * into a readable one (`NEET 2016 Phase 1 (Code A)`). Idempotent: an already clean name comes back unchanged.
 * Pure JVM, unit-tested.
 */
object PaperNaming {
    private val CODE = Regex("\\b[Cc]ode\\s*[-:]?\\s*([A-Z0-9]{1,2})\\b")
    private val NOISE = listOf(
        Regex("(?i)\\bquestion\\s*papers?\\b"),
        Regex("(?i)\\bwith\\s+(detailed\\s+)?(solutions?|answers?(\\s*keys?)?)\\b"),
        Regex("(?i)\\b(solutions?|answer\\s*keys?)\\b"),
        Regex("(?i)\\b(pdf|final|official|download|free|copy)\\b"),
        Regex("(?i)\\bwww\\.\\S+"),
    )
    private val SPACES = Regex("\\s+")
    private val SINGLE_LETTER = Regex("^\\p{L}$")
    private const val MAX = 70

    fun pretty(raw: String): String {
        val original = raw.trim()
        var s = original
        if (s.endsWith(".pdf", ignoreCase = true) || s.endsWith(".zip", ignoreCase = true)) s = s.dropLast(4)
        s = s.replace('_', ' ').replace('(', ' ').replace(')', ' ')
        s = s.replace(Regex("(?<!\\d)-|-(?!\\d)"), " ")

        var code: String? = null
        CODE.find(s)?.let { m ->
            code = m.groupValues[1]
            s = s.removeRange(m.range)
        }
        NOISE.forEach { s = it.replace(s, " ") }

        val tokens = s.split(SPACES).filter { it.isNotEmpty() }.toMutableList()
        while (tokens.size > 2 && SINGLE_LETTER.matches(tokens.last())) tokens.removeAt(tokens.lastIndex)

        val base = tokens.joinToString(" ")
        if (base.isEmpty()) return original
        val full = if (code != null) "$base (Code $code)" else base
        return if (full.length <= MAX) full else full.take(MAX).trimEnd()
    }
}

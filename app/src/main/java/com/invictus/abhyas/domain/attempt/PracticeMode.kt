package com.invictus.abhyas.domain.attempt

import com.invictus.abhyas.domain.query.KindFilter
import com.invictus.abhyas.domain.query.QuestionFilter

enum class PracticeMode(val label: String) {
    QUICK("Quick"),
    CHAPTER("Chapter"),
    TOPIC("Topic"),
    SUBJECT("Subject"),
    RANDOM("Random"),
    PYQ("Previous year"),
    THEORY("Theory only"),
    NUMERICAL("Numerical only"),
    INCORRECT("Incorrect only"),
    BOOKMARKED("Bookmarked"),
    CUSTOM("Custom");

    /** Sets the mode's own flags on [f], clearing the flags other modes set. CUSTOM leaves [f] alone. */
    fun apply(f: QuestionFilter): QuestionFilter {
        if (this == CUSTOM) return f
        val base = f.copy(pyqOnly = false, incorrectOnly = false, bookmarkedOnly = false, kind = KindFilter.ALL)
        return when (this) {
            PYQ -> base.copy(pyqOnly = true)
            THEORY -> base.copy(kind = KindFilter.THEORY)
            NUMERICAL -> base.copy(kind = KindFilter.NUMERICAL)
            INCORRECT -> base.copy(incorrectOnly = true)
            BOOKMARKED -> base.copy(bookmarkedOnly = true)
            else -> base
        }
    }

    /** Null when [f] has what this mode needs, otherwise a message for the user. */
    fun validate(f: QuestionFilter): String? = when (this) {
        SUBJECT -> if (f.subject == null) "Choose a subject" else null
        CHAPTER -> if (f.subject == null || f.chapter == null) "Choose a subject and chapter" else null
        TOPIC -> if (f.subject == null || f.chapter == null || f.topic == null) "Choose a subject, chapter and topic" else null
        else -> null
    }

    /** Recently attempted questions are skipped, except when the point is to revisit them. */
    val avoidRecent: Boolean get() = this != INCORRECT && this != BOOKMARKED
}

data class PracticeConfig(
    val mode: PracticeMode,
    val filter: QuestionFilter,
    val count: Int,
    val durationSec: Int? = null,
) {
    val title: String get() = "${mode.label} practice"
}

enum class PaletteStatus { NOT_VISITED, NOT_ANSWERED, ANSWERED, MARKED, ANSWERED_MARKED }

fun paletteStatus(visited: Boolean, answered: Boolean, marked: Boolean): PaletteStatus = when {
    answered && marked -> PaletteStatus.ANSWERED_MARKED
    marked -> PaletteStatus.MARKED
    answered -> PaletteStatus.ANSWERED
    visited -> PaletteStatus.NOT_ANSWERED
    else -> PaletteStatus.NOT_VISITED
}

package com.invictus.abhyas.domain.pack

import com.invictus.abhyas.data.local.BankEntity
import com.invictus.abhyas.ui.library.PaperRows
import com.invictus.abhyas.ui.library.PaperStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperNamingTest {
    @Test fun cleansFileStyleName() {
        assertEquals(
            "NEET 2016 Phase 1 (Code A)",
            PaperNaming.pretty("NEET_2016_Phase_1_Question_Paper_with_Solutions_Code_A_P_W_"),
        )
    }

    @Test fun isIdempotent() {
        val once = PaperNaming.pretty("NEET_2016_Phase_1_Question_Paper_with_Solutions_Code_A_P_W_")
        assertEquals(once, PaperNaming.pretty(once))
        assertEquals("NEET 2024", PaperNaming.pretty("NEET 2024"))
    }

    @Test fun stripsExtensionAndNoise() {
        assertEquals("JEE Main 2023 Shift 1", PaperNaming.pretty("JEE-Main-2023-Shift-1-Question-Paper-with-Answer-Key.pdf"))
    }

    @Test fun keepsYearRangesAndFallsBack() {
        assertEquals("AIPMT 2014-15", PaperNaming.pretty("AIPMT_2014-15.pdf"))
        assertEquals("Solutions", PaperNaming.pretty("Solutions"))
    }

    private fun bank(id: String) = BankEntity(id, "T $id", 1, "NEET", null, "Physics", "PYQS", "c", 10, 0L)

    @Test fun statusDrivesCompletedAndInProgress() {
        val rows = PaperRows.build(
            listOf(bank("a"), bank("b"), bank("c")),
            emptyMap(),
            emptySet(),
            PaperStatus(submitted = setOf("a", "b"), inProgress = mapOf("b" to 7L)),
        )
        assertTrue(rows.first { it.bank.id == "a" }.completed)
        val b = rows.first { it.bank.id == "b" }
        assertFalse(b.completed)
        assertEquals(7L, b.inProgressAttemptId)
        assertNull(rows.first { it.bank.id == "c" }.inProgressAttemptId)
        assertEquals("b", rows.first().bank.id)
    }
}

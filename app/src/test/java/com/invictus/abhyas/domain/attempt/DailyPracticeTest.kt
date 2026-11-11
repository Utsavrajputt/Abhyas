package com.invictus.abhyas.domain.attempt

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyPracticeTest {
    private val day = LocalDate.of(2026, 11, 11)

    @Test fun titleRoundTrips() {
        assertEquals(day, DailyPractice.dayOf(DailyPractice.title(day)))
        assertNull(DailyPractice.dayOf("Quick practice"))
    }

    @Test fun pickIsStableForADayAndIgnoresInputOrder() {
        val ids = (1..100).map { "q$it" }
        val a = DailyPractice.pick(ids, day, 30)
        assertEquals(30, a.size)
        assertEquals(a, DailyPractice.pick(ids.shuffled(), day, 30))
        assert(a != DailyPractice.pick(ids, day.plusDays(1), 30))
    }

    @Test fun pickReturnsEverythingWhenFewerThanCount() {
        assertEquals(3, DailyPractice.pick(listOf("a", "b", "c"), day, 30).size)
    }

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val done = setOf(day, day.minusDays(1), day.minusDays(2), day.minusDays(4))
        assertEquals(3, DailyPractice.streak(done, day))
        // Today not finished yet: yesterday still keeps the streak alive.
        assertEquals(2, DailyPractice.streak(done - day, day))
        assertEquals(0, DailyPractice.streak(setOf(day.minusDays(3)), day))
    }
}

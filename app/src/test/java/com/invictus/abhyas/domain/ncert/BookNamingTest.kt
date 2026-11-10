package com.invictus.abhyas.domain.ncert

import org.junit.Assert.assertEquals
import org.junit.Test

class BookNamingTest {
    @Test fun dropsNcertClassAndNumbersPart() {
        assertEquals("Physics 1", BookNaming.shortTitle("NCERT Physics Class 11 Part 1", "Physics"))
        assertEquals("Chemistry 2", BookNaming.shortTitle("NCERT Chemistry Part II (Class 12)", "Chemistry"))
        assertEquals("Biology", BookNaming.shortTitle("NCERT Biology Class 12", "Biology"))
    }
    @Test fun fallsBackToSubject() = assertEquals("Physics", BookNaming.shortTitle("NCERT Class 11", "Physics"))
    @Test fun labelsAndParts() {
        assertEquals("Class 11", BookNaming.classLabel(11))
        assertEquals("Other", BookNaming.classLabel(null))
        assertEquals(2, BookNaming.part("Physics Part II"))
    }
}

package com.invictus.abhyas.domain.ncert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageMatcherTest {
    private val book = listOf(
        10 to "The rate of change of momentum of a body is directly proportional to the applied force and takes place in the direction of the force. This is the second law of motion.",
        11 to "A body continues in its state of rest or uniform motion in a straight line unless an external unbalanced force acts on it. This property is called inertia.",
        12 to "Friction is the force that opposes the relative motion between two surfaces in contact. Static friction acts while the bodies are at rest, and kinetic friction acts once they slide.",
        13 to "The centre of mass of a system of particles moves as if the entire mass of the system were concentrated there and the total external force were applied at that point.",
        14 to "Angular momentum of a rigid body about a fixed axis is the product of its moment of inertia and its angular velocity.",
        15 to "Table of constants and unit conversion factors used in later sections of the textbook for reference.",
    ).mapIndexed { i, (page, text) -> MatchPassage("b", page, i, text) }
    private val index = PassageIndex(book)

    @Test fun findsThePassageAQuestionIsAbout() {
        val m = PassageMatcher.best(
            "According to the second law of motion the rate of change of momentum of a body is proportional to which quantity: applied force or velocity?",
            index,
        )
        assertNotNull(m)
        assertEquals(10, m!!.passage.page)
        assertNotNull(m.confidence)
    }

    @Test fun findsCentreOfMassPassage() {
        val m = PassageMatcher.best("The centre of mass of a system of particles moves as if the whole mass were concentrated and the external force applied there", index)
        assertEquals(13, m?.passage?.page)
        assertEquals(MatchConfidence.HIGH, m?.confidence)
    }

    @Test fun unrelatedQuestionGetsNoPage() {
        assertNull(PassageMatcher.best("A convex lens of focal length 20 cm forms a real image of an object kept at 30 cm. Find the image distance.", index))
        assertNull(PassageMatcher.best("The efficiency of a Carnot engine working between 400 K and 300 K is", index))
    }

    @Test fun veryShortOrNumericQuestionsGetNoPage() {
        assertNull(PassageMatcher.best("Find 25 + 17", index))
        assertNull(PassageMatcher.best("Force on the body", index))
        assertNull(PassageMatcher.best("", index))
    }

    @Test fun emptyIndexIsSafe() {
        assertNull(PassageMatcher.best("momentum of a body proportional to force applied", PassageIndex(emptyList())))
    }

    @Test fun tokensDropStopWordsNumbersAndSuffixes() {
        val t = PassageMatcher.tokens("The forces acting on 5 bodies are measured quickly")
        assertTrue("force" in t)
        assertTrue("acting" in t)
        assertTrue("the" !in t)
        assertTrue("5" !in t)
    }
}

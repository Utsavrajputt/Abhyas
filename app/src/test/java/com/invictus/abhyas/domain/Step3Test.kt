package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.TestData.opts
import com.invictus.abhyas.domain.TestData.question
import com.invictus.abhyas.domain.attempt.ExamTemplate
import com.invictus.abhyas.domain.attempt.Marking
import com.invictus.abhyas.domain.attempt.PaletteStatus
import com.invictus.abhyas.domain.attempt.PracticeMode
import com.invictus.abhyas.domain.attempt.SummaryCalculator
import com.invictus.abhyas.domain.attempt.TemplateParseResult
import com.invictus.abhyas.domain.attempt.TemplateParser
import com.invictus.abhyas.domain.attempt.TemplateSection
import com.invictus.abhyas.domain.attempt.paletteStatus
import com.invictus.abhyas.domain.model.Response
import com.invictus.abhyas.domain.model.SingleChoicePayload
import com.invictus.abhyas.domain.query.KindFilter
import com.invictus.abhyas.domain.query.QuestionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step3Test {
    private fun parse(json: String) = TemplateParser.parse(json)

    @Test fun validTemplateParses() {
        val r = parse("""{"id":"t","name":"T","exam":"NEET","durationMinutes":60,"marking":{"correct":4,"wrong":1},"sections":[{"title":"P","subject":"Physics","count":10}]}""")
        val t = (r as TemplateParseResult.Ok).template
        assertEquals(10, t.totalQuestions)
        assertEquals(Marking(4.0, 1.0), t.markingFor(t.sections.first()))
    }

    @Test fun sectionMarkingOverridesDefault() {
        val sec = TemplateSection("N", kind = "NUMERICAL", count = 5, marking = Marking(4.0, 0.0))
        val t = ExamTemplate("t", "T", marking = Marking(4.0, 1.0), sections = listOf(sec))
        assertEquals(0.0, t.markingFor(sec)!!.wrong, 0.0)
        assertEquals(1.0, t.markingFor(null)!!.wrong, 0.0)
    }

    @Test fun invalidTemplatesRejected() {
        listOf(
            "not json",
            """{"id":"","name":"T","questionCount":5}""",
            """{"id":"t","name":"T"}""",
            """{"id":"t","name":"T","questionCount":5,"durationMinutes":0}""",
            """{"id":"t","name":"T","exam":"SAT","questionCount":5}""",
            """{"id":"t","name":"T","sections":[{"title":"A","count":0}]}""",
            """{"id":"t","name":"T","sections":[{"title":"A","count":2,"kind":"ESSAY"}]}""",
            """{"id":"t","name":"T","questionCount":5,"marking":{"correct":4,"wrong":-1}}""",
        ).forEach { assertTrue(it, parse(it) is TemplateParseResult.Error) }
    }

    @Test fun plainCountTemplateWorks() {
        val t = (parse("""{"id":"m","name":"Mini","durationMinutes":45,"questionCount":30}""") as TemplateParseResult.Ok).template
        assertEquals(30, t.totalQuestions)
    }

    @Test fun modesSetOwnFlagsAndClearOthers() {
        val f = PracticeMode.PYQ.apply(QuestionFilter(bookmarkedOnly = true, kind = KindFilter.NUMERICAL))
        assertTrue(f.pyqOnly); assertTrue(!f.bookmarkedOnly); assertEquals(KindFilter.ALL, f.kind)
        assertEquals(KindFilter.NUMERICAL, PracticeMode.NUMERICAL.apply(QuestionFilter()).kind)
        assertTrue(PracticeMode.INCORRECT.apply(QuestionFilter()).incorrectOnly)
        val custom = QuestionFilter(pyqOnly = true)
        assertEquals(custom, PracticeMode.CUSTOM.apply(custom))
    }

    @Test fun modeValidation() {
        assertNull(PracticeMode.QUICK.validate(QuestionFilter()))
        assertTrue(PracticeMode.SUBJECT.validate(QuestionFilter()) != null)
        assertNull(PracticeMode.SUBJECT.validate(QuestionFilter(subject = "Physics")))
        assertTrue(PracticeMode.CHAPTER.validate(QuestionFilter(subject = "Physics")) != null)
        assertNull(PracticeMode.TOPIC.validate(QuestionFilter(subject = "P", chapter = "C", topic = "T")))
        assertTrue(!PracticeMode.INCORRECT.avoidRecent && PracticeMode.RANDOM.avoidRecent)
    }

    @Test fun paletteStatuses() {
        assertEquals(PaletteStatus.NOT_VISITED, paletteStatus(false, false, false))
        assertEquals(PaletteStatus.NOT_ANSWERED, paletteStatus(true, false, false))
        assertEquals(PaletteStatus.ANSWERED, paletteStatus(true, true, false))
        assertEquals(PaletteStatus.MARKED, paletteStatus(true, false, true))
        assertEquals(PaletteStatus.ANSWERED_MARKED, paletteStatus(true, true, true))
    }

    @Test fun summaryCountsScoreAndSkipsUnverifiedInMax() {
        val right = question(SingleChoicePayload(opts(), "o1"))
        val wrong = question(SingleChoicePayload(opts(), "o1"), marks = 4.0, negative = 1.0)
        val blank = question(SingleChoicePayload(opts(), "o1"))
        val unverified = question(SingleChoicePayload(opts(), null), verified = false)
        val s = SummaryCalculator.compute(
            listOf(
                right to Response.Choice(setOf("o1")),
                wrong to Response.Choice(setOf("o2")),
                blank to null,
                unverified to Response.Choice(setOf("o1")),
            ),
        )
        assertEquals(3.0, s.score, 0.0)
        assertEquals(12.0, s.maxScore, 0.0)
        assertEquals(listOf(1, 1, 1, 1), listOf(s.correct, s.wrong, s.unattempted, s.unscored))
        assertEquals(0.5, s.accuracy, 1e-9)
    }
}

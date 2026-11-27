package com.puzzlequest.app

import com.puzzlequest.app.data.history.PracticeHistory
import com.puzzlequest.app.data.model.Answer
import com.puzzlequest.app.data.model.Difficulty
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.QuestionSource
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.Theme
import com.puzzlequest.app.engine.AttemptRecord
import com.puzzlequest.app.engine.DailyChallengeEngine
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.Grading
import com.puzzlequest.app.engine.PlayerResponse
import com.puzzlequest.app.engine.SessionOptions
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.update.latestUpdate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random

class EngineAndContentTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun contentDir(): File {
        val candidates = listOf(File("../content"), File("content"))
        return candidates.first { it.isDirectory }
    }

    private fun loadAllContent(): Triple<List<Skill>, List<Theme>, List<Question>> {
        val dir = contentDir()
        val skills = json.decodeFromString<List<Skill>>(File(dir, "skills.json").readText())
        val themes = json.decodeFromString<List<Theme>>(File(dir, "themes.json").readText())
        val qDir = File(dir, "questions")
        val questions = qDir.listFiles { f -> f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.flatMap { file -> json.decodeFromString<List<Question>>(file.readText()) }
            .orEmpty()
        return Triple(skills, themes, questions)
    }

    @Test
    fun allBundledContentFilesDeserializeCleanly() {
        val (skills, themes, questions) = loadAllContent()
        assertEquals(66, skills.size)
        assertEquals(3, themes.size)
        assertEquals(874, questions.size)
        val skillCodes = skills.map { it.code }.toSet()
        for (q in questions) {
            assertTrue("Question ${q.id} has empty skills", q.skills.isNotEmpty())
            assertTrue("Question ${q.id} references unknown skill", q.skills.all { it in skillCodes })
        }
    }

    @Test
    fun gradingHandlesChoiceByValueOrderAndFindTheBug() {
        val choiceQ = Question(
            id = "q-choice",
            source = QuestionSource("original"),
            skills = listOf("MATH-OA-ADD-SUB"),
            prompt = "2 + 2?",
            answer = Answer.Choice(choices = listOf("3", "4", "5"), correctIndex = 1),
            explanation = "2 + 2 = 4.",
            difficulty = Difficulty(band = 2, basis = "author-estimate"),
            reviewStatus = "reviewed",
        )
        assertTrue(Grading.isCorrect(choiceQ, PlayerResponse.Choice(index = 0, value = "4")))
        assertFalse(Grading.isCorrect(choiceQ, PlayerResponse.Choice(index = 1, value = "3")))
        assertTrue(Grading.isCorrect(choiceQ, PlayerResponse.Choice(index = 1, value = null)))

        val orderQ = choiceQ.copy(
            id = "q-order",
            answer = Answer.Order(items = listOf("A", "B", "C"), correctOrder = listOf("C", "A", "B")),
        )
        assertTrue(Grading.isCorrect(orderQ, PlayerResponse.Order(listOf("C", "A", "B"))))
        assertFalse(Grading.isCorrect(orderQ, PlayerResponse.Order(listOf("A", "B", "C"))))

        val bugQ = choiceQ.copy(
            id = "q-bug",
            answer = Answer.FindTheBug(solver = "Bot", steps = listOf("s1", "s2", "s3"), bugIndex = 1, fix = "fixed"),
        )
        assertTrue(Grading.isCorrect(bugQ, PlayerResponse.Step(index = 1)))
        assertFalse(Grading.isCorrect(bugQ, PlayerResponse.Step(index = 2, secondIndex = 1)))
    }

    @Test
    fun sessionSelectorRespectsHumanitiesCapRetriesAndThemes() {
        val (skills, themes, questions) = loadAllContent()
        val reviewed = questions.filter { it.reviewStatus == "reviewed" }
        val skillsByCode = skills.associateBy { it.code }
        val qById = reviewed.associateBy { it.id }

        // Cold-start Humanities cap
        for (seed in 1..25) {
            val rng = Random(seed)
            val plan = SessionSelector.selectSession(
                questions = reviewed,
                skills = skills,
                attempts = emptyList(),
                today = "2026-11-27",
                random = { rng.nextDouble() },
            )
            assertEquals(SessionSelector.SESSION_SIZE, plan.picks.size)
            val humCount = plan.picks.count { pick ->
                val q = qById[pick.questionId]
                skillsByCode[q?.skills?.firstOrNull()]?.strand == "HUMANITIES"
            }
            assertTrue("Seed $seed exceeded Humanities cap: $humCount", humCount <= 2)
        }

        // All themes fill cleanly
        val allThemeSkills = themes.flatMap { it.skills }.toSet()
        for (theme in themes) {
            val rng = Random(42)
            val plan = SessionSelector.selectSession(
                questions = reviewed,
                skills = skills,
                attempts = emptyList(),
                today = "2026-11-27",
                random = { rng.nextDouble() },
                options = SessionOptions(themeSkills = allThemeSkills, onlySkills = theme.skills.toSet()),
            )
            assertEquals(SessionSelector.SESSION_SIZE, plan.picks.size)
        }
    }

    @Test
    fun workingBandStepsDownOnSustainedSlump() {
        val (skills, _, questions) = loadAllContent()
        val skillsByCode = skills.associateBy { it.code }
        val oaBand3 = questions.filter { skillsByCode[it.skills.first()]?.strand == "OPERATIONS_ALGEBRA" && it.difficulty.band == 3 }.take(3)
        val oaBand4 = questions.filter { skillsByCode[it.skills.first()]?.strand == "OPERATIONS_ALGEBRA" && it.difficulty.band == 4 }.take(5)
        assertTrue(oaBand3.size == 3 && oaBand4.size >= 4)

        var t = 1_000L
        val attempts = oaBand3.map { AttemptRecord(it.id, true, t++, "2026-11-20") } +
            oaBand4.map { AttemptRecord(it.id, false, t++, "2026-11-21") }

        val bands = SessionSelector.computeWorkingBands(questions, skillsByCode, attempts)
        assertEquals(3, bands["OPERATIONS_ALGEBRA"])
    }

    @Test
    fun dailyChallengeSelectsValidPuzzlesAcrossDays() {
        val (skills, _, questions) = loadAllContent()
        val reviewed = questions.filter { it.reviewStatus == "reviewed" }
        for (offset in 0 until 30) {
            val day = Dates.addDays("2026-11-01", offset)
            val plan = DailyChallengeEngine.selectDailyChallenge(
                studentId = "student-test-id-$offset",
                questions = reviewed,
                skills = skills,
                attempts = emptyList(),
                pastChallenges = emptyList(),
                today = day,
            )
            assertNotNull("Expected daily challenge for $day", plan)
        }
    }

    @Test
    fun practiceHistoryValidatesAndParsesV1Export() {
        val sample = """
            {
              "format": "puzzle-quest-practice-history",
              "version": 1,
              "learner": { "id": "11111111-1111-4111-8111-111111111111", "name": "Aishik" },
              "sessions": [
                {
                  "id": "22222222-2222-4222-8222-222222222222",
                  "startedAt": "2026-11-24T15:00:00Z",
                  "endedAt": "2026-11-24T15:08:00Z",
                  "attempts": [
                    {
                      "id": "33333333-3333-4333-8333-333333333333",
                      "questionId": "ixl-round10-43",
                      "response": { "type": "choice", "index": 2, "value": "40" },
                      "isCorrect": true,
                      "totalMs": 4200,
                      "listenCount": 0,
                      "answeredAt": "2026-11-24T15:01:00Z",
                      "localDate": "2026-11-24"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsed = PracticeHistory.read(ByteArrayInputStream(sample.toByteArray()))
        assertEquals("Aishik", parsed.learner.name)
        assertEquals(1, parsed.answerCount)
    }

    @Test
    fun latestUpdateParsesNewerStableReleaseAndSkipsSameOrDraft() {
        val sha = "a".repeat(64)
        val body = """
            {
              "draft": false,
              "prerelease": false,
              "tag_name": "v1.2.2",
              "assets": [
                { "name": "PuzzleQuest-v1.2.2.apk", "size": 12345678, "digest": "sha256:$sha" }
              ]
            }
        """.trimIndent()
        val found = latestUpdate(body, installedCode = 10201)
        assertNotNull(found)
        assertEquals("v1.2.2", found!!.tag)
        assertEquals(10202, found.code)
        assertEquals(sha, found.sha256)
        assertNull(latestUpdate(body, installedCode = 10202))
    }
}

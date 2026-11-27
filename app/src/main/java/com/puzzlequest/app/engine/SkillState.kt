package com.puzzlequest.app.engine

import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill

data class AttemptRecord(
    val questionId: String,
    val isCorrect: Boolean,
    val answeredAtMs: Long,
    val localDate: LocalDateString,
)

enum class SkillStatus(val key: String) {
    NEW("new"),
    LEARNING("learning"),
    MASTERED("mastered"),
    REVIEW_DUE("review-due"),
}

data class SkillState(
    val code: String,
    val status: SkillStatus,
    val attempts: Int,
    val recent: List<Boolean>,
    val box: Int,
    val lastPracticed: LocalDateString?,
    val masteredOn: LocalDateString?,
    val nextReview: LocalDateString?,
)

object SkillStateEngine {
    const val MASTERY_WINDOW = 5
    const val MASTERY_CORRECT = 4
    const val MASTERY_MIN_DAYS = 2
    val REVIEW_INTERVALS = listOf(1, 3, 7, 14, 30)

    private data class Entry(val isCorrect: Boolean, val localDate: LocalDateString)

    fun computeSkillStates(
        skills: List<Skill>,
        questionsById: Map<String, Question>,
        attempts: List<AttemptRecord>,
        today: LocalDateString,
    ): Map<String, SkillState> {
        val bySkill = skills.associate { it.code to mutableListOf<Entry>() }
        val ordered = attempts.sortedBy { it.answeredAtMs }
        for (attempt in ordered) {
            val question = questionsById[attempt.questionId] ?: continue
            for (code in question.skills) {
                bySkill[code]?.add(Entry(attempt.isCorrect, attempt.localDate))
            }
        }
        return skills.associate { skill ->
            skill.code to replay(skill.code, bySkill[skill.code].orEmpty(), today)
        }
    }

    private fun replay(code: String, entries: List<Entry>, today: LocalDateString): SkillState {
        val window = ArrayDeque<Entry>()
        var box = 0
        var masteredOn: LocalDateString? = null
        var nextReview: LocalDateString? = null

        for (entry in entries) {
            window.addLast(entry)
            if (window.size > MASTERY_WINDOW) window.removeFirst()

            if (box > 0) {
                if (!entry.isCorrect) {
                    box -= 1
                    if (box == 0) {
                        masteredOn = null
                        nextReview = null
                    } else {
                        nextReview = Dates.addDays(entry.localDate, REVIEW_INTERVALS[box - 1])
                    }
                } else if (nextReview != null && entry.localDate >= nextReview) {
                    box = minOf(box + 1, REVIEW_INTERVALS.size)
                    nextReview = Dates.addDays(entry.localDate, REVIEW_INTERVALS[box - 1])
                }
                continue
            }

            if (meetsMastery(window)) {
                box = 1
                masteredOn = entry.localDate
                nextReview = Dates.addDays(entry.localDate, REVIEW_INTERVALS[0])
            }
        }

        val status = when {
            entries.isEmpty() -> SkillStatus.NEW
            box == 0 -> SkillStatus.LEARNING
            nextReview != null && today >= nextReview -> SkillStatus.REVIEW_DUE
            else -> SkillStatus.MASTERED
        }

        return SkillState(
            code = code,
            status = status,
            attempts = entries.size,
            recent = window.map { it.isCorrect },
            box = box,
            lastPracticed = entries.lastOrNull()?.localDate,
            masteredOn = masteredOn,
            nextReview = nextReview,
        )
    }

    private fun meetsMastery(window: Collection<Entry>): Boolean {
        if (window.size < MASTERY_WINDOW) return false
        val right = window.filter { it.isCorrect }
        return right.size >= MASTERY_CORRECT && right.map { it.localDate }.toSet().size >= MASTERY_MIN_DAYS
    }
}

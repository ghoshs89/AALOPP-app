package com.puzzlequest.app.engine

import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.StrandLabels
import kotlin.math.roundToInt

data class InsightAttempt(
    val questionId: String,
    val isCorrect: Boolean,
    val answeredAtMs: Long,
    val localDate: LocalDateString,
    val confidence: String?,
    val scratchpad: String?,
    val totalMs: Int,
)

data class Tally(val answered: Int, val correct: Int) {
    val percent: Int?
        get() = if (answered == 0) null else ((100.0 * correct) / answered).roundToInt()
}

data class DailyActivityRow(
    val date: LocalDateString,
    val answered: Int,
    val correct: Int,
    val minutes: Int,
)

data class WeeklyAccuracyRow(
    val start: LocalDateString,
    val end: LocalDateString,
    val tally: Tally,
)

data class StrandAccuracyRow(
    val strand: String,
    val tally: Tally,
)

data class ConfidenceCalibrationRow(
    val level: String,
    val tally: Tally,
)

data class EverydayReasoningSplit(
    val everyday: Tally,
    val reasoning: Tally,
)

data class LearnerSummary(
    val lastWeek: Tally,
    val practiceDaysLast14: Int,
    val scratchpadShare: Int?,
    val sureButWrongLastWeek: Int,
)

data class SkillMapNode(
    val code: String,
    val name: String,
    val layer: Int,
    val row: Int,
    val strand: String,
)

data class SkillMapEdge(val from: String, val to: String)

data class SkillMapLayout(
    val nodes: List<SkillMapNode>,
    val edges: List<SkillMapEdge>,
    val layers: Int,
    val rows: Int,
)

object InsightsEngine {
    private fun tally(items: List<InsightAttempt>): Tally =
        Tally(answered = items.size, correct = items.count { it.isCorrect })

    fun dailyActivity(
        attempts: List<InsightAttempt>,
        today: LocalDateString,
        days: Int = 14,
    ): List<DailyActivityRow> = List(days) { i ->
        val date = Dates.addDays(today, i - (days - 1))
        val onDay = attempts.filter { it.localDate == date }
        val t = tally(onDay)
        val minutes = (onDay.sumOf { it.totalMs.toLong() } / 60000.0).roundToInt()
        DailyActivityRow(date = date, answered = t.answered, correct = t.correct, minutes = minutes)
    }

    fun weeklyAccuracy(
        attempts: List<InsightAttempt>,
        today: LocalDateString,
        weeks: Int = 8,
    ): List<WeeklyAccuracyRow> = List(weeks) { i ->
        val w = weeks - 1 - i
        val end = Dates.addDays(today, -7 * w)
        val start = Dates.addDays(end, -6)
        val inWeek = attempts.filter { it.localDate >= start && it.localDate <= end }
        WeeklyAccuracyRow(start = start, end = end, tally = tally(inWeek))
    }

    fun accuracyByStrand(
        attempts: List<InsightAttempt>,
        questionsById: Map<String, Question>,
        skills: List<Skill>,
    ): List<StrandAccuracyRow> {
        val strandOf = skills.associate { it.code to it.strand }
        return StrandLabels.orderedStrands.map { strand ->
            val matched = attempts.filter { a ->
                val q = questionsById[a.questionId]
                q != null && strandOf[q.skills.firstOrNull()] == strand
            }
            StrandAccuracyRow(strand = strand, tally = tally(matched))
        }
    }

    fun everydayVsReasoning(
        attempts: List<InsightAttempt>,
        questionsById: Map<String, Question>,
        skills: List<Skill>,
    ): EverydayReasoningSplit {
        val convention = skills.filter { it.isConvention }.map { it.code }.toSet()
        val knowledge = skills.filter { it.strand == "HUMANITIES" }.map { it.code }.toSet()

        fun isEveryday(a: InsightAttempt): Boolean =
            questionsById[a.questionId]?.skills?.any { it in convention } == true

        fun isKnowledge(a: InsightAttempt): Boolean {
            val sk = questionsById[a.questionId]?.skills ?: return false
            return sk.isNotEmpty() && sk.all { it in knowledge }
        }

        return EverydayReasoningSplit(
            everyday = tally(attempts.filter { isEveryday(it) && !isKnowledge(it) }),
            reasoning = tally(attempts.filter { questionsById.containsKey(it.questionId) && !isEveryday(it) && !isKnowledge(it) }),
        )
    }

    fun confidenceCalibration(attempts: List<InsightAttempt>): List<ConfidenceCalibrationRow> =
        listOf("LOW", "MEDIUM", "HIGH").map { level ->
            ConfidenceCalibrationRow(level = level, tally = tally(attempts.filter { it.confidence == level }))
        }

    fun summarize(attempts: List<InsightAttempt>, today: LocalDateString): LearnerSummary {
        val week = attempts.filter {
            val d = Dates.daysBetween(it.localDate, today)
            d in 0..6
        }
        val fortnightDays = attempts
            .filter { Dates.daysBetween(it.localDate, today) in 0..13 }
            .map { it.localDate }
            .toSet()
        val recorded = attempts.filter { it.scratchpad != null }
        val scratchShare = if (recorded.isEmpty()) {
            null
        } else {
            ((100.0 * recorded.count { it.scratchpad != "NONE" }) / recorded.size).roundToInt()
        }
        return LearnerSummary(
            lastWeek = tally(week),
            practiceDaysLast14 = fortnightDays.size,
            scratchpadShare = scratchShare,
            sureButWrongLastWeek = week.count { it.confidence == "HIGH" && !it.isCorrect },
        )
    }

    fun skillMapLayout(skills: List<Skill>): SkillMapLayout {
        val byCode = skills.associateBy { it.code }
        val depth = mutableMapOf<String, Int>()

        fun depthOf(code: String, seen: MutableSet<String> = mutableSetOf()): Int {
            depth[code]?.let { return it }
            if (!seen.add(code)) return 0
            val prereqs = byCode[code]?.prerequisites?.filter { it in byCode }.orEmpty()
            val d = if (prereqs.isEmpty()) 0 else 1 + prereqs.maxOf { depthOf(it, seen.toMutableSet()) }
            depth[code] = d
            return d
        }

        val layersMap = mutableMapOf<Int, MutableList<Skill>>()
        for (s in skills) {
            val d = depthOf(s.code)
            layersMap.getOrPut(d) { mutableListOf() }.add(s)
        }

        val nodes = mutableListOf<SkillMapNode>()
        for ((layer, list) in layersMap.toSortedMap()) {
            list.sortedWith(
                compareBy<Skill> {
                    val idx = StrandLabels.orderedStrands.indexOf(it.strand)
                    if (idx == -1) 99 else idx
                }.thenBy { it.name },
            ).forEachIndexed { row, s ->
                nodes += SkillMapNode(code = s.code, name = s.name, layer = layer, row = row, strand = s.strand)
            }
        }

        val edges = skills.flatMap { s ->
            s.prerequisites.filter { it in byCode }.map { p -> SkillMapEdge(from = p, to = s.code) }
        }

        return SkillMapLayout(
            nodes = nodes,
            edges = edges,
            layers = layersMap.size.coerceAtLeast(1),
            rows = (layersMap.values.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1),
        )
    }

    /**
     * Multi-category progress over time across daily (e.g. 14 days) or weekly (e.g. 8 weeks) buckets.
     * Groups each attempt by its question's primary skill strand.
     */
    fun categoryProgressOverTime(
        attempts: List<InsightAttempt>,
        questionsById: Map<String, Question>,
        skills: List<Skill>,
        today: LocalDateString,
        weekly: Boolean = false,
        bucketCount: Int = if (weekly) 8 else 14,
    ): CategoryProgressOverTime {
        val strandOf = skills.associate { it.code to it.strand }
        val buckets = List(bucketCount) { i ->
            if (weekly) {
                val w = bucketCount - 1 - i
                val end = Dates.addDays(today, -7 * w)
                val start = Dates.addDays(end, -6)
                CategoryProgressBucket(
                    label = Dates.shortLabel(end),
                    startDate = start,
                    endDate = end,
                )
            } else {
                val date = Dates.addDays(today, i - (bucketCount - 1))
                CategoryProgressBucket(
                    label = Dates.shortLabel(date),
                    startDate = date,
                    endDate = date,
                )
            }
        }

        val tagged = attempts.mapNotNull { a ->
            val q = questionsById[a.questionId] ?: return@mapNotNull null
            val strand = strandOf[q.skills.firstOrNull()] ?: return@mapNotNull null
            strand to a
        }

        val series = StrandLabels.orderedStrands.map { strand ->
            val strandAttempts = tagged.filter { it.first == strand }.map { it.second }
            val tallies = buckets.map { b ->
                val inBucket = strandAttempts.filter { it.localDate >= b.startDate && it.localDate <= b.endDate }
                tally(inBucket)
            }
            CategorySeries(strand = strand, tallies = tallies)
        }

        return CategoryProgressOverTime(buckets = buckets, series = series)
    }
}

data class CategoryProgressBucket(
    val label: String,
    val startDate: LocalDateString,
    val endDate: LocalDateString,
)

data class CategorySeries(
    val strand: String,
    val tallies: List<Tally>,
)

data class CategoryProgressOverTime(
    val buckets: List<CategoryProgressBucket>,
    val series: List<CategorySeries>,
)


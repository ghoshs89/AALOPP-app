package com.puzzlequest.app.engine

import com.puzzlequest.app.data.db.AttemptEntity
import com.puzzlequest.app.data.db.DailyChallengeRecord
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.StrandLabels
import java.time.LocalDate
import kotlin.math.abs

enum class DifficultyAdjustment {
    STARTING,
    STEPPED_UP,
    EASED_DOWN,
    HELD_MAX,
    HELD_MIN,
}

data class DailyChallengePlan(
    val localDate: LocalDateString,
    val strand: String,
    val strandLabel: String,
    val strandEmoji: String,
    val question: Question,
    val targetBand: Int,
    val previousBand: Int?,
    val adjustment: DifficultyAdjustment,
    val adjustmentSummary: String,
    val bonusPoints: Int,
    val completedRecord: DailyChallengeRecord?,
) {
    val isCompletedToday: Boolean get() = completedRecord?.completed == true
}

data class ProgressPointsSummary(
    val totalPoints: Int,
    val regularPoints: Int,
    val dailyBonusPoints: Int,
    val dailyCompletedCount: Int,
    val dailyStreak: Int,
)

/**
 * Selects a unique, curated hand-picked puzzle each calendar day while dynamically
 * adjusting difficulty from the last time the student answered a puzzle of the same kind.
 */
object DailyChallengeEngine {
    const val REGULAR_CORRECT_POINTS = 10
    const val DAILY_BASE_BONUS_POINTS = 25
    const val DAILY_BAND_BONUS_STEP = 5
    const val DAILY_EFFORT_BONUS_POINTS = 15

    private val curatedStrandRotation = listOf(
        "LOGIC_CS",
        "OPERATIONS_ALGEBRA",
        "GEOMETRY_SPATIAL",
        "NUMBER_BASE_TEN",
        "MEASUREMENT_DATA",
        "LANGUAGE",
        "HUMANITIES",
    )

    fun bonusPointsForBand(band: Int): Int =
        DAILY_BASE_BONUS_POINTS + band.coerceIn(1, 5) * DAILY_BAND_BONUS_STEP

    fun strandEmoji(strand: String): String = when (strand) {
        "LOGIC_CS" -> "🧠"
        "OPERATIONS_ALGEBRA" -> "➕"
        "GEOMETRY_SPATIAL" -> "📐"
        "NUMBER_BASE_TEN" -> "🔟"
        "MEASUREMENT_DATA" -> "📏"
        "LANGUAGE" -> "📖"
        "HUMANITIES" -> "🌍"
        else -> "✨"
    }

    fun computeProgressSummary(
        attempts: List<AttemptEntity>,
        dailyChallenges: List<DailyChallengeRecord>,
        today: LocalDateString,
    ): ProgressPointsSummary {
        val regularPoints = attempts.count { it.isCorrect } * REGULAR_CORRECT_POINTS
        val completedDailies = dailyChallenges.filter { it.completed }
        val dailyBonusPoints = completedDailies.sumOf { it.bonusPoints }
        val completedDates = completedDailies.map { it.localDate }.toSet()

        var streak = 0
        var cursor = if (today in completedDates) today else Dates.addDays(today, -1)
        while (cursor in completedDates) {
            streak += 1
            cursor = Dates.addDays(cursor, -1)
        }

        return ProgressPointsSummary(
            totalPoints = regularPoints + dailyBonusPoints,
            regularPoints = regularPoints,
            dailyBonusPoints = dailyBonusPoints,
            dailyCompletedCount = completedDailies.size,
            dailyStreak = streak,
        )
    }

    fun selectDailyChallenge(
        studentId: String,
        questions: List<Question>,
        skills: List<Skill>,
        attempts: List<AttemptRecord>,
        pastChallenges: List<DailyChallengeRecord>,
        today: LocalDateString,
    ): DailyChallengePlan? {
        if (questions.isEmpty()) return null
        val skillByCode = skills.associateBy { it.code }
        val questionsById = questions.associateBy { it.id }

        fun primaryStrand(q: Question): String? =
            q.skills.firstNotNullOfOrNull { skillByCode[it]?.strand }

        val questionsByStrand = questions
            .mapNotNull { q -> primaryStrand(q)?.let { s -> s to q } }
            .groupBy({ it.first }, { it.second })

        val availableStrands = curatedStrandRotation.filter { !questionsByStrand[it].isNullOrEmpty() }
            .ifEmpty { questionsByStrand.keys.sorted() }
        if (availableStrands.isEmpty()) return null

        val existingToday = pastChallenges.firstOrNull { it.localDate == today }
        if (existingToday != null) {
            val savedQuestion = questionsById[existingToday.questionId]
            if (savedQuestion != null) {
                val strand = existingToday.strand
                val priorAttempts = if (existingToday.completedAtMs != null) {
                    attempts.filter { it.answeredAtMs < existingToday.completedAtMs }
                } else {
                    attempts
                }
                val strandPool = questionsByStrand[strand].orEmpty().ifEmpty { listOf(savedQuestion) }
                val adjustmentInfo = computeDifficultyAdjustment(
                    strand = strand,
                    strandPool = strandPool,
                    attempts = priorAttempts,
                    questionsById = questionsById,
                    skillByCode = skillByCode,
                )
                return DailyChallengePlan(
                    localDate = today,
                    strand = strand,
                    strandLabel = StrandLabels.labelFor(strand),
                    strandEmoji = strandEmoji(strand),
                    question = savedQuestion,
                    targetBand = savedQuestion.difficulty.band,
                    previousBand = adjustmentInfo.previousBand,
                    adjustment = adjustmentInfo.adjustment,
                    adjustmentSummary = adjustmentInfo.summary,
                    bonusPoints = bonusPointsForBand(savedQuestion.difficulty.band),
                    completedRecord = existingToday,
                )
            }
        }

        // Deterministic daily rotation across puzzle kinds based on calendar day.
        val epochDay = LocalDate.parse(today).toEpochDay()
        val strandIndex = ((epochDay % availableStrands.size) + availableStrands.size).toInt() % availableStrands.size
        val featuredStrand = availableStrands[strandIndex]
        val strandPool = questionsByStrand[featuredStrand].orEmpty()

        val adjustmentInfo = computeDifficultyAdjustment(
            strand = featuredStrand,
            strandPool = strandPool,
            attempts = attempts,
            questionsById = questionsById,
            skillByCode = skillByCode,
        )

        // Pick closest available band to the adjusted difficulty target.
        val minBandDelta = strandPool.minOf { abs(it.difficulty.band - adjustmentInfo.targetBand) }
        val bandMatchedPool = strandPool.filter { abs(it.difficulty.band - adjustmentInfo.targetBand) == minBandDelta }

        // Exclude previously served daily challenges for uniqueness across days.
        val pastDailyIds = pastChallenges.map { it.questionId }.toSet()
        val uniquePool = bandMatchedPool.filter { it.id !in pastDailyIds }.ifEmpty { bandMatchedPool }

        // Prefer puzzles the student hasn't answered recently.
        val lastAttemptByQuestion = mutableMapOf<String, AttemptRecord>()
        for (a in attempts.sortedBy { it.answeredAtMs }) {
            lastAttemptByQuestion[a.questionId] = a
        }
        val freshPool = uniquePool.filter { lastAttemptByQuestion[it.id] == null }
            .ifEmpty {
                val oldestTime = uniquePool.minOf { lastAttemptByQuestion[it.id]?.answeredAtMs ?: Long.MIN_VALUE }
                uniquePool.filter { (lastAttemptByQuestion[it.id]?.answeredAtMs ?: Long.MIN_VALUE) <= oldestTime + 86_400_000L }
            }

        // Rank hand-picked quality: contest / rich-visual / hint-equipped / fun-fact items rise to the top.
        val scored = freshPool.map { q ->
            var quality = 0
            if (q.visual != null) quality += 4
            if (q.hint != null) quality += 2
            if (!q.didYouKnow.isNullOrBlank()) quality += 2
            if (q.source.kind.equals("kangaroo", ignoreCase = true) ||
                q.source.kind.equals("bebras", ignoreCase = true)
            ) {
                quality += 3
            }
            if (q.passage != null) quality += 1
            q to quality
        }.sortedWith(
            compareByDescending<Pair<Question, Int>> { it.second }
                .thenBy { it.first.id },
        )

        // Select deterministically among top-tier hand-picked candidates for this calendar day.
        val bestQuality = scored.first().second
        val topTier = scored.filter { it.second >= bestQuality - 2 }.map { it.first }
        val dailyHash = abs((epochDay * 1_103_515_245L + studentId.hashCode() * 31L + featuredStrand.hashCode()).toInt())
        val pickedQuestion = topTier[dailyHash % topTier.size]

        val resolvedBand = pickedQuestion.difficulty.band
        val resolvedSummary = if (resolvedBand != adjustmentInfo.targetBand) {
            "Level $resolvedBand · Adjusted for ${StrandLabels.labelFor(featuredStrand)}"
        } else {
            adjustmentInfo.summary
        }

        return DailyChallengePlan(
            localDate = today,
            strand = featuredStrand,
            strandLabel = StrandLabels.labelFor(featuredStrand),
            strandEmoji = strandEmoji(featuredStrand),
            question = pickedQuestion,
            targetBand = resolvedBand,
            previousBand = adjustmentInfo.previousBand,
            adjustment = adjustmentInfo.adjustment,
            adjustmentSummary = resolvedSummary,
            bonusPoints = bonusPointsForBand(resolvedBand),
            completedRecord = null,
        )
    }

    private data class AdjustmentResult(
        val targetBand: Int,
        val previousBand: Int?,
        val adjustment: DifficultyAdjustment,
        val summary: String,
    )

    private fun computeDifficultyAdjustment(
        strand: String,
        strandPool: List<Question>,
        attempts: List<AttemptRecord>,
        questionsById: Map<String, Question>,
        skillByCode: Map<String, Skill>,
    ): AdjustmentResult {
        val minBand = strandPool.minOfOrNull { it.difficulty.band } ?: 1
        val maxBand = strandPool.maxOfOrNull { it.difficulty.band } ?: 5

        // Find the most recent attempt on any puzzle of this same kind (strand).
        val lastSameKindAttempt = attempts
            .asSequence()
            .filter { attempt ->
                val q = questionsById[attempt.questionId] ?: return@filter false
                q.skills.any { skillByCode[it]?.strand == strand }
            }
            .maxByOrNull { it.answeredAtMs }

        if (lastSameKindAttempt == null) {
            val startBand = SessionSelector.startingBandFor(strand).coerceIn(minBand, maxBand)
            return AdjustmentResult(
                targetBand = startBand,
                previousBand = null,
                adjustment = DifficultyAdjustment.STARTING,
                summary = "Level $startBand · Starting level",
            )
        }

        val lastBand = questionsById[lastSameKindAttempt.questionId]?.difficulty?.band
            ?: SessionSelector.startingBandFor(strand)

        return if (lastSameKindAttempt.isCorrect) {
            val nextBand = (lastBand + 1).coerceAtMost(maxBand)
            if (nextBand > lastBand) {
                AdjustmentResult(
                    targetBand = nextBand,
                    previousBand = lastBand,
                    adjustment = DifficultyAdjustment.STEPPED_UP,
                    summary = "Stepped up to Level $nextBand from Level $lastBand",
                )
            } else {
                AdjustmentResult(
                    targetBand = nextBand,
                    previousBand = lastBand,
                    adjustment = DifficultyAdjustment.HELD_MAX,
                    summary = "Holding strong at top Level $nextBand",
                )
            }
        } else {
            val nextBand = (lastBand - 1).coerceAtLeast(minBand)
            if (nextBand < lastBand) {
                AdjustmentResult(
                    targetBand = nextBand,
                    previousBand = lastBand,
                    adjustment = DifficultyAdjustment.EASED_DOWN,
                    summary = "Adjusted to Level $nextBand from Level $lastBand",
                )
            } else {
                AdjustmentResult(
                    targetBand = nextBand,
                    previousBand = lastBand,
                    adjustment = DifficultyAdjustment.HELD_MIN,
                    summary = "Staying at Level $nextBand to practice",
                )
            }
        }
    }
}

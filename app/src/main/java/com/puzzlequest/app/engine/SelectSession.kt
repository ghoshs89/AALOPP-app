package com.puzzlequest.app.engine

import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill
import kotlin.random.Random

data class SessionPick(val questionId: String, val reason: String)

data class SessionPlan(
    val picks: List<SessionPick>,
    val workingBands: Map<String, Int>,
)

data class SessionOptions(
    val themeSkills: Set<String>? = null,
    val onlySkills: Set<String>? = null,
)

object SessionSelector {
    const val SESSION_SIZE = 8
    const val THEMED_CORE_ITEMS = 3
    const val WORKING_BAND_WINDOW = 8

    private const val MIN_CONVENTION_ITEMS = 3
    private const val MIN_NEW_ITEMS = 1
    private const val MIN_THEME_ITEMS = 1
    private const val MAX_HUMANITIES_ITEMS = 2
    private const val STRETCH_SLOTS = 2
    private const val MAX_REVIEWS = 4
    private const val MAX_RETRIES = 2
    private const val MAX_PER_SKILL = 2
    private const val REPEAT_COOLDOWN_DAYS = 14
    private const val STARTING_BAND = 2

    private val strandStartingBand = mapOf(
        "OPERATIONS_ALGEBRA" to 3,
        "NUMBER_BASE_TEN" to 3,
    )

    fun startingBandFor(strand: String): Int = strandStartingBand[strand] ?: STARTING_BAND

    private data class Quotas(val convention: Int, val newSkill: Int, val theme: Int)

    private enum class Category { REVIEW, RETRY, PRACTICE, NEW, MAINTAIN }

    private data class Candidate(
        val question: Question,
        val skill: Skill,
        val state: SkillState,
        val category: Category,
        val score: Double,
        val isStretch: Boolean,
        val isConvention: Boolean,
        val blocked: Boolean,
        val reason: String,
    )

    fun selectSession(
        questions: List<Question>,
        skills: List<Skill>,
        attempts: List<AttemptRecord>,
        today: LocalDateString,
        random: () -> Double = { Random.nextDouble() },
        size: Int = SESSION_SIZE,
        options: SessionOptions = SessionOptions(),
    ): SessionPlan {
        val themeSkills = options.themeSkills
        val onlySkills = options.onlySkills
        val skillByCode = skills.associateBy { it.code }
        val questionsById = questions.associateBy { it.id }
        val states = SkillStateEngine.computeSkillStates(skills, questionsById, attempts, today)
        val workingBands = computeWorkingBands(questions, skillByCode, attempts)

        val lastByQuestion = mutableMapOf<String, AttemptRecord>()
        for (a in attempts.sortedBy { it.answeredAtMs }) {
            lastByQuestion[a.questionId] = a
        }

        val skillJitter = mutableMapOf<String, Double>()
        fun jitterFor(code: String): Double = skillJitter.getOrPut(code) { random() * 8.0 }

        val skillMaxBand = mutableMapOf<String, Int>()
        for (q in questions) {
            for (code in q.skills) {
                skillMaxBand[code] = maxOf(skillMaxBand[code] ?: 1, q.difficulty.band)
            }
        }

        val candidates = questions.mapNotNull { question ->
            val skillOptions = question.skills.mapNotNull { code ->
                val sk = skillByCode[code]
                val st = states[code]
                if (sk != null && st != null) sk to st else null
            }
            if (skillOptions.isEmpty()) return@mapNotNull null

            val (skill, state) = skillOptions.maxBy { (_, st) -> urgency(st, skillByCode, states) }
            val working = workingBands[skill.strand] ?: startingBandFor(skill.strand)
            val targetBand = minOf(working, skillMaxBand[skill.code] ?: working)
            val band = question.difficulty.band
            val last = lastByQuestion[question.id]
            val isConvention = question.skills.any { skillByCode[it]?.isConvention == true }

            val category: Category
            var score: Double
            when {
                last != null && !last.isCorrect && last.localDate < today -> {
                    category = Category.RETRY
                    score = 90.0
                }
                state.status == SkillStatus.REVIEW_DUE -> {
                    category = Category.REVIEW
                    score = 100.0
                }
                state.status == SkillStatus.LEARNING -> {
                    category = Category.PRACTICE
                    val misses = state.recent.count { !it }
                    val nearMastery = if (state.recent.size in 2..4 && misses <= 1) 6.0 else 0.0
                    score = 70.0 + misses * 3.0 + nearMastery
                }
                state.status == SkillStatus.NEW -> {
                    val ready = skill.prerequisites.all { (states[it]?.attempts ?: 0) > 0 }
                    category = Category.NEW
                    score = if (ready) 50.0 else 20.0
                }
                else -> {
                    category = Category.MAINTAIN
                    score = 10.0
                }
            }

            val isStretch = band == working + 1
            when {
                band == targetBand -> score += 10.0
                band == targetBand - 1 -> score += 5.0
                band < targetBand - 1 -> score -= 15.0
                band > working + 1 -> score -= 30.0
            }
            score += jitterFor(skill.code) + random() * 0.5

            val answeredToday = last?.localDate == today
            val recentlyRight = last?.isCorrect == true && Dates.daysBetween(last.localDate, today) < REPEAT_COOLDOWN_DAYS
            val blocked = answeredToday || recentlyRight

            Candidate(
                question = question,
                skill = skill,
                state = state,
                category = category,
                score = score,
                isStretch = isStretch,
                isConvention = isConvention,
                blocked = blocked,
                reason = describe(category, skill, state, last, isStretch, band),
            )
        }

        val picked = mutableListOf<Candidate>()
        val perSkill = mutableMapOf<String, Int>()

        fun take(c: Candidate, reasonOverride: String? = null) {
            picked += if (reasonOverride != null) c.copy(reason = reasonOverride) else c
            for (code in c.question.skills) {
                perSkill[code] = (perSkill[code] ?: 0) + 1
            }
        }

        fun isThemed(c: Candidate): Boolean =
            themeSkills != null && c.question.skills.any { it in themeSkills }

        fun isHumanities(c: Candidate): Boolean =
            skillByCode[c.question.skills.firstOrNull()]?.strand == "HUMANITIES"

        fun reviewsFull(): Boolean = picked.count { it.category == Category.REVIEW } >= MAX_REVIEWS
        fun retriesFull(): Boolean = picked.count { it.category == Category.RETRY } >= MAX_RETRIES
        fun humanitiesFull(): Boolean = onlySkills == null && picked.count(::isHumanities) >= MAX_HUMANITIES_ITEMS

        fun available(c: Candidate, allowBlocked: Boolean = false, ignoreCaps: Boolean = false): Boolean {
            if (picked.any { it.question.id == c.question.id }) return false
            if (!allowBlocked && c.blocked) return false
            if (!allowBlocked && c.question.topic != null && picked.any { it.question.topic == c.question.topic }) return false
            if (!ignoreCaps) {
                if (c.category == Category.REVIEW && reviewsFull()) return false
                if (c.category == Category.RETRY && retriesFull()) return false
                if (isHumanities(c) && humanitiesFull()) return false
                if (c.question.skills.any { (perSkill[it] ?: 0) >= MAX_PER_SKILL }) return false
            }
            return true
        }

        val byScore = candidates.sortedByDescending { it.score }
        val reviewBacklog = states.values.count { it.status == SkillStatus.REVIEW_DUE }
        val introducing = reviewBacklog <= MAX_REVIEWS * 2

        fun fill(pool: List<Candidate>, target: Int, quotas: Quotas) {
            val start = picked.size
            val slots = target - start
            fun mine() = picked.subList(start, picked.size)
            fun hasNewSkill(c: Candidate) = c.question.skills.any { states[it]?.status == SkillStatus.NEW }
            fun conventionNeeded() = maxOf(0, minOf(quotas.convention, slots) - mine().count { it.isConvention })
            fun themeNeeded() = maxOf(0, minOf(quotas.theme, slots) - mine().count(::isThemed))
            fun newNeeded() = maxOf(0, minOf(quotas.newSkill, slots) - mine().count(::hasNewSkill))
            fun roomForExtra() = picked.size < target - conventionNeeded() - newNeeded() - themeNeeded()

            // 1. Retries then due reviews
            for (c in pool.filter { it.category == Category.RETRY }) {
                if (picked.count { it.category == Category.RETRY } >= MAX_RETRIES || picked.size >= target) break
                if (!c.isConvention && !roomForExtra()) continue
                if (available(c)) take(c)
            }
            for (c in pool.filter { it.category == Category.REVIEW }) {
                if (reviewsFull() || picked.size >= target) break
                if (!c.isConvention && !roomForExtra()) continue
                if (available(c) && picked.none { it.skill.code == c.skill.code }) take(c)
            }

            // 2. New skill
            val newCandidates = pool.filter { it.category == Category.NEW } +
                pool.filter { it.category != Category.NEW && hasNewSkill(it) }
            for (c in newCandidates) {
                if (newNeeded() == 0 || picked.size >= target - conventionNeeded()) break
                if (available(c)) take(c)
            }

            // 3. Themed slot
            for (c in pool.filter(::isThemed)) {
                if (themeNeeded() == 0 || picked.size >= target - conventionNeeded()) break
                if (available(c)) take(c)
            }

            // 4. Stretch slots
            fun reserved() = conventionNeeded() + newNeeded() + themeNeeded()
            val stretchSlots = minOf(STRETCH_SLOTS, maxOf(0, target - picked.size - reserved()))
            for (c in pool.filter { it.isStretch }) {
                if (picked.count { it.isStretch } >= stretchSlots || picked.size >= target - reserved()) break
                if (available(c)) take(c)
            }

            // 5. Applied-convention quota
            val passes = listOf(false to false, false to true, true to true)
            for ((allowBlocked, ignoreCaps) in passes) {
                for (c in pool.filter { it.isConvention }) {
                    if (conventionNeeded() == 0 || picked.size >= target) break
                    if (available(c, allowBlocked, ignoreCaps)) {
                        take(c, if (c.category == Category.MAINTAIN) "Everyday skills mix: ${c.skill.name}" else null)
                    }
                }
            }

            // 6. Remaining by priority
            for ((allowBlocked, ignoreCaps) in passes) {
                for (c in pool) {
                    if (picked.size >= target) break
                    if (available(c, allowBlocked, ignoreCaps)) {
                        take(c, if (c.blocked) "Extra practice (pool is small): ${c.skill.name}" else null)
                    }
                }
            }
        }

        val newSkillQuota = if (introducing) MIN_NEW_ITEMS else 0
        if (onlySkills != null) {
            fun inTheme(c: Candidate) = c.question.skills.any { it in onlySkills }
            fill(byScore.filter { !inTheme(it) && !isThemed(it) }, minOf(THEMED_CORE_ITEMS, size), Quotas(1, 0, 0))
            fill(byScore.filter(::inTheme), size, Quotas(0, newSkillQuota, 0))
        } else {
            fill(
                byScore,
                size,
                Quotas(
                    convention = MIN_CONVENTION_ITEMS,
                    newSkill = newSkillQuota,
                    theme = if (themeSkills != null) MIN_THEME_ITEMS else 0,
                ),
            )
        }

        val ordered = picked.sortedWith(
            compareBy<Candidate> { if (it.isStretch) 1 else 0 }
                .thenBy { it.question.difficulty.band },
        )
        return SessionPlan(
            picks = ordered.map { SessionPick(it.question.id, it.reason) },
            workingBands = workingBands,
        )
    }

    private fun urgency(
        state: SkillState,
        skills: Map<String, Skill>,
        states: Map<String, SkillState>,
    ): Int = when (state.status) {
        SkillStatus.REVIEW_DUE -> 4
        SkillStatus.LEARNING -> 3
        SkillStatus.NEW -> {
            val prereqs = skills[state.code]?.prerequisites.orEmpty()
            if (prereqs.all { (states[it]?.attempts ?: 0) > 0 }) 2 else 1
        }
        SkillStatus.MASTERED -> 0
    }

    private fun describe(
        category: Category,
        skill: Skill,
        state: SkillState,
        last: AttemptRecord?,
        isStretch: Boolean,
        band: Int,
    ): String {
        val right = state.recent.count { it }
        val base = when (category) {
            Category.REVIEW -> "Review: ${skill.name} (mastered, review was due ${state.nextReview})"
            Category.RETRY -> "Try again: missed this question on ${last?.localDate}"
            Category.PRACTICE -> "Practice: ${skill.name} ($right of last ${state.recent.size} right)"
            Category.NEW -> "New skill: ${skill.name}"
            Category.MAINTAIN -> "Keep it fresh: ${skill.name} (mastered)"
        }
        return if (isStretch) "$base. Stretch question, level $band" else base
    }

    fun computeWorkingBands(
        questions: List<Question>,
        skills: Map<String, Skill>,
        attempts: List<AttemptRecord>,
    ): Map<String, Int> {
        val questionsById = questions.associateBy { it.id }
        val history = mutableMapOf<String, MutableList<Boolean>>()
        for (a in attempts.sortedBy { it.answeredAtMs }) {
            val q = questionsById[a.questionId] ?: continue
            val strand = skills[q.skills.firstOrNull()]?.strand ?: continue
            val key = "$strand|${q.difficulty.band}"
            history.getOrPut(key) { mutableListOf() }.add(a.isCorrect)
        }

        val strands = skills.values.map { it.strand }.toSet()
        val result = mutableMapOf<String, Int>()
        for (strand in strands) {
            var passed = 0
            for (band in 1..5) {
                val recent = history["$strand|$band"].orEmpty().takeLast(WORKING_BAND_WINDOW)
                val right = recent.count { it }
                if (recent.size >= 3 && right.toDouble() / recent.size >= 2.0 / 3.0) {
                    passed = band
                }
            }
            var working = minOf(5, maxOf(startingBandFor(strand), passed + 1))
            val currentRecent = history["$strand|$working"].orEmpty().takeLast(WORKING_BAND_WINDOW)
            if (currentRecent.size >= 3 && currentRecent.count { it }.toDouble() / currentRecent.size < 1.0 / 3.0) {
                working = maxOf(1, working - 1)
            }
            result[strand] = working
        }
        return result
    }
}

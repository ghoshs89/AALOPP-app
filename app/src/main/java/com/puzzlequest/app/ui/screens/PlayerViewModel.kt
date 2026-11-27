package com.puzzlequest.app.ui.screens

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.puzzlequest.app.data.content.ContentBank
import com.puzzlequest.app.data.db.AttemptEntity
import com.puzzlequest.app.data.db.DailyChallengeRecord
import com.puzzlequest.app.data.db.StudentRecord
import com.puzzlequest.app.data.model.Answer
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.repository.LearnerRepository
import com.puzzlequest.app.engine.AttemptRecord
import com.puzzlequest.app.engine.DailyChallengeEngine
import com.puzzlequest.app.engine.DailyChallengePlan
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.Grading
import com.puzzlequest.app.engine.PlayerResponse
import com.puzzlequest.app.engine.SessionOptions
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.engine.SpeechFormatter
import com.puzzlequest.app.engine.SpeechPart
import com.puzzlequest.app.engine.TtsController
import com.puzzlequest.app.engine.shouldSuggestBreak
import com.puzzlequest.app.ui.components.ConfettiIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PlayerPhase { LOADING, READY, LEARNING, ANSWERING, FEEDBACK, DONE }

data class FeedbackResult(val correct: Boolean, val foundWithNudge: Boolean)

data class HeardSpeech(val text: String, val matched: Boolean)

data class PlayerUiState(
    val student: StudentRecord? = null,
    val phase: PlayerPhase = PlayerPhase.LOADING,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val selectedChoice: Int? = null,
    val firstBugTap: Int? = null,
    val digits: String = "",
    val placedOrder: List<String> = emptyList(),
    val showHint: Boolean = false,
    val result: FeedbackResult? = null,
    val showExplanation: Boolean = false,
    val offerBreak: Boolean = false,
    val scratchOpen: Boolean = false,
    val scratchStrokes: Int = 0,
    val usingPaper: Boolean = false,
    val heard: HeardSpeech? = null,
    val voiceOn: Boolean = true,
    val confettiBurstId: Int = 0,
    val confettiIntensity: ConfettiIntensity = ConfettiIntensity.PUZZLE,
    val isDailyChallenge: Boolean = false,
    val dailyChallengePlan: DailyChallengePlan? = null,
    val dailyBonusAwarded: Int? = null,
    val totalProgressPoints: Int = 0,
    val dailyStreak: Int = 0,
) {
    val currentQuestion: Question? get() = questions.getOrNull(index)

    val currentResponse: PlayerResponse?
        get() {
            val q = currentQuestion ?: return null
            return when (val ans = q.answer) {
                is Answer.Numeric -> digits.toDoubleOrNull()?.let { PlayerResponse.Numeric(it) }
                is Answer.Order -> if (placedOrder.size == ans.items.size) PlayerResponse.Order(placedOrder) else null
                is Answer.Choice -> selectedChoice?.let { idx ->
                    PlayerResponse.Choice(index = idx, value = ans.choices.getOrNull(idx))
                }
                is Answer.FindTheBug -> selectedChoice?.let { idx ->
                    if (firstBugTap == null) {
                        PlayerResponse.Step(index = idx)
                    } else {
                        PlayerResponse.Step(index = firstBugTap, secondIndex = idx)
                    }
                }
            }
        }
}

class PlayerViewModel(
    private val studentId: String,
    private val themeId: String?,
    private val categoryKey: String? = null,
    private val isDailyChallenge: Boolean = false,
    private val contentBank: ContentBank,
    private val repository: LearnerRepository,
    private val tts: TtsController,
) : ViewModel() {

    companion object {
        // Silent 12-minute session limit (no visible timers).
        private const val SESSION_LIMIT_MS = 12L * 60L * 1000L
        const val NUDGE = "Not quite. The first mistake happened earlier. Look above that step."
        const val FOUND_WITH_NUDGE = "You found it! Next time, start checking from the top."
    }

    private val _uiState = MutableStateFlow(PlayerUiState(voiceOn = repository.voiceOn.value))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var reasons: Map<String, String> = emptyMap()
    private var seenIds: Set<String> = emptySet()
    private var sessionId: String = ""
    private var sessionStartedAtMs: Long = 0L
    private var sessionStartedClockMs: Long = 0L
    private var sessionActive: Boolean = false

    private var questionShownClockMs: Long = 0L
    private var firstActionMs: Int? = null
    private var listenCount: Int = 0

    private val sessionResults = mutableListOf<Boolean>()
    private var breakSuggested = false

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val students = repository.students.value.ifEmpty {
                repository.refreshStudents()
                repository.students.value
            }
            val student = students.firstOrNull { it.id == studentId } ?: students.firstOrNull()
            val history = if (student != null) repository.getAttempts(student.id) else emptyList()
            val pastChallenges = if (student != null) repository.getDailyChallenges(student.id) else emptyList()
            val today = Dates.today()
            val progressSummary = DailyChallengeEngine.computeProgressSummary(history, pastChallenges, today)
            val attemptRecords = history.map {
                AttemptRecord(
                    questionId = it.questionId,
                    isCorrect = it.isCorrect,
                    answeredAtMs = it.answeredAtMs,
                    localDate = it.localDate,
                )
            }
            seenIds = history.map { it.questionId }.toSet()

            if (isDailyChallenge && student != null) {
                val dailyPlan = DailyChallengeEngine.selectDailyChallenge(
                    studentId = student.id,
                    questions = contentBank.reviewedQuestions,
                    skills = contentBank.skills,
                    attempts = attemptRecords,
                    pastChallenges = pastChallenges,
                    today = today,
                )
                reasons = if (dailyPlan != null) {
                    mapOf(dailyPlan.question.id to "Daily challenge (${dailyPlan.adjustmentSummary})")
                } else {
                    emptyMap()
                }
                _uiState.update {
                    it.copy(
                        student = student,
                        questions = listOfNotNull(dailyPlan?.question),
                        phase = PlayerPhase.READY,
                        voiceOn = repository.voiceOn.value,
                        isDailyChallenge = true,
                        dailyChallengePlan = dailyPlan,
                        dailyBonusAwarded = null,
                        totalProgressPoints = progressSummary.totalPoints,
                        dailyStreak = progressSummary.dailyStreak,
                    )
                }
                return@launch
            }

            val chosenTheme = themeId?.let { id -> contentBank.themes.firstOrNull { it.id == id } }
            val categorySkills: Set<String>? = when {
                chosenTheme != null -> chosenTheme.skills.toSet()
                categoryKey != null && categoryKey.startsWith("domain:") -> {
                    val domain = categoryKey.removePrefix("domain:")
                    contentBank.skills.filter { it.domain == domain }.map { it.code }.toSet()
                }
                categoryKey != null && categoryKey.startsWith("strand:") -> {
                    val strand = categoryKey.removePrefix("strand:")
                    contentBank.skills.filter { it.strand == strand }.map { it.code }.toSet()
                }
                categoryKey != null && categoryKey.startsWith("skill:") -> {
                    setOf(categoryKey.removePrefix("skill:"))
                }
                else -> null
            }
            val plan = SessionSelector.selectSession(
                questions = contentBank.reviewedQuestions,
                skills = contentBank.skills,
                attempts = attemptRecords,
                today = today,
                options = SessionOptions(
                    themeSkills = contentBank.themes.flatMap { it.skills }.toSet(),
                    onlySkills = categorySkills?.takeIf { it.isNotEmpty() },
                    pureCategory = chosenTheme == null && categorySkills != null,
                ),
            )
            reasons = plan.picks.associate { it.questionId to it.reason }
            val pickedQuestions = plan.picks.mapNotNull { contentBank.questionsById[it.questionId] }

            _uiState.update {
                it.copy(
                    student = student,
                    questions = pickedQuestions,
                    phase = PlayerPhase.READY,
                    voiceOn = repository.voiceOn.value,
                    isDailyChallenge = false,
                    dailyChallengePlan = null,
                    totalProgressPoints = progressSummary.totalPoints,
                    dailyStreak = progressSummary.dailyStreak,
                )
            }
        }
    }

    private fun autoSpeak(parts: List<SpeechPart>) {
        if (_uiState.value.voiceOn) tts.speakParts(parts)
    }

    private fun autoSpeakText(text: String) {
        if (_uiState.value.voiceOn) tts.speak(text)
    }

    fun toggleVoice() {
        val next = !_uiState.value.voiceOn
        repository.setVoiceOn(next)
        _uiState.update { it.copy(voiceOn = next) }
        if (!next) {
            tts.stop()
        } else {
            val state = _uiState.value
            val q = state.currentQuestion ?: return
            when (state.phase) {
                PlayerPhase.LEARNING -> if (q.teach != null) tts.speakParts(SpeechFormatter.teachSpeechParts(q))
                PlayerPhase.ANSWERING -> tts.speakParts(SpeechFormatter.questionSpeechParts(q))
                else -> Unit
            }
        }
    }

    fun startSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStartedAtMs = System.currentTimeMillis()
        sessionStartedClockMs = SystemClock.elapsedRealtime()
        sessionActive = true
        sessionResults.clear()
        breakSuggested = false
        showQuestion(0)
    }

    private fun showQuestion(i: Int) {
        val q = _uiState.value.questions.getOrNull(i) ?: return
        val teaching = q.teach != null && q.id !in seenIds
        questionShownClockMs = SystemClock.elapsedRealtime()
        firstActionMs = null
        listenCount = 0

        _uiState.update {
            it.copy(
                index = i,
                phase = if (teaching) PlayerPhase.LEARNING else PlayerPhase.ANSWERING,
                selectedChoice = null,
                firstBugTap = null,
                digits = "",
                placedOrder = emptyList(),
                showHint = false,
                result = null,
                showExplanation = false,
                offerBreak = false,
                scratchOpen = false,
                scratchStrokes = 0,
                usingPaper = false,
                heard = null,
            )
        }
        if (teaching) {
            autoSpeak(SpeechFormatter.teachSpeechParts(q))
        } else {
            autoSpeak(SpeechFormatter.questionSpeechParts(q))
        }
    }

    fun beginQuestionAfterTeach() {
        tts.stop()
        questionShownClockMs = SystemClock.elapsedRealtime()
        firstActionMs = null
        listenCount = 0
        _uiState.update { it.copy(phase = PlayerPhase.ANSWERING) }
        _uiState.value.currentQuestion?.let { autoSpeak(SpeechFormatter.questionSpeechParts(it)) }
    }

    private fun markAction() {
        if (firstActionMs == null) {
            firstActionMs = (SystemClock.elapsedRealtime() - questionShownClockMs).toInt().coerceAtLeast(0)
        }
    }

    fun readQuestionAloud() {
        val q = _uiState.value.currentQuestion ?: return
        listenCount += 1
        tts.speakParts(SpeechFormatter.questionSpeechParts(q))
    }

    fun hearForeignPrompt() {
        val listen = _uiState.value.currentQuestion?.listen ?: return
        listenCount += 1
        tts.speak(listen.text, listen.lang)
    }

    fun selectChoice(index: Int) {
        markAction()
        _uiState.update { it.copy(selectedChoice = index) }
    }

    fun onDigitKey(key: String) {
        markAction()
        _uiState.update { state ->
            val current = state.digits
            val next = when {
                key == "⌫" -> current.dropLast(1)
                current.length >= 4 -> current
                current == "0" -> key
                else -> current + key
            }
            state.copy(digits = next)
        }
    }

    fun placeOrderItem(item: String) {
        val ans = _uiState.value.currentQuestion?.answer as? Answer.Order ?: return
        val placed = _uiState.value.placedOrder
        if (item in placed || placed.size >= ans.items.size) return
        markAction()
        _uiState.update { it.copy(placedOrder = it.placedOrder + item) }
    }

    fun removeOrderAt(slotIdx: Int) {
        val placed = _uiState.value.placedOrder
        if (slotIdx !in placed.indices) return
        markAction()
        _uiState.update { state ->
            state.copy(placedOrder = state.placedOrder.filterIndexed { i, _ -> i != slotIdx })
        }
    }

    fun clearOrder() {
        markAction()
        _uiState.update { it.copy(placedOrder = emptyList()) }
    }

    fun toggleScratchpad() {
        _uiState.update { it.copy(scratchOpen = !it.scratchOpen) }
    }

    fun toggleUsingPaper() {
        _uiState.update { it.copy(usingPaper = !it.usingPaper) }
    }

    fun updateScratchStrokes(count: Int) {
        _uiState.update { it.copy(scratchStrokes = count) }
    }

    fun revealHint() {
        val hint = _uiState.value.currentQuestion?.hint ?: return
        val wasShown = _uiState.value.showHint
        _uiState.update { it.copy(showHint = true) }
        if (!wasShown) {
            autoSpeakText("Hint: ${SpeechFormatter.speakable(hint)}")
        }
    }

    fun onSpokenAlternatives(alternatives: List<String>) {
        val ans = _uiState.value.currentQuestion?.answer as? Answer.Choice ?: return
        val match = SpeechFormatter.matchSpokenChoice(alternatives, ans.choices)
        if (match != null) {
            markAction()
            _uiState.update { it.copy(selectedChoice = match, heard = HeardSpeech(alternatives.firstOrNull().orEmpty(), true)) }
        } else {
            _uiState.update { it.copy(heard = HeardSpeech(alternatives.firstOrNull().orEmpty(), false)) }
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val q = state.currentQuestion ?: return
        val resp = state.currentResponse ?: return
        tts.stop()

        // Find the Bug: nudge once if child tapped a step after the first mistake.
        if (q.answer is Answer.FindTheBug && resp is PlayerResponse.Step && state.firstBugTap == null && resp.index > q.answer.bugIndex) {
            _uiState.update { it.copy(firstBugTap = resp.index, selectedChoice = null) }
            autoSpeakText(NUDGE)
            return
        }

        val correct = Grading.isCorrect(q, resp)
        val foundWithNudge = q.answer is Answer.FindTheBug &&
            resp is PlayerResponse.Step &&
            resp.secondIndex == q.answer.bugIndex

        sessionResults += (correct || foundWithNudge)
        val shouldBreak = state.index + 1 < state.questions.size && shouldSuggestBreak(sessionResults, breakSuggested)
        if (shouldBreak) breakSuggested = true

        val totalMs = (SystemClock.elapsedRealtime() - questionShownClockMs).toInt().coerceAtLeast(0)
        val scratchMode = when {
            state.scratchStrokes > 0 -> "DIGITAL"
            state.usingPaper -> "PAPER"
            else -> "NONE"
        }

        val nowMs = System.currentTimeMillis()
        val today = Dates.today()
        val celebrate = correct || foundWithNudge

        var awardedBonus: Int? = null
        var updatedDailyPlan = state.dailyChallengePlan
        var newPoints = state.totalProgressPoints + if (correct) DailyChallengeEngine.REGULAR_CORRECT_POINTS else 0
        var newStreak = state.dailyStreak

        state.student?.let { st ->
            val attempt = AttemptEntity(
                id = UUID.randomUUID().toString(),
                studentId = st.id,
                sessionId = sessionId,
                questionId = q.id,
                responseJson = Grading.encodeResponse(resp),
                isCorrect = correct,
                confidence = null,
                firstActionMs = firstActionMs,
                totalMs = totalMs,
                listenCount = listenCount,
                answeredAtMs = nowMs,
                localDate = today,
                selectionReason = reasons[q.id]?.take(200),
                scratchpad = scratchMode,
                scratchpadStrokes = state.scratchStrokes,
            )

            val dailyPlan = state.dailyChallengePlan
            var newChallengeRecord: DailyChallengeRecord? = null
            if (state.isDailyChallenge && dailyPlan != null) {
                val alreadyCompleted = dailyPlan.isCompletedToday
                val bonus = when {
                    alreadyCompleted -> 0
                    celebrate -> dailyPlan.bonusPoints
                    else -> DailyChallengeEngine.DAILY_EFFORT_BONUS_POINTS
                }
                awardedBonus = bonus
                if (!alreadyCompleted) {
                    val record = DailyChallengeRecord(
                        id = "${st.id}_${dailyPlan.localDate}",
                        studentId = st.id,
                        localDate = dailyPlan.localDate,
                        questionId = dailyPlan.question.id,
                        strand = dailyPlan.strand,
                        difficultyBand = dailyPlan.targetBand,
                        completed = true,
                        isCorrect = celebrate,
                        bonusPoints = bonus,
                        completedAtMs = nowMs,
                    )
                    newChallengeRecord = record
                    updatedDailyPlan = dailyPlan.copy(completedRecord = record)
                    newPoints += bonus
                    newStreak += 1
                }
            }

            viewModelScope.launch {
                repository.recordAttempt(attempt, sessionStartedAtMs)
                if (newChallengeRecord != null) {
                    repository.recordDailyChallenge(newChallengeRecord)
                }
            }
        }

        _uiState.update {
            it.copy(
                phase = PlayerPhase.FEEDBACK,
                result = FeedbackResult(correct = correct, foundWithNudge = foundWithNudge),
                showExplanation = !correct && !foundWithNudge,
                offerBreak = shouldBreak,
                confettiBurstId = if (celebrate) it.confettiBurstId + 1 else it.confettiBurstId,
                confettiIntensity = when {
                    celebrate && it.isDailyChallenge -> ConfettiIntensity.SESSION_DONE
                    celebrate -> ConfettiIntensity.PUZZLE
                    else -> it.confettiIntensity
                },
                dailyChallengePlan = updatedDailyPlan,
                dailyBonusAwarded = awardedBonus,
                totalProgressPoints = newPoints,
                dailyStreak = newStreak,
            )
        }

        val dyk = q.didYouKnow?.let { " Did you know? ${SpeechFormatter.speakable(it)}" }.orEmpty()
        when {
            correct -> autoSpeakText("You got it! Nice careful thinking.$dyk")
            foundWithNudge -> autoSpeakText("$FOUND_WITH_NUDGE$dyk")
            else -> autoSpeakText("Let's check it together. ${SpeechFormatter.speakable(q.explanation)}$dyk")
        }
    }

    fun revealExplanation() {
        val q = _uiState.value.currentQuestion ?: return
        _uiState.update { it.copy(showExplanation = true) }
        autoSpeakText(SpeechFormatter.speakable(q.explanation))
    }

    fun nextQuestion() {
        val state = _uiState.value
        val outOfTime = SystemClock.elapsedRealtime() - sessionStartedClockMs > SESSION_LIMIT_MS
        if (state.index + 1 >= state.questions.size || outOfTime) {
            finishSession()
        } else {
            showQuestion(state.index + 1)
        }
    }

    fun finishSession() {
        tts.stop()
        val state = _uiState.value
        val tried = state.index + 1
        _uiState.update {
            it.copy(
                phase = PlayerPhase.DONE,
                confettiBurstId = it.confettiBurstId + 1,
                confettiIntensity = ConfettiIntensity.SESSION_DONE,
            )
        }
        state.student?.let { st ->
            autoSpeakText("Great work, ${st.name}! You tried $tried puzzles. Time for a break.")
        }
        markSessionEnded()
    }

    fun markSessionEnded() {
        if (!sessionActive) return
        sessionActive = false
        val st = _uiState.value.student ?: return
        val endMs = System.currentTimeMillis()
        viewModelScope.launch {
            repository.endSession(sessionId, st.id, sessionStartedAtMs, endMs)
        }
    }

    override fun onCleared() {
        tts.stop()
        markSessionEnded()
        super.onCleared()
    }
}

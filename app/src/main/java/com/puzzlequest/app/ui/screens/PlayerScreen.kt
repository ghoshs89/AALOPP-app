package com.puzzlequest.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlequest.app.R
import com.puzzlequest.app.data.model.Answer
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.Grading
import com.puzzlequest.app.ui.components.ConfettiOverlay
import com.puzzlequest.app.ui.components.ScratchStroke
import com.puzzlequest.app.ui.components.ScratchpadCanvas
import com.puzzlequest.app.ui.components.VisualView
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onLeave: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isListening by remember { mutableStateOf(false) }

    LaunchedEffect(state.confettiBurstId) {
        if (state.confettiBurstId > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val recognizerAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun startSpeechCapture(languageCode: String) {
        if (!recognizerAvailable) return
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                    viewModel.onSpokenAlternatives(emptyList())
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    viewModel.onSpokenAlternatives(matches)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        val langTag = when (languageCode) {
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            else -> "en-US"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        isListening = true
        recognizer.startListening(intent)
    }

    var pendingSpeechLang by remember { mutableStateOf("en") }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startSpeechCapture(pendingSpeechLang)
    }

    fun requestVoiceAnswer(lang: String) {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            return
        }
        pendingSpeechLang = lang
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startSpeechCapture(lang)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val progress = when (state.phase) {
        PlayerPhase.LEARNING, PlayerPhase.ANSWERING, PlayerPhase.FEEDBACK ->
            (state.index + 1) to state.questions.size
        else -> null
    }

    QuestBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuestColors.BgMid)
                        .clickable {
                            viewModel.markSessionEnded()
                            onLeave()
                        }
                        .testTag("home_button")
                        .semantics { contentDescription = "Back to puzzles" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🏠", fontSize = 22.sp)
                }

                if (progress != null) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Puzzle ${progress.first} of ${progress.second}" },
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        for (i in 0 until progress.second) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (i < progress.first) QuestColors.Gold else QuestColors.BgMid),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state.voiceOn) QuestColors.Gold else QuestColors.BgMid)
                        .clickable { viewModel.toggleVoice() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("voice_toggle_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.voiceOn) "🔊 ${stringResource(R.string.voice_on)}" else "🔇 ${stringResource(R.string.voice_off)}",
                        color = if (state.voiceOn) QuestColors.Ink else QuestColors.Cream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }

            // Main phase body
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(bottom = 28.dp),
            ) {
                when (state.phase) {
                    PlayerPhase.LOADING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = QuestColors.Gold)
                        }
                    }

                    PlayerPhase.READY -> {
                        val student = state.student
                        val dailyPlan = state.dailyChallengePlan
                        if (state.questions.isEmpty()) {
                            Text(
                                text = "No puzzles available yet.",
                                style = MaterialTheme.typography.headlineMedium,
                                color = QuestColors.Cream,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                            )
                        } else if (state.isDailyChallenge && dailyPlan != null) {
                            val plan = dailyPlan
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "🏆 ${stringResource(R.string.daily_challenge_badge).uppercase()} · ${Dates.shortLabel(plan.localDate).uppercase()}",
                                    color = QuestColors.Gold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.2.sp,
                                )
                                Text(text = plan.strandEmoji, fontSize = 76.sp)
                                Text(
                                    text = plan.strandLabel,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = QuestColors.Cream,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = plan.adjustmentSummary,
                                    color = QuestColors.MossLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(QuestColors.BgMid)
                                        .border(1.5.dp, QuestColors.Moss.copy(alpha = 0.5f), RoundedCornerShape(50))
                                        .padding(horizontal = 16.dp, vertical = 7.dp),
                                )
                                Text(
                                    text = if (plan.isCompletedToday) {
                                        "You already earned today's bonus — play again for practice!"
                                    } else {
                                        "Complete today's hand-picked puzzle to earn +${plan.bonusPoints} bonus progress points."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.CreamDim,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                BigQuestButton(
                                    text = "${stringResource(R.string.daily_challenge_start)} 🚀",
                                    background = QuestColors.Gold,
                                    contentColor = QuestColors.Ink,
                                    onClick = { viewModel.startSession() },
                                    modifier = Modifier.testTag("start_session_button"),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Text(text = student?.avatar ?: "🧩", fontSize = 84.sp)
                                Text(
                                    text = stringResource(R.string.ready_greeting, student?.name ?: "friend"),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = QuestColors.Cream,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.puzzles_waiting, state.questions.size),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.CreamDim,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                BigQuestButton(
                                    text = "${stringResource(R.string.lets_go)} 🚀",
                                    background = QuestColors.Gold,
                                    contentColor = QuestColors.Ink,
                                    onClick = { viewModel.startSession() },
                                    modifier = Modifier.testTag("start_session_button"),
                                )
                            }
                        }
                    }

                    PlayerPhase.LEARNING -> {
                        val teach = state.currentQuestion?.teach
                        if (teach != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(12.dp, RoundedCornerShape(28.dp))
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(QuestColors.Cream)
                                    .padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.something_new).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = QuestColors.Ink.copy(alpha = 0.55f),
                                    letterSpacing = 1.5.sp,
                                )
                                Text(
                                    text = teach.title,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = QuestColors.Ink,
                                )
                                teach.visual?.let { v ->
                                    VisualView(visual = v)
                                }
                                teach.lines.forEach { line ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(text = "•", color = QuestColors.Moss, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            text = line,
                                            fontSize = 19.sp,
                                            lineHeight = 27.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = QuestColors.Ink,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            BigQuestButton(
                                text = "${stringResource(R.string.got_it)} 👍",
                                background = QuestColors.Gold,
                                contentColor = QuestColors.Ink,
                                onClick = { viewModel.beginQuestionAfterTeach() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("teach_got_it_button"),
                            )
                        }
                    }

                    PlayerPhase.DONE -> {
                        val tried = state.index + 1
                        val studentName = state.student?.name ?: "friend"
                        val bonusEarned = state.dailyBonusAwarded ?: 0
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Text(
                                text = if (state.isDailyChallenge) "🏆✨⭐" else "⭐".repeat(minOf(tried, 10)),
                                fontSize = 42.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 52.sp,
                            )
                            Text(
                                text = if (state.isDailyChallenge) {
                                    "Daily Challenge complete, $studentName!"
                                } else {
                                    "Great work, $studentName!"
                                },
                                style = MaterialTheme.typography.displayLarge,
                                color = QuestColors.Cream,
                                textAlign = TextAlign.Center,
                            )
                            if (state.isDailyChallenge) {
                                if (bonusEarned > 0) {
                                    Text(
                                        text = stringResource(R.string.daily_challenge_earned_pts, bonusEarned),
                                        color = QuestColors.Ink,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(QuestColors.Gold)
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                            .testTag("daily_bonus_awarded_pill"),
                                    )
                                }
                                Text(
                                    text = "Total progress: ${state.totalProgressPoints} pts" +
                                        if (state.dailyStreak > 0) " · 🔥 ${state.dailyStreak}-day streak" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.MossLight,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Come back tomorrow for a brand-new hand-picked puzzle!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.CreamDim,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    text = "You tried $tried puzzles. Every try makes your brain stronger.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.CreamDim,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Time for a break. Go stretch like a kangaroo! 🦘",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = QuestColors.Cream,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            BigQuestButton(
                                text = stringResource(R.string.all_done),
                                background = QuestColors.Cream,
                                contentColor = QuestColors.Ink,
                                onClick = onLeave,
                                modifier = Modifier.testTag("all_done_button"),
                            )
                        }
                    }

                    PlayerPhase.ANSWERING, PlayerPhase.FEEDBACK -> {
                        val question = state.currentQuestion ?: return@Column
                        QuestionContent(
                            question = question,
                            state = state,
                            isListening = isListening,
                            recognizerAvailable = recognizerAvailable,
                            onReadAloud = { viewModel.readQuestionAloud() },
                            onHearForeign = { viewModel.hearForeignPrompt() },
                            onToggleScratchpad = { viewModel.toggleScratchpad() },
                            onTogglePaper = { viewModel.toggleUsingPaper() },
                            onScratchStrokesChanged = { viewModel.updateScratchStrokes(it) },
                            onRevealHint = { viewModel.revealHint() },
                            onVoiceAnswer = { requestVoiceAnswer(question.language) },
                            onSelectChoice = { viewModel.selectChoice(it) },
                            onDigitKey = { viewModel.onDigitKey(it) },
                            onPlaceOrder = { viewModel.placeOrderItem(it) },
                            onRemoveOrder = { viewModel.removeOrderAt(it) },
                            onClearOrder = { viewModel.clearOrder() },
                            onSubmit = { viewModel.submitAnswer() },
                            onRevealExplanation = { viewModel.revealExplanation() },
                            onNext = { viewModel.nextQuestion() },
                            onTakeBreak = { viewModel.finishSession() },
                        )
                    }
                }
            }
        }

        // Non-blocking celebratory particle confetti overlay
        val isCelebrating = (state.phase == PlayerPhase.FEEDBACK &&
            (state.result?.correct == true || state.result?.foundWithNudge == true)) ||
            state.phase == PlayerPhase.DONE
        ConfettiOverlay(
            burstId = state.confettiBurstId,
            intensity = state.confettiIntensity,
            visible = isCelebrating,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionContent(
    question: Question,
    state: PlayerUiState,
    isListening: Boolean,
    recognizerAvailable: Boolean,
    onReadAloud: () -> Unit,
    onHearForeign: () -> Unit,
    onToggleScratchpad: () -> Unit,
    onTogglePaper: () -> Unit,
    onScratchStrokesChanged: (Int) -> Unit,
    onRevealHint: () -> Unit,
    onVoiceAnswer: () -> Unit,
    onSelectChoice: (Int) -> Unit,
    onDigitKey: (String) -> Unit,
    onPlaceOrder: (String) -> Unit,
    onRemoveOrder: (Int) -> Unit,
    onClearOrder: () -> Unit,
    onSubmit: () -> Unit,
    onRevealExplanation: () -> Unit,
    onNext: () -> Unit,
    onTakeBreak: () -> Unit,
) {
    val reveal = state.phase == PlayerPhase.FEEDBACK
    val answer = question.answer

    // Keep scratch strokes alive across hide/reopen for the same question
    val strokes = key(question.id) { remember { mutableStateListOf<ScratchStroke>() } }

    val dailyPlan = state.dailyChallengePlan
    if (state.isDailyChallenge && dailyPlan != null) {
        val plan = dailyPlan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(QuestColors.BgMid)
                .border(1.5.dp, QuestColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .testTag("daily_challenge_header_banner"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🏆 ${plan.strandEmoji} ${plan.strandLabel} · Level ${plan.targetBand}",
                color = QuestColors.Cream,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+${plan.bonusPoints} pts",
                color = QuestColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(QuestColors.Gold)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }

    // Cream Question Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(QuestColors.Cream)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        question.passage?.let { passage ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuestColors.PaperSoft)
                    .border(2.dp, QuestColors.Ink.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.read_this).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = QuestColors.Ink.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                )
                Text(
                    text = passage,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QuestColors.Ink,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuestColors.Ink)
                    .clickable(onClick = onReadAloud)
                    .testTag("read_aloud_button")
                    .semantics { contentDescription = "Read the question out loud" },
                contentAlignment = Alignment.Center,
            ) {
                Text("🔊", fontSize = 26.sp)
            }
            Text(
                text = question.prompt,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QuestColors.Ink,
                modifier = Modifier.weight(1f),
            )
        }

        question.listen?.let {
            Button(
                onClick = onHearForeign,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestColors.Ink,
                    contentColor = QuestColors.Cream,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("hear_it_button"),
            ) {
                Text("🔊 ${stringResource(R.string.hear_it)}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }

        question.visual?.let { v ->
            VisualView(visual = v)
        }
    }

    Spacer(Modifier.height(12.dp))

    // Tools Row
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolChip(
            label = "✏️ ${if (state.scratchOpen) stringResource(R.string.hide_scratchpad) else stringResource(R.string.scratchpad)}",
            active = state.scratchOpen,
            onClick = onToggleScratchpad,
            testTag = "scratchpad_toggle_button",
        )
        ToolChip(
            label = "📝 ${if (state.usingPaper) stringResource(R.string.using_paper) else stringResource(R.string.im_using_paper)}",
            active = state.usingPaper,
            onClick = onTogglePaper,
            testTag = "paper_toggle_button",
        )
        if (question.hint != null && state.phase == PlayerPhase.ANSWERING) {
            ToolChip(
                label = "💡 ${if (state.showHint) stringResource(R.string.hint_shown) else stringResource(R.string.need_a_hint)}",
                active = state.showHint,
                onClick = onRevealHint,
                testTag = "hint_button",
            )
        }
        if (question.voiceAnswer == true && answer is Answer.Choice && recognizerAvailable && state.phase == PlayerPhase.ANSWERING) {
            ToolChip(
                label = "🎤 ${if (isListening) stringResource(R.string.listening) else stringResource(R.string.say_it)}",
                active = isListening,
                onClick = onVoiceAnswer,
                testTag = "say_it_button",
            )
        }
    }

    if (state.showHint && question.hint != null && state.phase == PlayerPhase.ANSWERING) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(QuestColors.BgMid)
                .border(2.dp, QuestColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Text(
                text = "Hint: ${question.hint}",
                color = QuestColors.Cream,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    state.heard?.let { h ->
        Spacer(Modifier.height(8.dp))
        val msg = when {
            h.matched -> "I heard \"${h.text}\". Is that your answer?"
            h.text.isNotBlank() -> "I heard \"${h.text}\". Tap your answer, or try again."
            else -> "I did not catch that. Tap your answer, or try again."
        }
        Text(text = msg, color = QuestColors.CreamDim, fontSize = 16.sp)
    }

    if (state.scratchOpen) {
        Spacer(Modifier.height(12.dp))
        ScratchpadCanvas(
            strokes = strokes,
            onStrokesChanged = onScratchStrokesChanged,
        )
    }

    // Find-the-Bug gentle nudge banner
    if (answer is Answer.FindTheBug && state.firstBugTap != null && state.phase == PlayerPhase.ANSWERING) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(QuestColors.BgMid)
                .padding(14.dp),
        ) {
            Text(
                text = "${PlayerViewModel.NUDGE} 👆",
                color = QuestColors.Cream,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    // Answer Area
    when (answer) {
        is Answer.FindTheBug -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                answer.steps.forEachIndexed { idx, step ->
                    val picked = state.selectedChoice == idx
                    val ruledOut = !reveal && state.firstBugTap != null && idx >= state.firstBugTap
                    val borderColor = when {
                        ruledOut -> Color.White.copy(alpha = 0.1f)
                        reveal && idx == answer.bugIndex -> QuestColors.Danger
                        reveal && picked -> QuestColors.Gold.copy(alpha = 0.6f)
                        reveal -> Color.White.copy(alpha = 0.1f)
                        picked -> QuestColors.Gold
                        else -> Color.White.copy(alpha = 0.18f)
                    }
                    val bgColor = when {
                        reveal && idx == answer.bugIndex -> QuestColors.Danger.copy(alpha = 0.22f)
                        picked && !reveal -> QuestColors.Gold.copy(alpha = 0.2f)
                        else -> QuestColors.BgHill
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (ruledOut) 0.45f else if (reveal && idx != answer.bugIndex && !picked) 0.7f else 1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .border(3.dp, borderColor, RoundedCornerShape(18.dp))
                            .clickable(enabled = !reveal && !ruledOut) { onSelectChoice(idx) }
                            .padding(14.dp)
                            .testTag("bug_step_$idx"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "Step ${idx + 1}",
                            color = QuestColors.Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(QuestColors.Cream)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = step,
                                color = QuestColors.Cream,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 25.sp,
                            )
                            if (reveal && idx == answer.bugIndex) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Should be: ${answer.fix}",
                                    color = QuestColors.MossLight,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        is Answer.Choice -> {
            val labels = "ABCDE"
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                answer.choices.forEachIndexed { idx, label ->
                    val picked = state.selectedChoice == idx
                    val borderColor = when {
                        reveal && idx == answer.correctIndex -> QuestColors.Moss
                        reveal && picked -> QuestColors.Danger.copy(alpha = 0.75f)
                        reveal -> Color.White.copy(alpha = 0.1f)
                        picked -> QuestColors.Gold
                        else -> Color.White.copy(alpha = 0.18f)
                    }
                    val bgColor = when {
                        reveal && idx == answer.correctIndex -> QuestColors.Moss.copy(alpha = 0.25f)
                        reveal && picked -> QuestColors.Danger.copy(alpha = 0.18f)
                        picked -> QuestColors.Gold.copy(alpha = 0.2f)
                        else -> QuestColors.BgHill
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .alpha(if (reveal && idx != answer.correctIndex && !picked) 0.6f else 1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .border(3.dp, borderColor, RoundedCornerShape(18.dp))
                            .clickable(enabled = !reveal) { onSelectChoice(idx) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("choice_option_$idx"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(QuestColors.Cream),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = labels.getOrElse(idx) { '?' }.toString(),
                                color = QuestColors.Ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                            )
                        }
                        if (answer.choiceVisuals != null && idx < answer.choiceVisuals.size) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                VisualView(visual = answer.choiceVisuals[idx], compact = true)
                                if (reveal) {
                                    Text(
                                        text = label,
                                        color = QuestColors.Cream,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = label,
                                color = QuestColors.Cream,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        is Answer.Order -> {
            OrderBoardView(
                items = answer.items,
                slots = answer.slots,
                placed = state.placedOrder,
                correctOrder = answer.correctOrder,
                reveal = reveal,
                onPlace = onPlaceOrder,
                onRemoveAt = onRemoveOrder,
                onClear = onClearOrder,
            )
        }

        is Answer.Numeric -> {
            NumberPadView(
                value = state.digits,
                unit = answer.unit,
                disabled = reveal,
                onKey = onDigitKey,
            )
        }
    }

    // Check Answer button
    if (state.phase == PlayerPhase.ANSWERING && state.currentResponse != null) {
        Spacer(Modifier.height(18.dp))
        BigQuestButton(
            text = "${stringResource(R.string.check_answer)} ✓",
            background = QuestColors.Gold,
            contentColor = QuestColors.Ink,
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("check_answer_button"),
        )
    }

    // Feedback Section
    if (state.phase == PlayerPhase.FEEDBACK && state.result != null) {
        val res = state.result
        val tappedLaterStep = answer is Answer.FindTheBug &&
            state.selectedChoice != null &&
            state.selectedChoice > answer.bugIndex
        val correctLabel = when (answer) {
            is Answer.Choice -> answer.choices.getOrElse(answer.correctIndex) { "" }
            is Answer.FindTheBug -> "Step ${answer.bugIndex + 1}"
            is Answer.Order -> answer.correctOrder.joinToString(" -> ")
            is Answer.Numeric -> buildString {
                append(Grading.formatNumeric(answer.value))
                if (!answer.unit.isNullOrBlank()) append(" ${answer.unit}")
            }
        }

        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(QuestColors.BgMid)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                res.correct -> {
                    Text(
                        text = "You got it! Nice careful thinking. 🎉",
                        style = MaterialTheme.typography.headlineMedium,
                        color = QuestColors.MossLight,
                    )
                }
                res.foundWithNudge -> {
                    Text(
                        text = "${PlayerViewModel.FOUND_WITH_NUDGE} 🔍",
                        style = MaterialTheme.typography.headlineMedium,
                        color = QuestColors.MossLight,
                    )
                }
                else -> {
                    Text(
                        text = "Let's check it together. 🔍",
                        style = MaterialTheme.typography.headlineMedium,
                        color = QuestColors.Gold,
                    )
                    val prefix = if (tappedLaterStep) "So close. Something went wrong before that step. " else ""
                    val intro = if (answer is Answer.FindTheBug) "The first mistake is in " else "The answer is "
                    Text(
                        text = "$prefix$intro$correctLabel.",
                        color = QuestColors.Cream,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (state.isDailyChallenge) {
                val awarded = state.dailyBonusAwarded ?: 0
                if (awarded > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(QuestColors.Gold.copy(alpha = 0.18f))
                            .border(1.5.dp, QuestColors.Gold, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("daily_bonus_feedback_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (res.correct || res.foundWithNudge) {
                                "🏆 Daily Challenge Bonus!"
                            } else {
                                "🌟 Daily Challenge Effort Bonus!"
                            },
                            color = QuestColors.Gold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "+$awarded pts",
                            color = QuestColors.Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(QuestColors.Gold)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (state.showExplanation) {
                Text(
                    text = question.explanation,
                    color = QuestColors.Cream,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            } else {
                TextButton(
                    onClick = onRevealExplanation,
                    modifier = Modifier.testTag("how_do_we_know_button"),
                ) {
                    Text(
                        text = stringResource(R.string.how_do_we_know),
                        color = QuestColors.Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }

            question.didYouKnow?.let { dyk ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuestColors.BgHill)
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.did_you_know),
                        color = QuestColors.Gold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dyk,
                        color = QuestColors.Cream,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    )
                }
            }

            if (state.offerBreak) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(QuestColors.BgHill)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "That was a tricky run. Tricky puzzles grow your brain. Want a short break?",
                        color = QuestColors.Cream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BigQuestButton(
                            text = "${stringResource(R.string.take_a_break)} 🦘",
                            background = QuestColors.Cream,
                            contentColor = QuestColors.Ink,
                            onClick = onTakeBreak,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("take_break_button"),
                        )
                        BigQuestButton(
                            text = "${stringResource(R.string.keep_going)} ➜",
                            background = QuestColors.Gold,
                            contentColor = QuestColors.Ink,
                            onClick = onNext,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("keep_going_button"),
                        )
                    }
                }
            } else {
                val isLast = state.index + 1 >= state.questions.size
                BigQuestButton(
                    text = if (isLast) "${stringResource(R.string.finish_session)} ⭐" else "${stringResource(R.string.next_puzzle)} ➜",
                    background = QuestColors.Gold,
                    contentColor = QuestColors.Ink,
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("next_puzzle_button"),
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) QuestColors.Gold else QuestColors.BgHill)
            .border(
                width = 2.dp,
                color = if (active) QuestColors.Gold else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) QuestColors.Ink else QuestColors.Cream,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun BigQuestButton(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = contentColor),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.heightIn(min = 62.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NumberPadView(
    value: String,
    unit: String?,
    disabled: Boolean,
    onKey: (String) -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 190.dp)
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(QuestColors.Cream)
                .border(3.dp, QuestColors.Gold, RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value.ifEmpty { "?" },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QuestColors.Ink,
            )
            if (!unit.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = unit,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = QuestColors.Ink.copy(alpha = 0.7f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(62.dp)
                                    .alpha(if (disabled) 0.5f else 1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(QuestColors.BgHill)
                                    .border(2.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                                    .clickable(enabled = !disabled) { onKey(key) }
                                    .testTag("numpad_$key")
                                    .semantics { contentDescription = if (key == "⌫") "Delete" else key },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    color = QuestColors.Cream,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderBoardView(
    items: List<String>,
    slots: List<String>?,
    placed: List<String>,
    correctOrder: List<String>,
    reveal: Boolean,
    onPlace: (String) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val remaining = items.filter { it !in placed }
    val slotLabels = slots ?: items.indices.map { "${it + 1}" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slotLabels.forEachIndexed { idx, slotLabel ->
                val item = placed.getOrNull(idx)
                val borderColor = when {
                    reveal && item == correctOrder.getOrNull(idx) -> QuestColors.Moss
                    reveal -> QuestColors.Danger.copy(alpha = 0.75f)
                    item != null -> QuestColors.Gold
                    else -> Color.White.copy(alpha = 0.25f)
                }
                val bgColor = when {
                    reveal && item == correctOrder.getOrNull(idx) -> QuestColors.Moss.copy(alpha = 0.25f)
                    reveal -> QuestColors.Danger.copy(alpha = 0.18f)
                    item != null -> QuestColors.Gold.copy(alpha = 0.2f)
                    else -> QuestColors.BgMid.copy(alpha = 0.7f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bgColor)
                        .border(3.dp, borderColor, RoundedCornerShape(18.dp))
                        .clickable(enabled = !reveal && item != null) { onRemoveAt(idx) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("order_slot_$idx"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = slotLabel,
                        color = QuestColors.Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(QuestColors.Cream)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Text(
                        text = item ?: "Tap below",
                        color = if (item != null) QuestColors.Cream else QuestColors.CreamDim,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (!reveal) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(QuestColors.BgMid)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tap in order (tap a slot to take it back):",
                        color = QuestColors.CreamDim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (placed.isNotEmpty()) {
                        TextButton(onClick = onClear, modifier = Modifier.testTag("order_clear_button")) {
                            Text(text = stringResource(R.string.clear), color = QuestColors.Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (remaining.isEmpty()) {
                    Text(
                        text = "All slots filled. Tap Check answer below!",
                        color = QuestColors.MossLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        remaining.forEachIndexed { i, item ->
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(QuestColors.Cream)
                                    .clickable { onPlace(item) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("order_item_$i"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item,
                                    color = QuestColors.Ink,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

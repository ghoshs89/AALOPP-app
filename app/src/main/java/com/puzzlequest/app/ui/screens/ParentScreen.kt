package com.puzzlequest.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlequest.app.CrashStore
import com.puzzlequest.app.R
import com.puzzlequest.app.data.content.ContentBank
import com.puzzlequest.app.data.db.AttemptEntity
import com.puzzlequest.app.data.db.DailyChallengeRecord
import com.puzzlequest.app.data.db.ParentNoteRecord
import com.puzzlequest.app.data.db.StudentRecord
import com.puzzlequest.app.data.model.StrandLabels
import com.puzzlequest.app.data.repository.LearnerRepository
import com.puzzlequest.app.engine.AttemptRecord
import com.puzzlequest.app.engine.DailyChallengeEngine
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.InsightAttempt
import com.puzzlequest.app.engine.Grading
import com.puzzlequest.app.engine.InsightsEngine
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.engine.SkillStateEngine
import com.puzzlequest.app.engine.SkillStatus
import com.puzzlequest.app.ui.components.CategoryProgressChartCard
import com.puzzlequest.app.ui.components.ChartCard
import com.puzzlequest.app.ui.components.DailyColumnsChart
import com.puzzlequest.app.ui.components.PercentBarRow
import com.puzzlequest.app.ui.components.PercentBars
import com.puzzlequest.app.ui.components.SkillMapView
import com.puzzlequest.app.ui.components.SkillProgressSection
import com.puzzlequest.app.ui.components.StatTile
import com.puzzlequest.app.ui.components.WeeklyLineChart
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class ParentTab(val label: String) {
    OVERVIEW("Overview"),
    SKILLS("Skills"),
    MAP("Skill map"),
    ANSWERS("Recent answers"),
    NOTES("Notes"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentScreen(
    contentBank: ContentBank,
    repository: LearnerRepository,
    unlocked: Boolean,
    onUnlockStateChange: (Boolean) -> Unit,
    onOpenReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val students by repository.students.collectAsStateWithLifecycle()
    val dataVersion by repository.dataVersion.collectAsStateWithLifecycle()

    var pinInput by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var lockedUntilMs by remember { mutableLongStateOf(repository.getPinLockedUntilMs()) }

    if (!unlocked) {
        val now = System.currentTimeMillis()
        val isLockedOut = now < lockedUntilMs
        QuestBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.grown_ups_only),
                    style = MaterialTheme.typography.displayMedium,
                    color = QuestColors.Cream,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { value ->
                        pinInput = value.filter { it.isDigit() }.take(8)
                        wrongPin = false
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    placeholder = { Text("Enter PIN", color = QuestColors.Ink.copy(alpha = 0.45f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = QuestColors.Cream,
                        unfocusedContainerColor = QuestColors.Cream,
                        focusedTextColor = QuestColors.Ink,
                        unfocusedTextColor = QuestColors.Ink,
                        focusedBorderColor = QuestColors.Gold,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_pin_input"),
                )
                if (isLockedOut) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = stringResource(R.string.pin_locked), color = QuestColors.Danger, fontSize = 15.sp)
                } else if (wrongPin) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = stringResource(R.string.pin_mismatch), color = QuestColors.Danger, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val currentMs = System.currentTimeMillis()
                        if (currentMs < lockedUntilMs) return@Button
                        if (pinInput == repository.getParentPin()) {
                            repository.clearPinFailures()
                            lockedUntilMs = 0L
                            onUnlockStateChange(true)
                            pinInput = ""
                            wrongPin = false
                        } else {
                            wrongPin = true
                            pinInput = ""
                            val until = repository.recordPinFailure(currentMs)
                            if (until > 0L) lockedUntilMs = until
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuestColors.Gold,
                        contentColor = QuestColors.Ink,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("parent_unlock_button"),
                ) {
                    Text(stringResource(R.string.unlock), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onBack, modifier = Modifier.testTag("parent_back_button")) {
                    Text(
                        text = "Back",
                        color = QuestColors.CreamDim,
                        textDecoration = TextDecoration.Underline,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    } else {
        ParentUnlockedContent(
            students = students,
            dataVersion = dataVersion,
            contentBank = contentBank,
            repository = repository,
            onUnlockStateChange = onUnlockStateChange,
            onOpenReport = onOpenReport,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentUnlockedContent(
    students: List<StudentRecord>,
    dataVersion: Long,
    contentBank: ContentBank,
    repository: LearnerRepository,
    onUnlockStateChange: (Boolean) -> Unit,
    onOpenReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id) }
    val activeStudent: StudentRecord? = students.firstOrNull { it.id == selectedStudentId } ?: students.firstOrNull()
    var activeTab by remember { mutableStateOf(ParentTab.OVERVIEW) }
    var attempts by remember { mutableStateOf<List<AttemptEntity>>(emptyList()) }
    var notes by remember { mutableStateOf<List<ParentNoteRecord>>(emptyList()) }
    var dailyChallenges by remember { mutableStateOf<List<DailyChallengeRecord>>(emptyList()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showAddPlayerDialog by remember { mutableStateOf(false) }

    var today by remember { mutableStateOf(Dates.today()) }
    var crashLog by remember { mutableStateOf(CrashStore.read(context)) }
    var copiedCrash by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        today = Dates.today()
        crashLog = CrashStore.read(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(activeStudent?.id, dataVersion) {
        val id = activeStudent?.id ?: return@LaunchedEffect
        try {
            attempts = repository.getAttempts(id)
            notes = repository.getNotes(id)
            dailyChallenges = repository.getDailyChallenges(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PuzzleQuest", "Failed to load parent dashboard data", e)
        }
    }
    val attemptRecords = remember(attempts) {
        attempts.map {
            AttemptRecord(
                questionId = it.questionId,
                isCorrect = it.isCorrect,
                answeredAtMs = it.answeredAtMs,
                localDate = it.localDate,
            )
        }
    }
    val insightAttempts = remember(attempts) {
        attempts.map {
            InsightAttempt(
                questionId = it.questionId,
                isCorrect = it.isCorrect,
                answeredAtMs = it.answeredAtMs,
                localDate = it.localDate,
                confidence = it.confidence,
                scratchpad = it.scratchpad,
                totalMs = it.totalMs,
            )
        }
    }
    val skillStates = remember(attemptRecords, today) {
        SkillStateEngine.computeSkillStates(contentBank.skills, contentBank.questionsById, attemptRecords, today)
    }
    val workingBands = remember(attemptRecords) {
        SessionSelector.computeWorkingBands(contentBank.questions, contentBank.skillsByCode, attemptRecords)
    }

    QuestBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activeStudent?.let { "${it.avatar} ${it.name}" } ?: "Grown-ups",
                    style = MaterialTheme.typography.headlineLarge,
                    color = QuestColors.Cream,
                )
                TextButton(onClick = onBack, modifier = Modifier.testTag("parent_home_button")) {
                    Text("🏠 Puzzles", color = QuestColors.CreamDim, fontWeight = FontWeight.Bold)
                }
            }

            AppUpdateCard()

            crashLog?.let { logText ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuestColors.BgMid)
                        .border(1.5.dp, QuestColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Last crash report (stored only on this device)",
                        color = QuestColors.Danger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = logText.lines().take(12).joinToString("\n"),
                        color = QuestColors.CreamDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                runCatching {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Puzzle Quest crash log", logText))
                                    copiedCrash = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuestColors.Gold, contentColor = QuestColors.Ink),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(if (copiedCrash) "Copied" else "Copy crash log", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                CrashStore.clear(context)
                                crashLog = null
                                copiedCrash = false
                            },
                        ) {
                            Text("Clear", color = QuestColors.CreamDim)
                        }
                    }
                }
            }

            // Action chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeStudent != null) {
                    Button(
                        onClick = { onOpenReport(activeStudent.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = QuestColors.Gold, contentColor = QuestColors.Ink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_report_button"),
                    ) {
                        Text(stringResource(R.string.printable_report), fontWeight = FontWeight.Bold)
                    }
                    key(activeStudent.id) {
                        ImportPracticeHistory(activeStudent, repository)
                    }
                }
                Button(
                    onClick = { showAddPlayerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = QuestColors.BgMid, contentColor = QuestColors.Cream),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_player_button"),
                ) {
                    Text("+ Player")
                }
                Button(
                    onClick = { showPinDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = QuestColors.BgMid, contentColor = QuestColors.Cream),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("change_pin_button"),
                ) {
                    Text("Change PIN")
                }
                Button(
                    onClick = {
                        onUnlockStateChange(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuestColors.BgMid, contentColor = QuestColors.Cream),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("lock_parent_button"),
                ) {
                    Text(stringResource(R.string.lock_button))
                }
            }

            if (students.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    students.forEach { st ->
                        val isSel = st.id == activeStudent?.id
                        Text(
                            text = "${st.avatar} ${st.name}",
                            color = if (isSel) QuestColors.Ink else QuestColors.Cream,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSel) QuestColors.Gold else QuestColors.BgMid)
                                .clickable { selectedStudentId = st.id }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParentTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) QuestColors.Gold else QuestColors.BgMid)
                            .clickable { activeTab = tab }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("parent_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.label,
                            color = if (selected) QuestColors.Ink else QuestColors.CreamDim,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Tab Body
            if (activeStudent == null) {
                Text("No players yet.", color = QuestColors.CreamDim)
            } else {
                when (activeTab) {
                    ParentTab.OVERVIEW -> {
                        val summary = InsightsEngine.summarize(insightAttempts, today)
                        val days = InsightsEngine.dailyActivity(insightAttempts, today, 14)
                        val weeks = InsightsEngine.weeklyAccuracy(insightAttempts, today, 8)
                        val strands = InsightsEngine.accuracyByStrand(insightAttempts, contentBank.questionsById, contentBank.skills)
                        val split = InsightsEngine.everydayVsReasoning(insightAttempts, contentBank.questionsById, contentBank.skills)
                        val calibration = InsightsEngine.confidenceCalibration(insightAttempts)
                        val masteredCount = skillStates.values.count {
                            it.status == SkillStatus.MASTERED || it.status == SkillStatus.REVIEW_DUE
                        }
                        val weekPct = summary.lastWeek.percent

                        val progressSummary = remember(attempts, dailyChallenges, today) {
                            DailyChallengeEngine.computeProgressSummary(attempts, dailyChallenges, today)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                StatTile(
                                    label = "Answered (last 7 days)",
                                    value = summary.lastWeek.answered.toString(),
                                    detail = if (weekPct == null) "No answers this week" else "$weekPct% right",
                                    modifier = Modifier.weight(1f),
                                )
                                StatTile(
                                    label = "Practice days (last 14)",
                                    value = summary.practiceDaysLast14.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                StatTile(
                                    label = "Skills mastered",
                                    value = "$masteredCount of ${contentBank.skills.size}",
                                    detail = "Includes skills due for review",
                                    modifier = Modifier.weight(1f),
                                )
                                StatTile(
                                    label = "Worked it out",
                                    value = summary.scratchpadShare?.let { "$it%" } ?: "Not yet",
                                    detail = "Used scratchpad or paper",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                StatTile(
                                    label = "Progress points",
                                    value = "${progressSummary.totalPoints} pts",
                                    detail = "+${progressSummary.dailyBonusPoints} from daily challenges",
                                    modifier = Modifier.weight(1f),
                                )
                                StatTile(
                                    label = "Daily challenges",
                                    value = progressSummary.dailyCompletedCount.toString(),
                                    detail = if (progressSummary.dailyStreak > 0) {
                                        "${progressSummary.dailyStreak}-day streak"
                                    } else {
                                        "Ready for today's challenge"
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            CategoryProgressChartCard(
                                attempts = insightAttempts,
                                questionsById = contentBank.questionsById,
                                skills = contentBank.skills,
                                today = today,
                            )

                            if (insightAttempts.isNotEmpty()) {
                                ChartCard(
                                    title = "Everyday skills and reasoning",
                                    subtitle = "Percent right. Everyday skills are time, money, measuring, graphs and calendars.",
                                ) {
                                    PercentBars(
                                        rows = listOf(
                                            PercentBarRow("Everyday skills", split.everyday),
                                            PercentBarRow("Reasoning", split.reasoning),
                                        ),
                                    )
                                }

                                if (calibration.any { it.tally.answered > 0 }) {
                                    ChartCard(
                                        title = "How sure, and how right",
                                        subtitle = "From sessions that recorded confidence.",
                                    ) {
                                        PercentBars(
                                            rows = calibration.map {
                                                PercentBarRow(
                                                    "${StrandLabels.confidenceFace(it.level)} ${StrandLabels.confidenceLabel(it.level)}",
                                                    it.tally,
                                                )
                                            },
                                        )
                                    }
                                }

                                ChartCard(
                                    title = "Questions answered per day",
                                    subtitle = "Last 14 days",
                                ) {
                                    DailyColumnsChart(days = days)
                                }

                                ChartCard(
                                    title = "Percent right per week",
                                    subtitle = "Last 8 weeks",
                                ) {
                                    WeeklyLineChart(weeks = weeks)
                                }

                                ChartCard(
                                    title = "By strand",
                                    subtitle = "Percent right, grouped by each question's main skill",
                                ) {
                                    PercentBars(
                                        rows = strands.map {
                                            PercentBarRow(StrandLabels.labelFor(it.strand), it.tally)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    ParentTab.SKILLS -> {
                        SkillProgressSection(
                            skills = contentBank.skills,
                            states = skillStates,
                            workingBands = workingBands,
                            today = today,
                        )
                    }

                    ParentTab.MAP -> {
                        ChartCard(
                            title = "Skill map",
                            subtitle = "Skills on the left come first. Tap a skill for details.",
                        ) {
                            SkillMapView(
                                skills = contentBank.skills,
                                states = skillStates,
                                today = today,
                            )
                        }
                    }

                    ParentTab.ANSWERS -> {
                        val recent = attempts.sortedByDescending { it.answeredAtMs }.take(60)
                        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                        if (recent.isEmpty()) {
                            Text("No answers yet.", color = QuestColors.CreamDim, fontSize = 16.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                recent.forEach { a ->
                                    val question = contentBank.questionsById[a.questionId]
                                    val prompt = question?.prompt ?: a.questionId
                                    val tools = buildString {
                                        append("${(a.totalMs / 1000.0).toInt()}s")
                                        if (a.listenCount > 0) append(" · 🔊${a.listenCount}")
                                        when (a.scratchpad) {
                                            "DIGITAL" -> append(" · ✏️${a.scratchpadStrokes ?: ""}")
                                            "PAPER" -> append(" · 📝")
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(QuestColors.BgMid)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = fmt.format(Date(a.answeredAtMs)),
                                                color = QuestColors.CreamDim,
                                                fontSize = 12.sp,
                                            )
                                            Text(
                                                text = "${if (a.isCorrect) "✅" else "❌"}  $tools",
                                                color = QuestColors.Cream,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Text(
                                            text = prompt,
                                            color = QuestColors.Cream,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Answer: " + Grading.describeResponse(a.responseJson, question),
                                            color = QuestColors.Cream,
                                        )
                                        Text(
                                            text = a.selectionReason ?: "Adaptive practice",
                                            color = QuestColors.CreamDim,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ParentTab.NOTES -> {
                        var noteBody by remember { mutableStateOf("") }
                        val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(QuestColors.BgMid)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.add_note),
                                    color = QuestColors.Cream,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                OutlinedTextField(
                                    value = noteBody,
                                    onValueChange = { noteBody = it.take(2000) },
                                    minLines = 3,
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.note_placeholder),
                                            color = QuestColors.Ink.copy(alpha = 0.5f),
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = QuestColors.Cream,
                                        unfocusedContainerColor = QuestColors.Cream,
                                        focusedTextColor = QuestColors.Ink,
                                        unfocusedTextColor = QuestColors.Ink,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("parent_note_input"),
                                )
                                Button(
                                    onClick = {
                                        val clean = noteBody.trim()
                                        if (clean.isNotEmpty()) {
                                            noteBody = ""
                                            scope.launch {
                                                try {
                                                    repository.addNote(activeStudent.id, clean)
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    android.util.Log.e("PuzzleQuest", "Failed to add note", e)
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = QuestColors.Gold,
                                        contentColor = QuestColors.Ink,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("save_note_button"),
                                ) {
                                    Text(stringResource(R.string.save_note), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (notes.isEmpty()) {
                                Text(
                                    text = "No notes yet. Notes appear in the progress report.",
                                    color = QuestColors.CreamDim,
                                    fontSize = 15.sp,
                                )
                            } else {
                                notes.forEach { n ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(QuestColors.BgMid)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(text = n.body, color = QuestColors.Cream, fontSize = 15.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "${fmt.format(Date(n.createdAtMs))} · ${n.authorLabel}",
                                                color = QuestColors.CreamDim,
                                                fontSize = 12.sp,
                                            )
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            repository.deleteNote(n.id)
                                                        } catch (e: CancellationException) {
                                                            throw e
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("PuzzleQuest", "Failed to delete note", e)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.testTag("delete_note_${n.id}"),
                                            ) {
                                                Text(stringResource(R.string.delete_note), color = QuestColors.Danger)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Change Grown-ups PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("New PIN (4 to 8 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length >= 4) {
                            repository.setParentPin(newPin)
                            showPinDialog = false
                        }
                    },
                    enabled = newPin.length >= 4,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddPlayerDialog) {
        var playerName by remember { mutableStateOf("") }
        val avatars = listOf("🏎️", "🦊", "🦕", "🚀", "🦉", "🐼", "🌟", "🦘")
        var selectedAvatar by remember { mutableStateOf(avatars[1]) }
        AlertDialog(
            onDismissRequest = { showAddPlayerDialog = false },
            title = { Text("Add a player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it.take(24) },
                        label = { Text("Player name") },
                        singleLine = true,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avatars.forEach { emoji ->
                            val sel = emoji == selectedAvatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) QuestColors.Gold else QuestColors.BgHill)
                                    .clickable { selectedAvatar = emoji },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = playerName.trim()
                        if (clean.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val created = repository.addStudent(clean, selectedAvatar)
                                    selectedStudentId = created.id
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    android.util.Log.e("PuzzleQuest", "Failed to add student", e)
                                }
                            }
                            showAddPlayerDialog = false
                        }
                    },
                    enabled = playerName.trim().isNotEmpty(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlayerDialog = false }) { Text("Cancel") }
            },
        )
    }
}

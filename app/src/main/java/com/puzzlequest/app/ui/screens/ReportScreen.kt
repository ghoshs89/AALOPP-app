package com.puzzlequest.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.puzzlequest.app.R
import com.puzzlequest.app.data.content.ContentBank
import com.puzzlequest.app.data.db.AttemptEntity
import com.puzzlequest.app.data.db.ParentNoteRecord
import com.puzzlequest.app.data.model.StrandLabels
import com.puzzlequest.app.data.repository.LearnerRepository
import com.puzzlequest.app.engine.AttemptRecord
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.InsightAttempt
import com.puzzlequest.app.engine.InsightsEngine
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.engine.SkillStateEngine
import com.puzzlequest.app.engine.SkillStatus
import com.puzzlequest.app.engine.Tally
import com.puzzlequest.app.ui.theme.QuestColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTally(t: Tally): String =
    t.percent?.let { "$it% (${t.correct} of ${t.answered})" } ?: "No answers"

@Composable
fun ReportScreen(
    studentId: String,
    contentBank: ContentBank,
    repository: LearnerRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val students by repository.students.collectAsStateWithLifecycle()
    val student = students.firstOrNull { it.id == studentId } ?: students.firstOrNull()
    var attempts by remember { mutableStateOf<List<AttemptEntity>>(emptyList()) }
    var notes by remember { mutableStateOf<List<ParentNoteRecord>>(emptyList()) }

    LaunchedEffect(student?.id) {
        val id = student?.id ?: return@LaunchedEffect
        attempts = repository.getAttempts(id)
        notes = repository.getNotes(id).take(20)
    }

    val today = remember { Dates.today() }
    val attemptRecords = remember(attempts) {
        attempts.map { AttemptRecord(it.questionId, it.isCorrect, it.answeredAtMs, it.localDate) }
    }
    val insightAttempts = remember(attempts) {
        attempts.map { InsightAttempt(it.questionId, it.isCorrect, it.answeredAtMs, it.localDate, it.confidence, it.scratchpad, it.totalMs) }
    }
    val states = remember(attemptRecords, today) {
        SkillStateEngine.computeSkillStates(contentBank.skills, contentBank.questionsById, attemptRecords, today)
    }
    val workingBands = remember(attemptRecords) {
        SessionSelector.computeWorkingBands(contentBank.questions, contentBank.skillsByCode, attemptRecords)
    }
    val summary = remember(insightAttempts, today) { InsightsEngine.summarize(insightAttempts, today) }
    val split = remember(insightAttempts) { InsightsEngine.everydayVsReasoning(insightAttempts, contentBank.questionsById, contentBank.skills) }
    val strands = remember(insightAttempts) { InsightsEngine.accuracyByStrand(insightAttempts, contentBank.questionsById, contentBank.skills) }
    val masteredSkills = remember(states) {
        contentBank.skills.filter {
            val st = states[it.code]?.status
            st == SkillStatus.MASTERED || st == SkillStatus.REVIEW_DUE
        }
    }
    val learningSkills = remember(states) {
        contentBank.skills.filter { states[it.code]?.status == SkillStatus.LEARNING }
    }
    val firstDate = attempts.minOfOrNull { it.localDate }
    val practiceDays = attempts.map { it.localDate }.toSet().size

    // Plain light report surface matching src/app/parent/report/page.tsx
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 740.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("report_back_button")) {
                    Text(
                        text = stringResource(R.string.back_to_grown_ups),
                        color = QuestColors.VizSeries,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                Button(
                    onClick = {
                        val shareText = buildString {
                            appendLine("Practice progress report: ${student?.name ?: "Learner"}")
                            appendLine("Generated $today.")
                            appendLine(
                                if (firstDate != null) {
                                    "Covers ${attempts.size} answers on $practiceDays days since $firstDate."
                                } else {
                                    "No answers recorded yet."
                                },
                            )
                            appendLine()
                            appendLine("Last 7 days: ${summary.lastWeek.answered} answered (${summary.lastWeek.percent?.let { "$it% right" } ?: "none"})")
                            appendLine("Skills mastered: ${masteredSkills.size} of ${contentBank.skills.size}")
                            appendLine("Everyday skills: ${formatTally(split.everyday)}")
                            appendLine("Reasoning: ${formatTally(split.reasoning)}")
                            appendLine()
                            appendLine("By strand:")
                            strands.forEach { s ->
                                appendLine("- ${StrandLabels.labelFor(s.strand)}: ${formatTally(s.tally)} (Level ${workingBands[s.strand] ?: 2}/5)")
                            }
                            if (notes.isNotEmpty()) {
                                appendLine()
                                appendLine("Parent notes:")
                                notes.forEach { n -> appendLine("- ${n.body}") }
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Puzzle Quest Progress Report: ${student?.name ?: "Learner"}")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share progress report"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuestColors.VizSeries, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("share_report_button"),
                ) {
                    Text(stringResource(R.string.share_report), fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Practice progress report: ${student?.name ?: "Learner"}",
                    color = QuestColors.VizInk,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Generated $today. " + if (firstDate != null) {
                        "Covers ${attempts.size} answers on $practiceDays days since $firstDate."
                    } else {
                        "No answers recorded yet."
                    },
                    color = QuestColors.VizInk2,
                    fontSize = 13.sp,
                )
                Text(
                    text = "Source: the Puzzle Quest home practice app. Questions are Math Kangaroo, Bebras and grade 2 to 3 style items; results reflect home practice, not a standardized test.",
                    color = QuestColors.VizInk2,
                    fontSize = 13.sp,
                )
            }

            HorizontalDivider(color = QuestColors.VizGrid)

            // Summary Tiles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportTile("Answers, last 7 days", summary.lastWeek.answered.toString(), Modifier.weight(1f))
                    ReportTile("Right, last 7 days", summary.lastWeek.percent?.let { "$it%" } ?: "None", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportTile("Skills mastered", "${masteredSkills.size} of ${contentBank.skills.size}", Modifier.weight(1f))
                    ReportTile("Worked it out", summary.scratchpadShare?.let { "$it%" } ?: "Not tracked yet", Modifier.weight(1f))
                }
            }

            // Accuracy Table
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Accuracy", color = QuestColors.VizInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                ReportRow("Area", "Percent right", "Level", header = true)
                HorizontalDivider(color = QuestColors.VizBaseline)
                ReportRow("Everyday skills (time, money, measuring, graphs)", formatTally(split.everyday), "", bold = true)
                ReportRow("Reasoning", formatTally(split.reasoning), "", bold = true)
                strands.forEach { s ->
                    ReportRow(
                        StrandLabels.labelFor(s.strand),
                        formatTally(s.tally),
                        "${workingBands[s.strand] ?: 2}/5",
                    )
                }
            }

            // Mastered & Still learning
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mastered skills", color = QuestColors.VizInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("4 of the last 5 right, on at least 2 different days.", color = QuestColors.VizInk2, fontSize = 13.sp)
                if (masteredSkills.isEmpty()) {
                    Text("None yet", color = QuestColors.VizInk2, fontSize = 14.sp)
                } else {
                    masteredSkills.forEach { s ->
                        Text("• ${s.name}", color = QuestColors.VizInk, fontSize = 14.sp)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Still learning", color = QuestColors.VizInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (learningSkills.isEmpty()) {
                    Text("None", color = QuestColors.VizInk2, fontSize = 14.sp)
                } else {
                    learningSkills.forEach { s ->
                        val st = states[s.code]
                        val right = st?.recent?.count { it } ?: 0
                        val total = st?.recent?.size ?: 0
                        Text("• ${s.name} ($right of last $total right)", color = QuestColors.VizInk, fontSize = 14.sp)
                    }
                }
            }

            // Parent Notes
            val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Parent notes", color = QuestColors.VizInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (notes.isEmpty()) {
                    Text("No notes.", color = QuestColors.VizInk2, fontSize = 14.sp)
                } else {
                    notes.forEach { n ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(QuestColors.VizBaseline),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(dateFmt.format(Date(n.createdAtMs)), color = QuestColors.VizInk2, fontSize = 12.sp)
                                Text(n.body, color = QuestColors.VizInk, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, QuestColors.VizGrid, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(label, color = QuestColors.VizInk2, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = QuestColors.VizInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReportRow(area: String, pct: String, level: String, header: Boolean = false, bold: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = area,
                color = QuestColors.VizInk,
                fontSize = 13.sp,
                fontWeight = if (header || bold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1.6f),
            )
            Text(
                text = pct,
                color = QuestColors.VizInk,
                fontSize = 13.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = level,
                color = QuestColors.VizInk,
                fontSize = 13.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(44.dp),
            )
        }
        if (!header) HorizontalDivider(color = QuestColors.VizGrid)
    }
}

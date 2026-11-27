package com.puzzlequest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.R
import com.puzzlequest.app.data.db.StudentRecord
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.Theme
import com.puzzlequest.app.engine.SessionSelector
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors

@Composable
fun ChooseThemeScreen(
    student: StudentRecord,
    themes: List<Theme>,
    skillsByCode: Map<String, Skill>,
    onStartDaily: () -> Unit,
    onStartTheme: (String) -> Unit,
    onBackToPlayers: () -> Unit,
) {
    QuestBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.what_shall_we_play, student.name),
                style = MaterialTheme.typography.displayMedium,
                color = QuestColors.Cream,
                textAlign = TextAlign.Center,
            )

            // Primary daily puzzles card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(QuestColors.Gold)
                    .clickable(onClick = onStartDaily)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag("todays_puzzles_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "🧩", fontSize = 64.sp)
                Text(
                    text = stringResource(R.string.todays_puzzles),
                    style = MaterialTheme.typography.headlineLarge,
                    color = QuestColors.Ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.picked_just_for_you),
                    style = MaterialTheme.typography.bodyLarge,
                    color = QuestColors.Ink.copy(alpha = 0.85f),
                )
            }

            themes.forEach { theme ->
                val skillSummary = theme.skills
                    .mapNotNull { skillsByCode[it]?.name }
                    .take(4)
                    .joinToString(", ")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(QuestColors.BgMid)
                        .clickable { onStartTheme(theme.id) }
                        .padding(20.dp)
                        .testTag("theme_card_${theme.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(text = theme.emoji, fontSize = 46.sp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = theme.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = QuestColors.Cream,
                        )
                        if (skillSummary.isNotBlank()) {
                            Text(
                                text = skillSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = QuestColors.CreamDim,
                            )
                        }
                        Text(
                            text = stringResource(R.string.plus_core_puzzles, SessionSelector.THEMED_CORE_ITEMS),
                            style = MaterialTheme.typography.bodyMedium,
                            color = QuestColors.CreamDim.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onBackToPlayers,
                modifier = Modifier.testTag("someone_else_button"),
            ) {
                Text(
                    text = stringResource(R.string.someone_else_playing),
                    color = QuestColors.CreamDim,
                    fontSize = 16.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

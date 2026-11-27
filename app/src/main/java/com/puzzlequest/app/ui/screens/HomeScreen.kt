package com.puzzlequest.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.puzzlequest.app.ui.theme.QuestBackdrop
import com.puzzlequest.app.ui.theme.QuestColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    students: List<StudentRecord>,
    isLoadingContent: Boolean,
    onSelectStudent: (String) -> Unit,
    onOpenParent: () -> Unit,
) {
    QuestBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .widthIn(max = 680.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🧩",
                fontSize = 56.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.who_is_playing),
                style = MaterialTheme.typography.displayLarge,
                color = QuestColors.Cream,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            if (isLoadingContent && students.isEmpty()) {
                CircularProgressIndicator(color = QuestColors.Gold)
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    students.forEach { student ->
                        Column(
                            modifier = Modifier
                                .width(176.dp)
                                .shadow(12.dp, RoundedCornerShape(28.dp))
                                .clip(RoundedCornerShape(28.dp))
                                .background(QuestColors.BgMid)
                                .clickable { onSelectStudent(student.id) }
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                                .testTag("student_card_${student.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = student.avatar, fontSize = 72.sp)
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.headlineLarge,
                                color = QuestColors.Cream,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            TextButton(
                onClick = onOpenParent,
                modifier = Modifier.testTag("grown_ups_button"),
            ) {
                Text(
                    text = stringResource(R.string.grown_ups),
                    color = QuestColors.CreamDim,
                    fontSize = 17.sp,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

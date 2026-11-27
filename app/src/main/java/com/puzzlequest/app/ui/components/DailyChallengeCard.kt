package com.puzzlequest.app.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.R
import com.puzzlequest.app.engine.DailyChallengePlan
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.DifficultyAdjustment
import com.puzzlequest.app.engine.ProgressPointsSummary
import com.puzzlequest.app.ui.theme.QuestColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentProgressPillRow(
    summary: ProgressPointsSummary,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.testTag("student_progress_pills"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(QuestColors.BgMid)
                .border(1.5.dp, QuestColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("progress_points_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Stars,
                contentDescription = null,
                tint = QuestColors.Gold,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.progress_points_label, summary.totalPoints),
                color = QuestColors.Cream,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
            )
        }

        if (summary.dailyStreak > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(QuestColors.BgMid)
                    .border(1.5.dp, QuestColors.ConfettiAmber.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("daily_streak_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = QuestColors.ConfettiAmber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.daily_streak_label, summary.dailyStreak),
                    color = QuestColors.Cream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyChallengeCard(
    plan: DailyChallengePlan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = plan.isCompletedToday
    val dateLabel = Dates.shortLabel(plan.localDate)
    val badgeTitle = stringResource(R.string.daily_challenge_badge)
    val earnedPoints = plan.completedRecord?.bonusPoints ?: plan.bonusPoints
    val cardDesc = if (completed) {
        "$badgeTitle for $dateLabel, ${plan.strandLabel}, Level ${plan.targetBand}, completed, $earnedPoints bonus points earned"
    } else {
        "$badgeTitle for $dateLabel, ${plan.strandLabel}, Level ${plan.targetBand}, earn ${plan.bonusPoints} bonus points"
    }

    val cardBrush = if (completed) {
        Brush.linearGradient(
            colors = listOf(
                QuestColors.BgHill,
                QuestColors.BgMid,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                QuestColors.BgTop,
                QuestColors.BgMid,
            ),
        )
    }
    val borderColor = if (completed) QuestColors.Moss.copy(alpha = 0.7f) else QuestColors.Gold

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(cardBrush)
            .border(2.dp, borderColor, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(18.dp)
            .semantics { contentDescription = cardDesc }
            .testTag("daily_challenge_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header row: Date badge + Bonus Points chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = if (completed) QuestColors.MossLight else QuestColors.Gold,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "${badgeTitle.uppercase()} · ${dateLabel.uppercase()}",
                    color = if (completed) QuestColors.MossLight else QuestColors.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (completed) QuestColors.Moss.copy(alpha = 0.25f) else QuestColors.Gold,
                    )
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (completed) {
                        "✓ +$earnedPoints pts"
                    } else {
                        stringResource(R.string.daily_challenge_bonus_pts, plan.bonusPoints)
                    },
                    color = if (completed) QuestColors.MossLight else QuestColors.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        // Main content: Emoji + Puzzle Kind + Hand-picked subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = plan.strandEmoji, fontSize = 32.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = plan.strandLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = QuestColors.Cream,
                )
                Text(
                    text = if (completed) {
                        stringResource(R.string.daily_challenge_completed)
                    } else {
                        stringResource(R.string.daily_challenge_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = QuestColors.CreamDim,
                )
            }
        }

        // Adaptive difficulty badge + CTA pill
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val adjustIcon = when (plan.adjustment) {
                DifficultyAdjustment.STEPPED_UP, DifficultyAdjustment.HELD_MAX -> Icons.Filled.TrendingUp
                DifficultyAdjustment.EASED_DOWN -> Icons.Filled.TrendingDown
                else -> Icons.Filled.Stars
            }
            val adjustTint = when (plan.adjustment) {
                DifficultyAdjustment.STEPPED_UP, DifficultyAdjustment.HELD_MAX -> QuestColors.MossLight
                DifficultyAdjustment.EASED_DOWN -> QuestColors.ConfettiSky
                else -> QuestColors.Gold
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = adjustIcon,
                    contentDescription = null,
                    tint = adjustTint,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = plan.adjustmentSummary,
                    color = QuestColors.Cream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Bottom Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (completed) Color.White.copy(alpha = 0.10f) else QuestColors.Gold,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (completed) {
                    stringResource(R.string.daily_challenge_replay)
                } else {
                    stringResource(R.string.daily_challenge_start)
                },
                color = if (completed) QuestColors.Cream else QuestColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (completed) QuestColors.Cream else QuestColors.Ink,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp),
            )
        }
    }
}

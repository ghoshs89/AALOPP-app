package com.puzzlequest.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.StrandLabels
import com.puzzlequest.app.engine.DailyActivityRow
import com.puzzlequest.app.engine.Dates
import com.puzzlequest.app.engine.InsightsEngine
import com.puzzlequest.app.engine.LocalDateString
import com.puzzlequest.app.engine.SkillState
import com.puzzlequest.app.engine.SkillStatus
import com.puzzlequest.app.engine.Tally
import com.puzzlequest.app.engine.WeeklyAccuracyRow
import com.puzzlequest.app.ui.theme.QuestColors
import kotlin.math.ceil

@Composable
fun StatTile(
    label: String,
    value: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(QuestColors.VizSurface)
            .padding(14.dp),
    ) {
        Text(text = label, color = QuestColors.VizInk2, fontSize = 13.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = QuestColors.VizInk, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(text = detail, color = QuestColors.VizMuted, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(QuestColors.VizSurface)
            .padding(16.dp),
    ) {
        Text(text = title, color = QuestColors.VizInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = QuestColors.VizInk2, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

data class PercentBarRow(val label: String, val tally: Tally)

@Composable
fun PercentBars(rows: List<PercentBarRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            val pct = row.tally.percent
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = row.label,
                    color = QuestColors.VizInk2,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(126.dp),
                )
                if (pct == null) {
                    Text(text = "No answers yet", color = QuestColors.VizMuted, fontSize = 13.sp)
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((pct.coerceAtLeast(2) / 100f))
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                    .background(QuestColors.VizSeries),
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = QuestColors.VizInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "of ${row.tally.answered}",
                            color = QuestColors.VizMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyColumnsChart(days: List<DailyActivityRow>) {
    val maxRaw = (days.maxOfOrNull { it.answered } ?: 1).coerceAtLeast(1)
    val niceMax = when {
        maxRaw <= 5 -> 5
        maxRaw <= 10 -> 10
        else -> (ceil(maxRaw / 10.0).toInt() * 10)
    }
    val scroll = rememberScrollState()

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Canvas(
                modifier = Modifier
                    .width(560.dp)
                    .height(190.dp),
            ) {
                val left = 30.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = 28.dp.toPx()
                val plotH = size.height - top - bottom
                val slot = (size.width - left) / days.size.coerceAtLeast(1)
                val barW = minOf(26.dp.toPx(), slot - 6.dp.toPx())
                fun yFor(v: Int): Float = top + plotH - (v.toFloat() / niceMax) * plotH

                val mutedPaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#898781")
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.RIGHT
                }
                val datePaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#898781")
                    textSize = 11.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                val fm = mutedPaint.fontMetrics
                val offset = -(fm.ascent + fm.descent) / 2f

                listOf(0, niceMax / 2, niceMax).forEach { t ->
                    val y = yFor(t)
                    drawLine(
                        color = if (t == 0) QuestColors.VizBaseline else QuestColors.VizGrid,
                        start = Offset(left, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(t.toString(), left - 6.dp.toPx(), y + offset, mutedPaint)
                    }
                }

                days.forEachIndexed { i, d ->
                    val x = left + i * slot + (slot - barW) / 2f
                    val wrong = d.answered - d.correct
                    if (d.correct > 0) {
                        val yTop = yFor(d.correct)
                        drawRoundRect(
                            color = QuestColors.VizSeries,
                            topLeft = Offset(x, yTop),
                            size = Size(barW, yFor(0) - yTop),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                    if (wrong > 0) {
                        val yTop = yFor(d.answered)
                        val h = (yFor(d.correct) - yTop - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
                        drawRoundRect(
                            color = QuestColors.VizSeriesLight,
                            topLeft = Offset(x, yTop),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                    if (i % 2 == days.size % 2) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                Dates.shortLabel(d.date),
                                x + barW / 2f,
                                size.height - 6.dp.toPx(),
                                datePaint,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(QuestColors.VizSeries, "Right")
            LegendDot(QuestColors.VizSeriesLight, "Not right")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(text = label, color = QuestColors.VizInk2, fontSize = 12.sp)
    }
}

@Composable
fun WeeklyLineChart(weeks: List<WeeklyAccuracyRow>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp),
    ) {
        val left = 38.dp.toPx()
        val right = 42.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 26.dp.toPx()
        val plotH = size.height - top - bottom
        val step = (size.width - left - right) / (weeks.size - 1).coerceAtLeast(1)
        fun xFor(i: Int): Float = left + i * step
        fun yFor(p: Int): Float = top + plotH - (p / 100f) * plotH

        val mutedPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#898781")
            textSize = 11.sp.toPx()
            textAlign = Paint.Align.RIGHT
        }
        val fm = mutedPaint.fontMetrics
        val offset = -(fm.ascent + fm.descent) / 2f

        listOf(0, 50, 100).forEach { t ->
            val y = yFor(t)
            drawLine(
                color = if (t == 0) QuestColors.VizBaseline else QuestColors.VizGrid,
                start = Offset(left, y),
                end = Offset(size.width - right, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("$t%", left - 6.dp.toPx(), y + offset, mutedPaint)
            }
        }

        val pts = weeks.mapIndexed { idx, w -> idx to w.tally.percent }
        val segments = mutableListOf<MutableList<Pair<Int, Int>>>()
        var current = mutableListOf<Pair<Int, Int>>()
        for ((idx, p) in pts) {
            if (p == null) {
                if (current.isNotEmpty()) {
                    segments += current
                    current = mutableListOf()
                }
            } else {
                current += idx to p
            }
        }
        if (current.isNotEmpty()) segments += current

        for (seg in segments) {
            if (seg.size > 1) {
                val path = Path().apply {
                    moveTo(xFor(seg[0].first), yFor(seg[0].second))
                    for (k in 1 until seg.size) {
                        lineTo(xFor(seg[k].first), yFor(seg[k].second))
                    }
                }
                drawPath(path, color = QuestColors.VizSeries, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
            }
        }

        for ((idx, p) in pts) {
            if (p != null) {
                val c = Offset(xFor(idx), yFor(p))
                drawCircle(color = QuestColors.VizSurface, radius = 5.5.dp.toPx(), center = c)
                drawCircle(color = QuestColors.VizSeries, radius = 4.dp.toPx(), center = c)
            }
        }

        val lastPoint = pts.lastOrNull { it.second != null }
        if (lastPoint != null && lastPoint.second != null) {
            val endPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#0B0B0B")
                textSize = 12.sp.toPx()
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val efm = endPaint.fontMetrics
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "${lastPoint.second}%",
                    xFor(lastPoint.first) + 8.dp.toPx(),
                    yFor(lastPoint.second!!) - (efm.ascent + efm.descent) / 2f,
                    endPaint,
                )
            }
        }

        if (weeks.isNotEmpty()) {
            val firstPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#898781")
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.LEFT
            }
            val lastPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#898781")
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.RIGHT
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(Dates.shortLabel(weeks.first().end), xFor(0), size.height - 4.dp.toPx(), firstPaint)
                canvas.nativeCanvas.drawText(Dates.shortLabel(weeks.last().end), xFor(weeks.lastIndex), size.height - 4.dp.toPx(), lastPaint)
            }
        }
    }
}

private data class StatusMeta(val color: Color, val icon: String, val label: String)

private fun statusMeta(status: SkillStatus): StatusMeta = when (status) {
    SkillStatus.MASTERED -> StatusMeta(QuestColors.VizGood, "✓", "Mastered")
    SkillStatus.REVIEW_DUE -> StatusMeta(QuestColors.VizWarning, "↻", "Review due")
    SkillStatus.LEARNING -> StatusMeta(QuestColors.VizSeries, "●", "Learning")
    SkillStatus.NEW -> StatusMeta(QuestColors.VizBaseline, "○", "Not started")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillMapView(
    skills: List<Skill>,
    states: Map<String, SkillState>,
    today: LocalDateString,
) {
    val layout = remember(skills) { InsightsEngine.skillMapLayout(skills) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    val scrollH = rememberScrollState()

    val nodeW = 212.dp
    val nodeH = 38.dp
    val colGap = 40.dp
    val rowGap = 10.dp
    val totalW = 24.dp + nodeW * layout.layers + colGap * (layout.layers - 1).coerceAtLeast(0)
    val totalH = 24.dp + nodeH * layout.rows + rowGap * (layout.rows - 1).coerceAtLeast(0)

    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(SkillStatus.MASTERED, SkillStatus.REVIEW_DUE, SkillStatus.LEARNING, SkillStatus.NEW).forEach { st ->
                val meta = statusMeta(st)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(meta.color),
                    )
                    Text(text = "${meta.icon} ${meta.label}", color = QuestColors.VizInk2, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap any skill card to see details. Scroll sideways to follow prerequisites left to right.",
            color = QuestColors.VizMuted,
            fontSize = 12.sp,
        )

        selectedCode?.let { code ->
            val sk = skills.firstOrNull { it.code == code }
            val st = states[code]
            if (sk != null && st != null) {
                val meta = statusMeta(st.status)
                val right = st.recent.count { it }
                val review = st.nextReview?.let { nr ->
                    val d = Dates.daysBetween(today, nr)
                    if (d <= 0) "Next review now" else "Next review in $d days"
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuestColors.VizGrid.copy(alpha = 0.55f))
                        .padding(12.dp),
                ) {
                    Text(text = "${meta.icon} ${sk.name}", color = QuestColors.VizInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = buildString {
                            append(meta.label)
                            append(" · ")
                            append(if (st.recent.isEmpty()) "No answers yet" else "$right of last ${st.recent.size} right")
                            if (review != null) append(" · $review")
                        },
                        color = QuestColors.VizInk2,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollH),
        ) {
            Box(modifier = Modifier.size(width = totalW, height = totalH)) {
                val nodeByCode = remember(layout) { layout.nodes.associateBy { it.code } }
                Canvas(modifier = Modifier.size(width = totalW, height = totalH)) {
                    val nw = nodeW.toPx()
                    val nh = nodeH.toPx()
                    val cg = colGap.toPx()
                    val rg = rowGap.toPx()
                    val pad = 12.dp.toPx()

                    layout.edges.forEach { e ->
                        val a = nodeByCode[e.from] ?: return@forEach
                        val b = nodeByCode[e.to] ?: return@forEach
                        val x1 = pad + a.layer * (nw + cg) + nw
                        val y1 = pad + a.row * (nh + rg) + nh / 2f
                        val x2 = pad + b.layer * (nw + cg)
                        val y2 = pad + b.row * (nh + rg) + nh / 2f
                        val mid = (x1 + x2) / 2f
                        val p = Path().apply {
                            moveTo(x1, y1)
                            cubicTo(mid, y1, mid, y2, x2 - 3.dp.toPx(), y2)
                        }
                        drawPath(p, color = QuestColors.VizBaseline.copy(alpha = 0.6f), style = Stroke(width = 1.2.dp.toPx()))
                    }
                }

                layout.nodes.forEach { node ->
                    val state = states[node.code]
                    val meta = statusMeta(state?.status ?: SkillStatus.NEW)
                    val x = 12.dp + (nodeW + colGap) * node.layer
                    val y = 12.dp + (nodeH + rowGap) * node.row
                    val isSelected = selectedCode == node.code

                    Row(
                        modifier = Modifier
                            .padding(start = x, top = y)
                            .size(width = nodeW, height = nodeH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(QuestColors.VizSurface)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) QuestColors.VizSeries else QuestColors.VizGrid,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedCode = node.code },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(nodeH)
                                .background(meta.color),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = meta.icon,
                            color = QuestColors.VizInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = node.name,
                            color = QuestColors.VizInk2,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillProgressSection(
    skills: List<Skill>,
    states: Map<String, SkillState>,
    workingBands: Map<String, Int>,
    today: LocalDateString,
) {
    val counts = SkillStatus.entries.associateWith { st -> states.values.count { it.status == st } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                SkillStatus.MASTERED to Triple("Mastered", QuestColors.Moss.copy(alpha = 0.25f), QuestColors.MossLight),
                SkillStatus.REVIEW_DUE to Triple("Review due", QuestColors.Danger.copy(alpha = 0.25f), QuestColors.Cream),
                SkillStatus.LEARNING to Triple("Learning", QuestColors.Gold.copy(alpha = 0.25f), QuestColors.Gold),
                SkillStatus.NEW to Triple("Not started", Color.White.copy(alpha = 0.1f), QuestColors.CreamDim),
            ).forEach { (st, visual) ->
                Text(
                    text = "${visual.first}: ${counts[st] ?: 0}",
                    color = visual.third,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(visual.second)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            text = "Mastered means at least 4 of the last 5 answers right, on at least 2 different days. Mastered skills come back after 1, 3, 7, 14, then 30 days. A missed review drops a skill one step, and only a miss at the first step moves it back to learning.",
            color = QuestColors.CreamDim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        val statusOrder = mapOf(
            SkillStatus.REVIEW_DUE to 0,
            SkillStatus.LEARNING to 1,
            SkillStatus.MASTERED to 2,
            SkillStatus.NEW to 3,
        )

        StrandLabels.orderedStrands.forEach { strandKey ->
            val strandSkills = skills
                .filter { it.strand == strandKey }
                .sortedBy { statusOrder[states[it.code]?.status ?: SkillStatus.NEW] ?: 4 }
            if (strandSkills.isEmpty()) return@forEach

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuestColors.BgMid),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = StrandLabels.detailedLabelFor(strandKey),
                        style = MaterialTheme.typography.titleLarge,
                        color = QuestColors.Cream,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Level ${workingBands[strandKey] ?: 2} of 5",
                        color = QuestColors.CreamDim,
                        fontSize = 13.sp,
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                strandSkills.forEachIndexed { idx, skill ->
                    val state = states[skill.code]
                    val st = state?.status ?: SkillStatus.NEW
                    val (badgeLabel, badgeBg, badgeFg) = when (st) {
                        SkillStatus.MASTERED -> Triple("Mastered", QuestColors.Moss.copy(alpha = 0.25f), QuestColors.MossLight)
                        SkillStatus.REVIEW_DUE -> Triple("Review due", QuestColors.Danger.copy(alpha = 0.25f), QuestColors.Cream)
                        SkillStatus.LEARNING -> Triple("Learning", QuestColors.Gold.copy(alpha = 0.25f), QuestColors.Gold)
                        SkillStatus.NEW -> Triple("Not started", Color.White.copy(alpha = 0.1f), QuestColors.CreamDim)
                    }
                    val reviewText = state?.nextReview?.let { nr ->
                        val d = Dates.daysBetween(today, nr)
                        when {
                            d <= 0 -> "Review due now"
                            d == 1 -> "Review tomorrow"
                            else -> "Review in $d days"
                        }
                    }.orEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = skill.name, fontWeight = FontWeight.Bold, color = QuestColors.Cream, fontSize = 15.sp)
                                if (skill.isConvention) {
                                    Text(text = "Everyday skill", color = QuestColors.CreamDim, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = badgeLabel,
                                color = badgeFg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val recent = state?.recent.orEmpty()
                            if (recent.isEmpty()) {
                                Text(text = "No answers yet", color = QuestColors.CreamDim, fontSize = 13.sp)
                            } else {
                                Text(
                                    text = recent.joinToString(" ") { if (it) "🟢" else "🔴" },
                                    fontSize = 13.sp,
                                )
                            }
                            if (reviewText.isNotEmpty()) {
                                Text(text = reviewText, color = QuestColors.CreamDim, fontSize = 13.sp)
                            }
                        }
                    }
                    if (idx < strandSkills.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

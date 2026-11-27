package com.puzzlequest.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.R
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.StrandLabels
import com.puzzlequest.app.engine.CategoryProgressOverTime
import com.puzzlequest.app.engine.InsightAttempt
import com.puzzlequest.app.engine.InsightsEngine
import com.puzzlequest.app.engine.LocalDateString
import com.puzzlequest.app.ui.theme.QuestColors
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class ProgressTimeframe(val label: String, val weekly: Boolean, val buckets: Int) {
    DAYS_14("14 days", weekly = false, buckets = 14),
    WEEKS_8("8 weeks", weekly = true, buckets = 8),
}

private enum class ProgressMetric(val label: String) {
    ACCURACY("Accuracy %"),
    ANSWERED("Puzzles solved"),
}

private data class CategoryStyle(val shortLabel: String, val color: Color)

private val categoryStyles: Map<String, CategoryStyle> = mapOf(
    "MEASUREMENT_DATA" to CategoryStyle("Measurement", Color(0xFF4C7AA8)),
    "OPERATIONS_ALGEBRA" to CategoryStyle("Algebra", Color(0xFF4F8F6D)),
    "NUMBER_BASE_TEN" to CategoryStyle("Numbers", Color(0xFFC47D44)),
    "GEOMETRY_SPATIAL" to CategoryStyle("Geometry", Color(0xFF816FAD)),
    "LOGIC_CS" to CategoryStyle("Logic & CS", Color(0xFFC26468)),
    "LANGUAGE" to CategoryStyle("Languages", Color(0xFF478B93)),
    "HUMANITIES" to CategoryStyle("World", Color(0xFFA88844)),
)

private fun styleForStrand(strand: String): CategoryStyle =
    categoryStyles[strand] ?: CategoryStyle(StrandLabels.labelFor(strand), QuestColors.VizSeries)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryProgressChartCard(
    attempts: List<InsightAttempt>,
    questionsById: Map<String, Question>,
    skills: List<Skill>,
    today: LocalDateString,
    modifier: Modifier = Modifier,
) {
    var timeframe by remember { mutableStateOf(ProgressTimeframe.DAYS_14) }
    var metric by remember { mutableStateOf(ProgressMetric.ACCURACY) }
    var selectedStrands by remember { mutableStateOf(StrandLabels.orderedStrands.toSet()) }
    var inspectedBucketIndex by remember { mutableStateOf<Int?>(null) }

    val progressData: CategoryProgressOverTime = remember(attempts, today, timeframe) {
        InsightsEngine.categoryProgressOverTime(
            attempts = attempts,
            questionsById = questionsById,
            skills = skills,
            today = today,
            weekly = timeframe.weekly,
            bucketCount = timeframe.buckets,
        )
    }

    val activeSeries = remember(progressData, selectedStrands) {
        progressData.series.filter { it.strand in selectedStrands }
    }

    val hasAnyData = remember(activeSeries) {
        activeSeries.any { s -> s.tallies.any { it.answered > 0 } }
    }

    // Reset inspection when timeframe changes
    LaunchedEffect(timeframe) {
        inspectedBucketIndex = null
    }

    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(timeframe, metric, selectedStrands, hasAnyData) {
        revealProgress.snapTo(0f)
        revealProgress.animateTo(1f, animationSpec = tween(durationMillis = 280))
    }

    ChartCard(
        title = stringResource(R.string.category_progress_title),
        subtitle = stringResource(R.string.category_progress_subtitle),
        modifier = modifier.testTag("category_progress_chart_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Controls row: Timeframe + Metric segmented toggles
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedControl(
                    options = ProgressTimeframe.entries.map { it.label },
                    selectedIndex = timeframe.ordinal,
                    onSelect = { timeframe = ProgressTimeframe.entries[it] },
                    tagPrefix = "timeframe",
                )
                SegmentedControl(
                    options = ProgressMetric.entries.map { it.label },
                    selectedIndex = metric.ordinal,
                    onSelect = { metric = ProgressMetric.entries[it] },
                    tagPrefix = "metric",
                )
            }

            // Category filter chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val allSelected = selectedStrands.size == StrandLabels.orderedStrands.size
                CategoryToggleChip(
                    label = stringResource(R.string.all_categories),
                    dotColor = null,
                    selected = allSelected,
                    onClick = { selectedStrands = StrandLabels.orderedStrands.toSet() },
                    testTag = "category_chip_all",
                )
                StrandLabels.orderedStrands.forEach { strand ->
                    val style = styleForStrand(strand)
                    val isSelected = strand in selectedStrands
                    CategoryToggleChip(
                        label = style.shortLabel,
                        dotColor = style.color,
                        selected = isSelected,
                        onClick = {
                            selectedStrands = when {
                                allSelected -> setOf(strand)
                                isSelected && selectedStrands.size == 1 -> StrandLabels.orderedStrands.toSet()
                                isSelected -> selectedStrands - strand
                                else -> selectedStrands + strand
                            }
                        },
                        testTag = "category_chip_${strand.lowercase()}",
                    )
                }
            }

            // Main Canvas Graph or Empty State
            if (!hasAnyData) {
                EmptyCategoryGraphState()
            } else {
                CategoryLineCanvas(
                    data = progressData,
                    visibleSeries = activeSeries,
                    metric = metric,
                    revealFraction = revealProgress.value,
                    inspectedIndex = inspectedBucketIndex,
                    onInspectIndex = { inspectedBucketIndex = it },
                )

                // Interactive bucket breakdown panel
                val bucketIdx = inspectedBucketIndex ?: progressData.buckets.lastIndex
                val bucket = progressData.buckets.getOrNull(bucketIdx)
                if (bucket != null) {
                    BucketDetailBanner(
                        bucketLabel = if (timeframe.weekly) {
                            "Week ending ${bucket.label}"
                        } else {
                            bucket.label
                        },
                        series = activeSeries,
                        bucketIndex = bucketIdx,
                        isPinned = inspectedBucketIndex != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(QuestColors.VizGrid.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) QuestColors.VizSurface else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("${tagPrefix}_$idx"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) QuestColors.VizInk else QuestColors.VizInk2,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CategoryToggleChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val bg = when {
        selected && dotColor != null -> dotColor.copy(alpha = 0.14f)
        selected -> QuestColors.VizInk.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val borderColor = when {
        selected && dotColor != null -> dotColor
        selected -> QuestColors.VizInk
        else -> QuestColors.VizGrid
    }
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.2.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) dotColor else dotColor.copy(alpha = 0.35f)),
            )
        }
        Text(
            text = label,
            color = if (selected) QuestColors.VizInk else QuestColors.VizMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyCategoryGraphState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(QuestColors.VizGrid.copy(alpha = 0.35f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = stringResource(R.string.category_progress_empty_title),
            tint = QuestColors.VizMuted,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.category_progress_empty_title),
            color = QuestColors.VizInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.category_progress_empty_tip),
            color = QuestColors.VizInk2,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategoryLineCanvas(
    data: CategoryProgressOverTime,
    visibleSeries: List<com.puzzlequest.app.engine.CategorySeries>,
    metric: ProgressMetric,
    revealFraction: Float,
    inspectedIndex: Int?,
    onInspectIndex: (Int) -> Unit,
) {
    val bucketCount = data.buckets.size.coerceAtLeast(1)
    val maxVolume = remember(visibleSeries) {
        val raw = visibleSeries.maxOfOrNull { s -> s.tallies.maxOfOrNull { it.answered } ?: 0 } ?: 1
        when {
            raw <= 4 -> 4
            raw <= 10 -> 10
            else -> (ceil(raw / 5.0).toInt() * 5)
        }
    }
    val yMax = if (metric == ProgressMetric.ACCURACY) 100f else maxVolume.toFloat()
    val yTicks = if (metric == ProgressMetric.ACCURACY) {
        listOf(0, 50, 100)
    } else {
        listOf(0, maxVolume / 2, maxVolume)
    }

    val chartDescription = stringResource(R.string.category_progress_chart_desc)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp)
            .testTag("category_progress_canvas")
            .semantics { contentDescription = chartDescription },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bucketCount) {
                    detectTapGestures { offset ->
                        val leftPx = 38.dp.toPx()
                        val rightPx = size.width - 14.dp.toPx()
                        val plotW = (rightPx - leftPx).coerceAtLeast(1f)
                        val rel = ((offset.x - leftPx) / plotW).coerceIn(0f, 1f)
                        val idx = (rel * (bucketCount - 1)).roundToInt().coerceIn(0, bucketCount - 1)
                        onInspectIndex(idx)
                    }
                }
                .pointerInput(bucketCount) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val leftPx = 38.dp.toPx()
                        val rightPx = size.width - 14.dp.toPx()
                        val plotW = (rightPx - leftPx).coerceAtLeast(1f)
                        val rel = ((change.position.x - leftPx) / plotW).coerceIn(0f, 1f)
                        val idx = (rel * (bucketCount - 1)).roundToInt().coerceIn(0, bucketCount - 1)
                        onInspectIndex(idx)
                    }
                },
        ) {
            val left = 38.dp.toPx()
            val right = size.width - 14.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = 28.dp.toPx()
            val plotW = (right - left).coerceAtLeast(1f)
            val plotH = (size.height - top - bottom).coerceAtLeast(1f)
            val stepX = if (bucketCount > 1) plotW / (bucketCount - 1) else 0f

            fun xFor(index: Int): Float = left + index * stepX
            fun yFor(value: Float): Float = top + plotH - (value / yMax).coerceIn(0f, 1f) * plotH

            val axisPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#898781")
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.DEFAULT
            }
            val xLabelPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#898781")
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
            }
            val fm = axisPaint.fontMetrics
            val yTextOffset = -(fm.ascent + fm.descent) / 2f

            // Horizontal gridlines & Y ticks
            yTicks.forEach { t ->
                val y = yFor(t.toFloat())
                drawLine(
                    color = if (t == 0) QuestColors.VizBaseline else QuestColors.VizGrid,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = if (metric == ProgressMetric.ACCURACY) "$t%" else t.toString()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, left - 6.dp.toPx(), y + yTextOffset, axisPaint)
                }
            }

            // X-axis date labels (4 evenly spaced labels including first & last)
            val labelIndices = buildSet {
                add(0)
                if (bucketCount > 3) add(bucketCount / 3)
                if (bucketCount > 4) add((bucketCount * 2) / 3)
                add(bucketCount - 1)
            }
            labelIndices.forEach { idx ->
                val x = xFor(idx)
                val paint = Paint(xLabelPaint).apply {
                    textAlign = when (idx) {
                        0 -> Paint.Align.LEFT
                        bucketCount - 1 -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        data.buckets[idx].label,
                        x,
                        size.height - 6.dp.toPx(),
                        paint,
                    )
                }
            }

            // Inspected bucket vertical guide line
            inspectedIndex?.let { idx ->
                val ix = xFor(idx.coerceIn(0, bucketCount - 1))
                drawLine(
                    color = QuestColors.VizInk.copy(alpha = 0.35f),
                    start = Offset(ix, top),
                    end = Offset(ix, top + plotH),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }

            val showAreaFill = visibleSeries.size == 1

            // Animated horizontal reveal clip for smooth graph drawing
            clipRect(
                left = left - 8.dp.toPx(),
                top = 0f,
                right = left + plotW * revealFraction + 8.dp.toPx(),
                bottom = size.height,
            ) {
                visibleSeries.forEach { series ->
                    val color = styleForStrand(series.strand).color
                    val points: List<Pair<Int, Float?>> = series.tallies.mapIndexed { idx, t ->
                        val v = when (metric) {
                            ProgressMetric.ACCURACY -> t.percent?.toFloat()
                            ProgressMetric.ANSWERED -> if (t.answered > 0) t.answered.toFloat() else null
                        }
                        idx to v
                    }

                    // Split into contiguous non-null segments so gaps don't draw misleading drops
                    val segments = mutableListOf<MutableList<Offset>>()
                    var current = mutableListOf<Offset>()
                    for ((idx, v) in points) {
                        if (v == null) {
                            if (current.isNotEmpty()) {
                                segments += current
                                current = mutableListOf()
                            }
                        } else {
                            current += Offset(xFor(idx), yFor(v))
                        }
                    }
                    if (current.isNotEmpty()) segments += current

                    // Draw smooth cubic curves (and subtle gradient fill when focused on 1 category)
                    segments.forEach { seg ->
                        if (seg.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(seg[0].x, seg[0].y)
                                for (k in 1 until seg.size) {
                                    val prev = seg[k - 1]
                                    val curr = seg[k]
                                    val midX = (prev.x + curr.x) / 2f
                                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                                }
                            }
                            if (showAreaFill) {
                                val fillPath = Path().apply {
                                    addPath(linePath)
                                    lineTo(seg.last().x, top + plotH)
                                    lineTo(seg.first().x, top + plotH)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.0f)),
                                        startY = top,
                                        endY = top + plotH,
                                    ),
                                )
                            }
                            drawPath(
                                path = linePath,
                                color = color,
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }

                    // Data point nodes
                    points.forEach { (idx, v) ->
                        if (v != null) {
                            val center = Offset(xFor(idx), yFor(v))
                            val isHighlighted = inspectedIndex == idx
                            val outerRadius = if (isHighlighted) 6.5.dp.toPx() else 4.5.dp.toPx()
                            val innerRadius = if (isHighlighted) 4.5.dp.toPx() else 3.dp.toPx()
                            drawCircle(color = QuestColors.VizSurface, radius = outerRadius, center = center)
                            drawCircle(color = color, radius = innerRadius, center = center)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BucketDetailBanner(
    bucketLabel: String,
    series: List<com.puzzlequest.app.engine.CategorySeries>,
    bucketIndex: Int,
    isPinned: Boolean,
) {
    val activeRows = series.mapNotNull { s ->
        val tally = s.tallies.getOrNull(bucketIndex) ?: return@mapNotNull null
        if (tally.answered == 0) return@mapNotNull null
        Triple(styleForStrand(s.strand), tally.percent ?: 0, tally)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(QuestColors.VizGrid.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bucketLabel,
                color = QuestColors.VizInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isPinned) "Selected point" else "Tap graph to inspect",
                color = QuestColors.VizMuted,
                fontSize = 11.sp,
            )
        }

        if (activeRows.isEmpty()) {
            Text(
                text = "No puzzles answered in this period.",
                color = QuestColors.VizInk2,
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                activeRows.forEach { (style, pct, tally) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(style.color),
                        )
                        Text(
                            text = "${style.shortLabel}: $pct% (${tally.correct}/${tally.answered})",
                            color = QuestColors.VizInk2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

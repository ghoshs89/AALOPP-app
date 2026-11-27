package com.puzzlequest.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.data.model.Visual
import com.puzzlequest.app.ui.theme.QuestColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class CoinStyle(val diameterDp: Float, val fill: Color, val rim: Color, val label: String)

private val coinStyles = mapOf(
    "penny" to CoinStyle(46f, Color(0xFFC77B4A), Color(0xFF8A4F2B), "penny"),
    "nickel" to CoinStyle(51f, Color(0xFFC9CED3), Color(0xFF7D858D), "nickel"),
    "dime" to CoinStyle(43f, Color(0xFFD8DDE1), Color(0xFF7D858D), "dime"),
    "quarter" to CoinStyle(58f, Color(0xFFD3D8DC), Color(0xFF6F7780), "quarter"),
    "loonie" to CoinStyle(64f, Color(0xFFE0B64A), Color(0xFF9C7A1F), "loonie"),
    "toonie" to CoinStyle(68f, Color(0xFFD3D8DC), Color(0xFF9C7A1F), "toonie"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoinsVisualView(visual: Visual.Coins, modifier: Modifier = Modifier) {
    VisualCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                visual.coins.forEach { coin ->
                    val s = coinStyles[coin] ?: coinStyles.getValue("quarter")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(s.diameterDp.dp)) {
                            val r = size.minDimension / 2f
                            val center = Offset(r, r)
                            if (coin == "toonie") {
                                drawCircle(color = Color(0xFFE0B64A), radius = r * 0.94f, center = center)
                                drawCircle(
                                    color = s.rim,
                                    radius = r * 0.94f,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                            val mainRadius = if (coin == "toonie") r * 0.60f else r * 0.94f
                            drawCircle(color = s.fill, radius = mainRadius, center = center)
                            drawCircle(
                                color = s.rim,
                                radius = mainRadius,
                                center = center,
                                style = Stroke(width = if (coin == "toonie") 1.5.dp.toPx() else 2.5.dp.toPx()),
                            )
                            if (coin == "loonie") {
                                val poly = Path()
                                for (k in 0 until 11) {
                                    val a = (k / 11.0) * 2.0 * PI
                                    val px = center.x + (r * 0.84f) * sin(a).toFloat()
                                    val py = center.y - (r * 0.84f) * cos(a).toFloat()
                                    if (k == 0) poly.moveTo(px, py) else poly.lineTo(px, py)
                                }
                                poly.close()
                                drawPath(poly, color = s.rim, style = Stroke(width = 1.5.dp.toPx()))
                            }
                            drawCircle(
                                color = s.rim.copy(alpha = 0.35f),
                                radius = if (coin == "toonie") r * 0.44f else r * 0.72f,
                                center = center,
                                style = Stroke(width = 1.2.dp.toPx()),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = s.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = QuestColors.Ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (visual.currency == "USD") "US coins" else "Canadian coins",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = QuestColors.Ink.copy(alpha = 0.7f),
            )
        }
    }
}

private data class RulerObjStyle(val emoji: String, val fill: Color)

private val rulerObjects = mapOf(
    "car" to RulerObjStyle("🚗", Color(0xFFD84957)),
    "pencil" to RulerObjStyle("✏️", Color(0xFFF2C94C)),
    "crayon" to RulerObjStyle("🖍️", Color(0xFF8A5CD6)),
    "truck" to RulerObjStyle("🚚", Color(0xFF2878E3)),
    "key" to RulerObjStyle("🔑", Color(0xFFE8A33B)),
    "bus" to RulerObjStyle("🚌", Color(0xFF2CB470)),
)

@Composable
fun RulerVisualView(visual: Visual.Ruler, modifier: Modifier = Modifier) {
    val obj = rulerObjects[visual.objectName] ?: rulerObjects.getValue("car")
    val compareObj = visual.compare?.let { rulerObjects[it.objectName] ?: rulerObjects.getValue("pencil") }
    val hasCompare = visual.compare != null && compareObj != null
    val viewHeight = if (hasCompare) 205.dp else 160.dp

    VisualCard(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewHeight)
                .semantics {
                    contentDescription = if (visual.compare != null) {
                        "A ${visual.objectName} and a ${visual.compare.objectName} above a ruler"
                    } else {
                        "A ${visual.objectName} above a ruler"
                    }
                },
        ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas
                val left = 18.dp.toPx()
                val right = (size.width - 18.dp.toPx()).coerceAtLeast(left + 1f)
                val usableW = (right - left).coerceAtLeast(1f)
            val perUnit = usableW / visual.length.coerceAtLeast(1)
            fun xFor(n: Float): Float = left + n * perUnit

            val rulerY = if (hasCompare) size.height * 0.65f else size.height * 0.56f
            val rulerH = size.height - rulerY - 6.dp.toPx()
            val barH = 34.dp.toPx()
            val primaryY = if (hasCompare) rulerY - barH - 16.dp.toPx() else 26.dp.toPx()
            val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

            val emojiPaint = Paint().apply {
                isAntiAlias = true
                textSize = 20.sp.toPx()
                textAlign = Paint.Align.CENTER
            }
            val emojiFm = emojiPaint.fontMetrics
            val emojiOffset = -(emojiFm.ascent + emojiFm.descent) / 2f

            val labelPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 13.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val unitPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 13.sp.toPx()
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    if (visual.unit == "in") "inches" else "centimeters",
                    right,
                    16.dp.toPx(),
                    unitPaint,
                )
            }

            if (hasCompare) {
                val cmp = visual.compare!!
                val cmpY = 22.dp.toPx()
                val sx = xFor(cmp.start.toFloat())
                val ex = xFor(cmp.end.toFloat())
                drawRoundRect(
                    color = compareObj!!.fill,
                    topLeft = Offset(sx, cmpY),
                    size = Size(ex - sx, barH),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
                drawRoundRect(
                    color = QuestColors.Ink,
                    topLeft = Offset(sx, cmpY),
                    size = Size(ex - sx, barH),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(compareObj.emoji, (sx + ex) / 2f, cmpY + barH / 2f + emojiOffset, emojiPaint)
                }
                drawLine(QuestColors.Ink.copy(alpha = 0.55f), Offset(sx, cmpY + barH), Offset(sx, rulerY), strokeWidth = 1.5.dp.toPx(), pathEffect = dashed)
                drawLine(QuestColors.Ink.copy(alpha = 0.55f), Offset(ex, cmpY + barH), Offset(ex, rulerY), strokeWidth = 1.5.dp.toPx(), pathEffect = dashed)
            }

            val sx = xFor(visual.start.toFloat())
            val ex = xFor(visual.end.toFloat())
            drawRoundRect(
                color = obj.fill,
                topLeft = Offset(sx, primaryY),
                size = Size(ex - sx, barH),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )
            drawRoundRect(
                color = QuestColors.Ink,
                topLeft = Offset(sx, primaryY),
                size = Size(ex - sx, barH),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(obj.emoji, (sx + ex) / 2f, primaryY + barH / 2f + emojiOffset, emojiPaint)
            }
            drawLine(QuestColors.Ink, Offset(sx, primaryY + barH), Offset(sx, rulerY), strokeWidth = 1.5.dp.toPx(), pathEffect = dashed)
            drawLine(QuestColors.Ink, Offset(ex, primaryY + barH), Offset(ex, rulerY), strokeWidth = 1.5.dp.toPx(), pathEffect = dashed)

            // Ruler body
            drawRect(
                color = Color(0xFFFFE08A),
                topLeft = Offset(left - 10.dp.toPx(), rulerY),
                size = Size(usableW + 20.dp.toPx(), rulerH),
            )
            drawRect(
                color = Color(0xFF9A7B1F),
                topLeft = Offset(left - 10.dp.toPx(), rulerY),
                size = Size(usableW + 20.dp.toPx(), rulerH),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            for (n in 0..visual.length) {
                val tx = xFor(n.toFloat())
                drawLine(QuestColors.Ink, Offset(tx, rulerY), Offset(tx, rulerY + 20.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(n.toString(), tx, rulerY + rulerH - 8.dp.toPx(), labelPaint)
                }
            }
            if (visual.unit == "in") {
                for (n in 0 until visual.length) {
                    val tx = xFor(n + 0.5f)
                    drawLine(QuestColors.Ink, Offset(tx, rulerY), Offset(tx, rulerY + 11.dp.toPx()), strokeWidth = 1.2.dp.toPx())
                }
            }
        }
    }
}

@Composable
fun NumberLineVisualView(visual: Visual.NumberLine, modifier: Modifier = Modifier) {
    val safeStep = visual.step.coerceAtLeast(1)
    val ticks = buildList {
        var v = visual.min
        var guard = 0
        while (v <= visual.max && guard < 200) {
            add(v)
            v += safeStep
            guard++
        }
    }
    VisualCard(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .semantics {
                    contentDescription = "Number line from ${visual.min} to ${visual.max} with a marker at ${visual.mark}"
                },
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val left = 24.dp.toPx()
            val right = (size.width - 24.dp.toPx()).coerceAtLeast(left + 1f)
            val span = (visual.max - visual.min).coerceAtLeast(1).toFloat()
            fun xFor(v: Double): Float = left + (((v - visual.min) / span).toFloat()) * (right - left)
            val lineY = size.height * 0.60f

            drawLine(
                color = QuestColors.Ink,
                start = Offset(left, lineY),
                end = Offset(right, lineY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val majorPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 13.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val minorPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 11.sp.toPx()
                textAlign = Paint.Align.CENTER
            }

            ticks.forEachIndexed { i, t ->
                val major = i == 0 || i == ticks.lastIndex || t * 2 == visual.min + visual.max
                val tx = xFor(t.toDouble())
                val halfTick = if (major) 13.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = QuestColors.Ink,
                    start = Offset(tx, lineY - halfTick),
                    end = Offset(tx, lineY + halfTick),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                )
                val showLabel = if (ticks.size <= 11) i % 2 == 0 || major else major
                if (showLabel) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            t.toString(),
                            tx,
                            size.height - 6.dp.toPx(),
                            if (major) majorPaint else minorPaint,
                        )
                    }
                }
            }

            val mx = xFor(visual.mark)
            val markerColor = Color(0xFFD84957)
            val pillW = 68.dp.toPx()
            val pillH = 28.dp.toPx()
            val pillPad = 6.dp.toPx()
            val maxPillX = (size.width - pillW - pillPad).coerceAtLeast(pillPad)
            val pillX = (mx - pillW / 2f).coerceIn(pillPad, maxPillX)
            val pillY = 8.dp.toPx()

            drawLine(markerColor, Offset(mx, pillY + pillH), Offset(mx, lineY - 7.dp.toPx()), strokeWidth = 2.5.dp.toPx())
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(mx, lineY))
            drawCircle(color = markerColor, radius = 7.dp.toPx(), center = Offset(mx, lineY))
            drawRoundRect(
                color = markerColor,
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            )

            val pillText = visual.label ?: visual.mark.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            val pillPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textSize = 14.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fm = pillPaint.fontMetrics
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    pillText,
                    pillX + pillW / 2f,
                    pillY + pillH / 2f - (fm.ascent + fm.descent) / 2f,
                    pillPaint,
                )
            }
        }
    }
}

private val stripColors = listOf(
    Color(0xFF2878E3),
    Color(0xFF2CB470),
    Color(0xFFE8A33B),
    Color(0xFF8A5CD6),
)

@Composable
fun FractionStripVisualView(visual: Visual.FractionStrip, modifier: Modifier = Modifier) {
    val barHeightDp = 38.dp
    val gapDp = 12.dp
    val totalHeight = (visual.strips.size * 50 + 16).dp
    VisualCard(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .height(totalHeight)
                .semantics { contentDescription = "Fraction strips comparing equal parts of the same whole" },
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val hasLabels = visual.strips.any { !it.label.isNullOrBlank() }
            val labelSpace = if (hasLabels) 56.dp.toPx() else 0f
            val left = 8.dp.toPx()
            val barW = (size.width - left * 2 - labelSpace).coerceAtLeast(1f)
            val barH = barHeightDp.toPx()
            val gap = gapDp.toPx()

            val labelPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 15.sp.toPx()
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fm = labelPaint.fontMetrics
            val textOffset = -(fm.ascent + fm.descent) / 2f

            visual.strips.forEachIndexed { row, strip ->
                val y = 8.dp.toPx() + row * (barH + gap)
                val cellW = barW / strip.parts.coerceAtLeast(1)
                val fill = stripColors[row % stripColors.size]
                for (i in 0 until strip.parts) {
                    val x = left + i * cellW
                    drawRect(
                        color = if (i < strip.shaded) fill else Color.White,
                        topLeft = Offset(x, y),
                        size = Size(cellW, barH),
                    )
                    drawRect(
                        color = QuestColors.Ink,
                        topLeft = Offset(x, y),
                        size = Size(cellW, barH),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                strip.label?.let { lbl ->
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            lbl,
                            left + barW + 10.dp.toPx(),
                            y + barH / 2f + textOffset,
                            labelPaint,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceVisualView(visual: Visual.Balance, modifier: Modifier = Modifier) {
    VisualCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            visual.scales.forEach { scale ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BalancePan(items = scale.left, modifier = Modifier.weight(1f))
                        Text(
                            text = "=",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = QuestColors.Ink,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        BalancePan(items = scale.right, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(QuestColors.Ink.copy(alpha = 0.8f)),
                    )
                    Canvas(modifier = Modifier.size(width = 24.dp, height = 14.dp)) {
                        val tri = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(tri, color = QuestColors.Ink.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalancePan(items: List<String>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(2.dp, QuestColors.Ink.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                Text(
                    text = item,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = QuestColors.Ink,
                )
            }
        }
    }
}

@Composable
fun PictureGraphVisualView(visual: Visual.PictureGraph, modifier: Modifier = Modifier) {
    VisualCard(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = visual.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QuestColors.Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visual.rows.forEach { row ->
                    val iconCount = (row.count / visual.perIcon.coerceAtLeast(1)).coerceAtLeast(0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = QuestColors.Ink,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(92.dp)
                                .padding(end = 10.dp),
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(QuestColors.Ink.copy(alpha = 0.3f)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (iconCount == 0) " " else visual.icon.repeat(iconCount),
                            fontSize = 22.sp,
                            color = QuestColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Key: each ${visual.icon} = ${visual.perIcon}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = QuestColors.Ink,
            )
        }
    }
}

private val weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun CalendarVisualView(visual: Visual.Calendar, modifier: Modifier = Modifier) {
    val marks = visual.marks.associate { it.day to it.emoji }
    val cells: List<Int?> = List(visual.firstWeekday) { null } + (1..visual.days).toList()
    val rows = cells.chunked(7)

    VisualCard(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = visual.month,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QuestColors.Ink,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weekdayLabels.forEach { dayName ->
                    Text(
                        text = dayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = QuestColors.Ink.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (col in 0 until 7) {
                            val day = week.getOrNull(col)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (day != null) Color.White else Color.Transparent)
                                    .then(
                                        if (day != null) {
                                            Modifier.border(1.dp, QuestColors.Ink.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(top = 2.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (day != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = QuestColors.Ink,
                                            lineHeight = 14.sp,
                                        )
                                        marks[day]?.let { emoji ->
                                            Text(text = emoji, fontSize = 14.sp, lineHeight = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkVisualView(visual: Visual.Network, modifier: Modifier = Modifier) {
    val byId = visual.nodes.associateBy { it.id }
    VisualCard(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(340f / 240f)
                .semantics {
                    contentDescription = "A map of places joined by roads, each road marked with its length"
                },
        ) {
            val sx = size.width / 340f
            val sy = size.height / 240f
            val s = minOf(sx, sy)
            fun px(x: Double): Float = (30f + x.toFloat() * 2.8f) * sx
            fun py(y: Double): Float = (30f + y.toFloat() * 1.8f) * sy

            val weightPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 13f * s
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val weightOffset = -(weightPaint.fontMetrics.ascent + weightPaint.fontMetrics.descent) / 2f

            val nodePaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 17f * s
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val nodeOffset = -(nodePaint.fontMetrics.ascent + nodePaint.fontMetrics.descent) / 2f

            visual.edges.forEach { edge ->
                val a = byId[edge.from] ?: return@forEach
                val b = byId[edge.to] ?: return@forEach
                val ax = px(a.x)
                val ay = py(a.y)
                val bx = px(b.x)
                val by = py(b.y)
                drawLine(
                    color = Color(0xFF9A7B5A),
                    start = Offset(ax, ay),
                    end = Offset(bx, by),
                    strokeWidth = 7f * s,
                    cap = StrokeCap.Round,
                )
                val mx = (ax + bx) / 2f
                val my = (ay + by) / 2f
                drawCircle(color = Color.White, radius = 12f * s, center = Offset(mx, my))
                drawCircle(
                    color = QuestColors.Ink,
                    radius = 12f * s,
                    center = Offset(mx, my),
                    style = Stroke(width = 1.5f * s),
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(edge.weight.toString(), mx, my + weightOffset, weightPaint)
                }
            }

            visual.nodes.forEach { node ->
                val cx = px(node.x)
                val cy = py(node.y)
                drawCircle(color = QuestColors.Cream, radius = 20f * s, center = Offset(cx, cy))
                drawCircle(
                    color = QuestColors.Ink,
                    radius = 20f * s,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5f * s),
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(node.label, cx, cy + nodeOffset, nodePaint)
                }
            }
        }
    }
}

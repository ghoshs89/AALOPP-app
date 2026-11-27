package com.puzzlequest.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.caverock.androidsvg.SVG
import com.puzzlequest.app.data.model.BarItem
import com.puzzlequest.app.data.model.GridCell
import com.puzzlequest.app.data.model.Visual
import com.puzzlequest.app.ui.theme.QuestColors
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VisualCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(QuestColors.VisualTile)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun VisualView(
    visual: Visual,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    when (visual) {
        is Visual.Emoji -> EmojiVisualView(visual.lines, modifier)
        is Visual.Grid -> GridVisualView(visual.rows, visual.highlight, modifier)
        is Visual.Clock -> ClockVisualView(visual.hour, visual.minute, modifier)
        is Visual.BarChart -> BarChartVisualView(visual.title, visual.unit, visual.bars, modifier)
        is Visual.Svg -> InlineSvgVisualView(visual.svg, visual.alt, modifier)
        is Visual.Image -> ImageVisualView(visual, compact, modifier)
        is Visual.Coins -> CoinsVisualView(visual, modifier)
        is Visual.Ruler -> RulerVisualView(visual, modifier)
        is Visual.NumberLine -> NumberLineVisualView(visual, modifier)
        is Visual.FractionStrip -> FractionStripVisualView(visual, modifier)
        is Visual.Balance -> BalanceVisualView(visual, modifier)
        is Visual.PictureGraph -> PictureGraphVisualView(visual, modifier)
        is Visual.Calendar -> CalendarVisualView(visual, modifier)
        is Visual.Network -> NetworkVisualView(visual, modifier)
    }
}

@Composable
private fun EmojiVisualView(lines: List<String>, modifier: Modifier) {
    VisualCard(modifier) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val widest = remember(lines) {
                lines.maxOfOrNull { line ->
                    var em = 0f
                    var i = 0
                    while (i < line.length) {
                        val cp = line.codePointAt(i)
                        em += if (cp > 0x2000) 1.25f else 0.6f
                        i += Character.charCount(cp)
                    }
                    em
                }?.coerceAtLeast(1f) ?: 1f
            }
            val computedSp = (maxWidth.value / widest).coerceIn(16f, 44f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = computedSp.sp,
                        lineHeight = (computedSp * 1.28f).sp,
                        color = QuestColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridVisualView(
    rows: List<List<String>>,
    highlight: GridCell?,
    modifier: Modifier,
) {
    val cols = rows.maxOfOrNull { it.size } ?: 1
    VisualCard(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            rows.forEachIndexed { r, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cellSize = when {
                        cols <= 3 -> 68.dp
                        cols == 4 -> 60.dp
                        else -> 50.dp
                    }
                    row.forEachIndexed { c, cell ->
                        val lit = highlight?.row == r && highlight.col == c
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (lit) QuestColors.Gold else Color.White)
                                .border(
                                    width = 2.dp,
                                    color = if (lit) QuestColors.Gold else QuestColors.Ink.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cell,
                                fontSize = if (cols <= 4) 26.sp else 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = QuestColors.Ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockVisualView(hour: Int, minute: Int, modifier: Modifier) {
    VisualCard(modifier) {
        Canvas(
            modifier = Modifier
                .size(210.dp)
                .semantics { contentDescription = "An analog clock" },
        ) {
            val c = size.minDimension / 2f
            val scale = c / 100f

            fun dialPoint(radius: Float, degrees: Float): Offset {
                val rad = Math.toRadians(degrees.toDouble())
                return Offset(
                    x = c + (radius * scale) * sin(rad).toFloat(),
                    y = c - (radius * scale) * cos(rad).toFloat(),
                )
            }

            drawCircle(color = Color(0xFFFFFDF6), radius = 94f * scale, center = Offset(c, c))
            drawCircle(
                color = QuestColors.Ink,
                radius = 94f * scale,
                center = Offset(c, c),
                style = Stroke(width = 6f * scale),
            )

            for (i in 0 until 60) {
                val outer = dialPoint(88f, i * 6f)
                val inner = dialPoint(if (i % 5 == 0) 80f else 84f, i * 6f)
                drawLine(
                    color = QuestColors.Ink,
                    start = inner,
                    end = outer,
                    strokeWidth = (if (i % 5 == 0) 3f else 1f) * scale,
                )
            }

            val textPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#1D2F4B")
                textSize = 17f * scale
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fm = textPaint.fontMetrics
            val textOffset = -(fm.ascent + fm.descent) / 2f

            drawIntoCanvas { canvas ->
                for (n in 1..12) {
                    val p = dialPoint(66f, n * 30f)
                    canvas.nativeCanvas.drawText(n.toString(), p.x, p.y + textOffset, textPaint)
                }
            }

            val hourAngle = ((hour % 12) + minute / 60f) * 30f
            val minuteAngle = minute * 6f
            val hourTip = dialPoint(36f, hourAngle)
            val minuteTip = dialPoint(54f, minuteAngle)

            drawLine(
                color = QuestColors.Ink,
                start = Offset(c, c),
                end = hourTip,
                strokeWidth = 9f * scale,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFD84957),
                start = Offset(c, c),
                end = minuteTip,
                strokeWidth = 5f * scale,
                cap = StrokeCap.Round,
            )
            drawCircle(color = QuestColors.Ink, radius = 6f * scale, center = Offset(c, c))
        }
    }
}

private val defaultBarColors = listOf(
    Color(0xFFD84957),
    Color(0xFF2878E3),
    Color(0xFF2CB470),
    Color(0xFFE8A33B),
    Color(0xFF8A5CD6),
)

private val namedBarColors = mapOf(
    "red" to Color(0xFFD84957),
    "blue" to Color(0xFF2878E3),
    "green" to Color(0xFF2CB470),
    "yellow" to Color(0xFFF2C94C),
    "orange" to Color(0xFFE8873B),
    "purple" to Color(0xFF8A5CD6),
    "pink" to Color(0xFFE87BB4),
    "white" to Color(0xFFFFFFFF),
    "black" to Color(0xFF26282B),
    "gray" to Color(0xFF9AA1A9),
    "grey" to Color(0xFF9AA1A9),
    "silver" to Color(0xFFC4CAD0),
    "brown" to Color(0xFF8A5A36),
)

private fun barColorFor(label: String, index: Int): Color =
    namedBarColors[label.trim().lowercase()] ?: defaultBarColors[index % defaultBarColors.size]

@Composable
private fun BarChartVisualView(
    title: String,
    unit: String,
    bars: List<BarItem>,
    modifier: Modifier,
) {
    val maxVal = (bars.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
    val step = if (maxVal <= 10) 1 else 2
    val top = (ceil(maxVal.toDouble() / step).toInt() * step).coerceAtLeast(step)
    val ticks = (0..top step step).toList()

    VisualCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QuestColors.Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .height(220.dp)
                    .semantics { contentDescription = "Bar graph: $title" },
            ) {
                val left = 44.dp.toPx()
                val right = size.width - 12.dp.toPx()
                val plotTop = 12.dp.toPx()
                val bottom = 36.dp.toPx()
                val plotHeight = size.height - plotTop - bottom
                val slot = (right - left) / bars.size.coerceAtLeast(1)

                fun yFor(v: Int): Float = plotTop + plotHeight - (v.toFloat() / top) * plotHeight

                val tickPaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#1D2F4B")
                    textSize = 12.sp.toPx()
                    textAlign = Paint.Align.RIGHT
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val labelPaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#1D2F4B")
                    textSize = 13.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val tickFm = tickPaint.fontMetrics
                val tickOffset = -(tickFm.ascent + tickFm.descent) / 2f

                for (t in ticks) {
                    val y = yFor(t)
                    drawLine(
                        color = QuestColors.Ink.copy(alpha = if (t == 0) 0.8f else 0.16f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = if (t == 0) 2.dp.toPx() else 1.dp.toPx(),
                    )
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(t.toString(), left - 8.dp.toPx(), y + tickOffset, tickPaint)
                    }
                }

                bars.forEachIndexed { i, bar ->
                    val x = left + i * slot + slot * 0.2f
                    val barW = slot * 0.6f
                    val barTop = yFor(bar.value)
                    val barH = yFor(0) - barTop
                    drawRoundRect(
                        color = barColorFor(bar.label, i),
                        topLeft = Offset(x, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                    drawRoundRect(
                        color = QuestColors.Ink,
                        topLeft = Offset(x, barTop),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            bar.label,
                            x + barW / 2f,
                            size.height - 10.dp.toPx(),
                            labelPaint,
                        )
                    }
                }
            }
            Text(
                text = "Number of $unit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = QuestColors.Ink.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun InlineSvgVisualView(svgString: String, alt: String, modifier: Modifier) {
    val parsedSvg = remember(svgString) {
        runCatching { SVG.getFromString(svgString) }.getOrNull()
    }
    VisualCard(modifier) {
        if (parsedSvg == null) {
            Text(text = alt, color = QuestColors.Ink, fontSize = 15.sp, textAlign = TextAlign.Center)
        } else {
            val vb = parsedSvg.documentViewBox
            val aspect = if (vb != null && vb.width() > 0f && vb.height() > 0f) {
                (vb.width() / vb.height()).coerceIn(0.6f, 3.6f)
            } else {
                val w = parsedSvg.documentWidth
                val h = parsedSvg.documentHeight
                if (w > 0f && h > 0f) (w / h).coerceIn(0.6f, 3.6f) else 1.8f
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .aspectRatio(aspect)
                    .semantics { contentDescription = alt },
            ) {
                parsedSvg.setDocumentWidth(size.width)
                parsedSvg.setDocumentHeight(size.height)
                drawIntoCanvas { canvas ->
                    parsedSvg.renderToCanvas(canvas.nativeCanvas)
                }
            }
        }
    }
}

@Composable
private fun ImageVisualView(
    visual: Visual.Image,
    compact: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val svgImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    if (compact) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssetPictureTile(visual.src, visual.alt, svgImageLoader, compact = true)
            visual.beside?.let { AssetPictureTile(it.src, it.alt, svgImageLoader, compact = true) }
        }
    } else {
        VisualCard(modifier) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssetPictureTile(visual.src, visual.alt, svgImageLoader, compact = false)
                visual.beside?.let { AssetPictureTile(it.src, it.alt, svgImageLoader, compact = false) }
            }
        }
    }
}

@Composable
private fun AssetPictureTile(
    src: String,
    alt: String,
    imageLoader: ImageLoader,
    compact: Boolean,
) {
    val context = LocalContext.current
    val assetPath = src.trimStart('/')
    val uri = "file:///android_asset/$assetPath"
    Box(
        modifier = Modifier
            .size(
                width = if (compact) 116.dp else 148.dp,
                height = if (compact) 82.dp else 108.dp,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, QuestColors.Ink.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(false)
                .build(),
            imageLoader = imageLoader,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

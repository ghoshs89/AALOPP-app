package com.puzzlequest.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puzzlequest.app.R
import com.puzzlequest.app.ui.theme.QuestColors

data class ScratchStroke(
    val color: Color,
    val points: List<Offset>,
    val widthPx: Float,
)

private val scratchInks = listOf(
    Color(0xFF242E38) to "Blue pen",
    Color(0xFFC26468) to "Red pen",
)

@Composable
fun ScratchpadCanvas(
    strokes: MutableList<ScratchStroke>,
    onStrokesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeInk by remember { mutableStateOf(scratchInks[0].first) }
    var penSeen by remember { mutableStateOf(false) }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var activeWidth by remember { mutableStateOf(10f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(QuestColors.Cream)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            scratchInks.forEachIndexed { idx, (color, label) ->
                val selected = activeInk == color
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 4.dp,
                            color = if (selected) QuestColors.Gold else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { activeInk = color }
                        .testTag("scratch_ink_$idx")
                        .semantics { contentDescription = label },
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.lastIndex)
                        onStrokesChanged(strokes.size)
                    }
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestColors.Ink,
                    contentColor = QuestColors.Cream,
                    disabledContainerColor = QuestColors.Ink.copy(alpha = 0.35f),
                    disabledContentColor = QuestColors.Cream.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("scratch_undo_button"),
            ) {
                Text("↩️ ${stringResource(R.string.undo)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Button(
                onClick = {
                    strokes.clear()
                    onStrokesChanged(0)
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestColors.Ink,
                    contentColor = QuestColors.Cream,
                    disabledContainerColor = QuestColors.Ink.copy(alpha = 0.35f),
                    disabledContentColor = QuestColors.Cream.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("scratch_clear_button"),
            ) {
                Text("🧽 ${stringResource(R.string.clear)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(QuestColors.PaperSoft)
                .border(2.dp, QuestColors.Ink.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("scratchpad_canvas")
                    .semantics { contentDescription = "Scratchpad for working it out" }
                    .pointerInput(activeInk) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.type == PointerType.Stylus) penSeen = true
                            if (down.type == PointerType.Touch && penSeen) return@awaitEachGesture
                            down.consume()
                            activeWidth = if (down.type == PointerType.Stylus) {
                                6f + down.pressure.coerceIn(0f, 1f) * 10f
                            } else {
                                10f
                            }
                            activePoints.clear()
                            activePoints.add(down.position)

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    change.consume()
                                    activePoints.add(change.position)
                                } else {
                                    change.consume()
                                    break
                                }
                            }
                            if (activePoints.isNotEmpty()) {
                                strokes.add(ScratchStroke(activeInk, activePoints.toList(), activeWidth))
                                activePoints.clear()
                                onStrokesChanged(strokes.size)
                            }
                        }
                    },
            ) {
                fun drawStroke(color: Color, points: List<Offset>, widthPx: Float) {
                    if (points.isEmpty()) return
                    if (points.size == 1) {
                        drawCircle(color = color, radius = widthPx / 2f, center = points[0])
                        return
                    }
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = widthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }

                strokes.forEach { s -> drawStroke(s.color, s.points, s.widthPx) }
                if (activePoints.isNotEmpty()) {
                    drawStroke(activeInk, activePoints, activeWidth)
                }
            }

            if (strokes.isEmpty() && activePoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.draw_here_hint),
                    color = QuestColors.Ink.copy(alpha = 0.4f),
                    fontSize = 17.sp,
                )
            }
        }
    }
}

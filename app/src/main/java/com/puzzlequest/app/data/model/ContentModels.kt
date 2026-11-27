@file:OptIn(ExperimentalSerializationApi::class)

package com.puzzlequest.app.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class Skill(
    val code: String,
    val name: String,
    val domain: String,
    val strand: String,
    val tier: Int,
    val isConvention: Boolean = false,
    val prerequisites: List<String> = emptyList(),
)

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val emoji: String,
    val skills: List<String>,
)

@Serializable
data class QuestionSource(
    val kind: String,
    val ref: String? = null,
)

@Serializable
data class ListenPrompt(
    val text: String,
    val lang: String,
)

@Serializable
data class Difficulty(
    val band: Int,
    val basis: String,
    val points: Int? = null,
)

@Serializable
data class Teach(
    val title: String,
    val lines: List<String>,
    val visual: Visual? = null,
)

@Serializable
data class Question(
    val id: String,
    val source: QuestionSource,
    val skills: List<String>,
    val language: String = "en",
    val prompt: String,
    val speech: String? = null,
    val visual: Visual? = null,
    val passage: String? = null,
    val teach: Teach? = null,
    val topic: String? = null,
    val didYouKnow: String? = null,
    val listen: ListenPrompt? = null,
    val voiceAnswer: Boolean? = null,
    val hint: String? = null,
    val answer: Answer,
    val explanation: String,
    val difficulty: Difficulty,
    val reviewStatus: String,
)

@Serializable
@JsonClassDiscriminator("kind")
sealed interface Visual {
    @Serializable
    @SerialName("emoji")
    data class Emoji(val lines: List<String>) : Visual

    @Serializable
    @SerialName("grid")
    data class Grid(
        val rows: List<List<String>>,
        val highlight: GridCell? = null,
    ) : Visual

    @Serializable
    @SerialName("clock")
    data class Clock(val hour: Int, val minute: Int) : Visual

    @Serializable
    @SerialName("barChart")
    data class BarChart(
        val title: String,
        val unit: String,
        val bars: List<BarItem>,
    ) : Visual

    @Serializable
    @SerialName("svg")
    data class Svg(val svg: String, val alt: String) : Visual

    @Serializable
    @SerialName("image")
    data class Image(
        val src: String,
        val alt: String,
        val beside: BesideImage? = null,
    ) : Visual

    @Serializable
    @SerialName("coins")
    data class Coins(
        val currency: String,
        val coins: List<String>,
    ) : Visual

    @Serializable
    @SerialName("ruler")
    data class Ruler(
        val unit: String,
        val length: Int,
        val start: Int,
        val end: Int,
        @SerialName("object") val objectName: String,
        val compare: RulerCompare? = null,
    ) : Visual

    @Serializable
    @SerialName("numberLine")
    data class NumberLine(
        val min: Int,
        val max: Int,
        val step: Int,
        val mark: Double,
        val label: String? = null,
    ) : Visual

    @Serializable
    @SerialName("fractionStrip")
    data class FractionStrip(val strips: List<StripItem>) : Visual

    @Serializable
    @SerialName("balance")
    data class Balance(val scales: List<BalanceScale>) : Visual

    @Serializable
    @SerialName("pictureGraph")
    data class PictureGraph(
        val title: String,
        val icon: String,
        val perIcon: Int,
        val rows: List<PictureGraphRow>,
    ) : Visual

    @Serializable
    @SerialName("calendar")
    data class Calendar(
        val month: String,
        val firstWeekday: Int,
        val days: Int,
        val marks: List<CalendarMark> = emptyList(),
    ) : Visual

    @Serializable
    @SerialName("network")
    data class Network(
        val nodes: List<NetworkNode>,
        val edges: List<NetworkEdge>,
    ) : Visual
}

@Serializable
data class GridCell(val row: Int, val col: Int)

@Serializable
data class BarItem(val label: String, val value: Int)

@Serializable
data class BesideImage(val src: String, val alt: String)

@Serializable
data class RulerCompare(
    val start: Int,
    val end: Int,
    @SerialName("object") val objectName: String,
)

@Serializable
data class StripItem(val parts: Int, val shaded: Int, val label: String? = null)

@Serializable
data class BalanceScale(val left: List<String>, val right: List<String>)

@Serializable
data class PictureGraphRow(val label: String, val count: Int)

@Serializable
data class CalendarMark(val day: Int, val emoji: String)

@Serializable
data class NetworkNode(val id: String, val label: String, val x: Double, val y: Double)

@Serializable
data class NetworkEdge(val from: String, val to: String, val weight: Int)

@Serializable
@JsonClassDiscriminator("type")
sealed interface Answer {
    @Serializable
    @SerialName("choice")
    data class Choice(
        val choices: List<String>,
        val correctIndex: Int,
        val choiceVisuals: List<Visual>? = null,
    ) : Answer

    @Serializable
    @SerialName("numeric")
    data class Numeric(
        val value: Double,
        val unit: String? = null,
    ) : Answer

    @Serializable
    @SerialName("findTheBug")
    data class FindTheBug(
        val solver: String,
        val steps: List<String>,
        val bugIndex: Int,
        val fix: String,
    ) : Answer

    @Serializable
    @SerialName("order")
    data class Order(
        val items: List<String>,
        val correctOrder: List<String>,
        val slots: List<String>? = null,
    ) : Answer
}

object StrandLabels {
    val orderedStrands: List<String> = listOf(
        "MEASUREMENT_DATA",
        "OPERATIONS_ALGEBRA",
        "NUMBER_BASE_TEN",
        "GEOMETRY_SPATIAL",
        "LOGIC_CS",
        "LANGUAGE",
        "HUMANITIES",
    )

    fun labelFor(strand: String): String = when (strand) {
        "MEASUREMENT_DATA" -> "Measurement and data"
        "OPERATIONS_ALGEBRA" -> "Operations and algebra"
        "NUMBER_BASE_TEN" -> "Numbers and place value"
        "GEOMETRY_SPATIAL" -> "Geometry and spatial"
        "LOGIC_CS" -> "Logic and computer science"
        "LANGUAGE" -> "Reading and languages"
        "HUMANITIES" -> "World knowledge"
        else -> strand
    }

    fun detailedLabelFor(strand: String): String = when (strand) {
        "MEASUREMENT_DATA" -> "Measurement and data"
        "OPERATIONS_ALGEBRA" -> "Operations and algebraic thinking"
        "NUMBER_BASE_TEN" -> "Numbers and place value"
        "GEOMETRY_SPATIAL" -> "Geometry and spatial reasoning"
        "LOGIC_CS" -> "Logic and computer science"
        "LANGUAGE" -> "Reading and languages"
        "HUMANITIES" -> "World knowledge"
        else -> strand
    }

    fun confidenceLabel(level: String): String = when (level) {
        "LOW" -> "Not sure"
        "MEDIUM" -> "Kind of sure"
        "HIGH" -> "Very sure"
        else -> level
    }

    fun confidenceFace(level: String): String = when (level) {
        "LOW" -> "😟"
        "MEDIUM" -> "🙂"
        "HIGH" -> "😄"
        else -> ""
    }
}

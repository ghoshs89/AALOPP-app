package com.puzzlequest.app.engine

import com.puzzlequest.app.data.model.Answer
import com.puzzlequest.app.data.model.Question
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
sealed interface PlayerResponse {
    @Serializable
    @SerialName("choice")
    data class Choice(val index: Int, val value: String? = null) : PlayerResponse

    @Serializable
    @SerialName("numeric")
    data class Numeric(val value: Double) : PlayerResponse

    @Serializable
    @SerialName("step")
    data class Step(val index: Int, val secondIndex: Int? = null) : PlayerResponse

    @Serializable
    @SerialName("order")
    data class Order(val order: List<String>) : PlayerResponse
}

object Grading {
    private val json = Json { encodeDefaults = true }

    fun encodeResponse(response: PlayerResponse): String = json.encodeToString(response)

    fun describeResponse(encoded: String, question: Question?): String {
        val response = try {
            json.decodeFromString<PlayerResponse>(encoded)
        } catch (_: IllegalArgumentException) {
            return "Answer unavailable"
        }
        return when (response) {
            is PlayerResponse.Choice -> response.value
                ?: (question?.answer as? Answer.Choice)?.choices?.getOrNull(response.index)
                ?: "Choice ${response.index + 1}"
            is PlayerResponse.Numeric -> listOfNotNull(formatNumeric(response.value), (question?.answer as? Answer.Numeric)?.unit).joinToString(" ")
            is PlayerResponse.Order -> response.order.joinToString(" → ")
            is PlayerResponse.Step -> listOfNotNull(response.index, response.secondIndex).joinToString(" → ") { "Step ${it + 1}" }
        }
    }

    fun isCorrect(question: Question, response: PlayerResponse): Boolean {
        return when (val answer = question.answer) {
            is Answer.Choice -> {
                if (response !is PlayerResponse.Choice) return false
                if (response.value != null && response.value in answer.choices) {
                    response.value == answer.choices[answer.correctIndex]
                } else {
                    response.index == answer.correctIndex
                }
            }
            is Answer.FindTheBug -> {
                // Only the first tap is graded so finding the bug after a nudge does not count toward mastery.
                response is PlayerResponse.Step && response.index == answer.bugIndex
            }
            is Answer.Order -> {
                response is PlayerResponse.Order &&
                    response.order.size == answer.correctOrder.size &&
                    response.order == answer.correctOrder
            }
            is Answer.Numeric -> {
                response is PlayerResponse.Numeric && abs(response.value - answer.value) < 1e-9
            }
        }
    }

    fun formatNumeric(value: Double): String {
        val asLong = value.toLong()
        return if (abs(value - asLong.toDouble()) < 1e-9) asLong.toString() else value.toString()
    }
}

const val BREAK_AFTER_WRONG_IN_A_ROW = 3

fun shouldSuggestBreak(results: List<Boolean>, alreadySuggested: Boolean): Boolean {
    if (alreadySuggested || results.size < BREAK_AFTER_WRONG_IN_A_ROW) return false
    return results.takeLast(BREAK_AFTER_WRONG_IN_A_ROW).all { !it }
}

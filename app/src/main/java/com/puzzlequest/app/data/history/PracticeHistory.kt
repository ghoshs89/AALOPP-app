package com.puzzlequest.app.data.history

import com.puzzlequest.app.engine.PlayerResponse
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val HISTORY_FORMAT = "puzzle-quest-practice-history"
private const val MAX_BYTES = 32 * 1024 * 1024
private val webId = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

@Serializable
data class PracticeHistory(
    val format: String,
    val version: Int,
    val learner: WebLearner,
    val sessions: List<WebSession>,
) {
    val answerCount: Int get() = sessions.sumOf { it.attempts.size }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun read(input: InputStream): PracticeHistory {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(count <= MAX_BYTES - bytes.size()) { "Practice history is too large" }
                bytes.write(buffer, 0, count)
            }

            val history = json.decodeFromString<PracticeHistory>(bytes.toString(Charsets.UTF_8.name()))
            require(history.format == HISTORY_FORMAT && history.version == 1) { "Unsupported practice history" }
            require(webId.matches(history.learner.id) && history.learner.name.isNotBlank())
            val sessionIds = mutableSetOf<String>()
            val attemptIds = mutableSetOf<String>()
            for (session in history.sessions) {
                require(webId.matches(session.id) && sessionIds.add(session.id))
                Instant.parse(session.startedAt).toEpochMilli()
                session.endedAt?.let { Instant.parse(it).toEpochMilli() }
                for (attempt in session.attempts) {
                    require(webId.matches(attempt.id) && attemptIds.add(attempt.id))
                    require(attempt.questionId.isNotBlank())
                    require(attempt.totalMs >= 0 && attempt.listenCount >= 0)
                    require((attempt.firstActionMs ?: 0) >= 0 && (attempt.scratchpadStrokes ?: 0) >= 0)
                    Instant.parse(attempt.answeredAt).toEpochMilli()
                    LocalDate.parse(attempt.practiceDate())
                }
            }
            return history
        }
    }
}

@Serializable
data class WebLearner(val id: String, val name: String)

@Serializable
data class WebSession(
    val id: String,
    val startedAt: String,
    val endedAt: String?,
    val attempts: List<WebAttempt>,
)

@Serializable
data class WebAttempt(
    val id: String,
    val questionId: String,
    val response: PlayerResponse,
    val isCorrect: Boolean,
    val confidence: String? = null,
    val firstActionMs: Int? = null,
    val totalMs: Int,
    val listenCount: Int,
    val answeredAt: String,
    val localDate: String? = null,
    val selectionReason: String? = null,
    val scratchpad: String? = null,
    val scratchpadStrokes: Int? = null,
) {
    fun practiceDate(): String =
        localDate ?: Instant.parse(answeredAt).atOffset(ZoneOffset.UTC).toLocalDate().toString()
}

data class HistoryImportResult(val added: Int, val alreadyPresent: Int)

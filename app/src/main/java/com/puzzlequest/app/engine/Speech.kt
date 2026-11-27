package com.puzzlequest.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import com.puzzlequest.app.data.model.Answer
import com.puzzlequest.app.data.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

data class SpeechPart(val text: String, val lang: String = "en-US")

object SpeechFormatter {
    private val articles = setOf("the", "a", "an", "el", "la", "los", "las", "un", "una", "le", "les", "l", "une", "des")
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonWordRegex = Regex("[^a-z0-9\\s']")

    /** Strips emoji, arrows, and symbols that TTS would read awkwardly. */
    fun speakable(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val skip = cp in 0x1F1E6..0x1FAFF ||
                cp in 0x2190..0x21FF ||
                cp in 0x2600..0x27BF ||
                cp in 0x2B05..0x2B07 ||
                cp == 0xFE0F ||
                cp == 0x200D
            if (skip) {
                sb.append(' ')
            } else {
                sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun questionSpeech(question: Question): String {
        val prompt = question.speech ?: speakable(question.prompt)
        return when (val ans = question.answer) {
            is Answer.FindTheBug -> {
                val stepsText = ans.steps.mapIndexed { idx, s -> "Step ${idx + 1}: ${speakable(s)}" }.joinToString(" ")
                "$prompt $stepsText"
            }
            is Answer.Order -> prompt
            is Answer.Choice -> {
                if (question.language != "en" || ans.choiceVisuals != null) return prompt
                val cleanChoices = ans.choices.map(::speakable)
                if (cleanChoices.any { it.isEmpty() }) return prompt
                val labels = "ABCDE"
                val joined = cleanChoices.mapIndexed { idx, c -> "${labels.getOrElse(idx) { '?' }}: $c." }.joinToString(" ")
                "$prompt $joined"
            }
            is Answer.Numeric -> prompt
        }
    }

    fun questionSpeechParts(question: Question): List<SpeechPart> {
        val parts = mutableListOf(SpeechPart(questionSpeech(question), "en-US"))
        question.listen?.let { parts += SpeechPart(it.text, it.lang) }
        if (question.language != "en" && question.answer is Answer.Choice) {
            val lang = when (question.language) {
                "es" -> "es-ES"
                "fr" -> "fr-FR"
                else -> "en-US"
            }
            for (c in question.answer.choices) {
                parts += SpeechPart(speakable(c), lang)
            }
        }
        return parts
    }

    fun teachSpeechParts(question: Question): List<SpeechPart> {
        val teach = question.teach ?: return emptyList()
        val combined = (listOf(teach.title) + teach.lines).joinToString(". ")
        return listOf(SpeechPart(speakable(combined), "en-US"))
    }

    fun normalizeSpoken(text: String): String {
        val decomposed = Normalizer.normalize(speakable(text), Normalizer.Form.NFD)
        val noDiacritics = diacriticsRegex.replace(decomposed, "")
        return noDiacritics
            .lowercase(Locale.US)
            .replace(nonWordRegex, " ")
            .replace("'", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in articles }
            .joinToString(" ")
    }

    fun matchSpokenChoice(alternatives: List<String>, choices: List<String>): Int? {
        val normalizedChoices = choices.map(::normalizeSpoken)
        for (heard in alternatives.map(::normalizeSpoken)) {
            if (heard.isEmpty()) continue
            val exact = normalizedChoices.indices.filter { normalizedChoices[it] == heard }
            if (exact.size == 1) return exact[0]
            val contained = normalizedChoices.indices.filter { i ->
                val c = normalizedChoices[i]
                c.isNotEmpty() && (" $heard ".contains(" $c ") || " $c ".contains(" $heard "))
            }
            if (contained.size == 1) return contained[0]
        }
        return null
    }
}

class TtsController(context: Context) {
    private var tts: TextToSpeech? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile
    var isReady: Boolean = false
        private set

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                val ok = status == TextToSpeech.SUCCESS
                isReady = ok
                _ready.value = ok
                if (ok) {
                    runCatching { tts?.setSpeechRate(0.95f) }
                }
            }
        }
    }

    fun speak(text: String, lang: String = "en-US") {
        speakParts(listOf(SpeechPart(text, lang)))
    }

    fun speakParts(parts: List<SpeechPart>) {
        val engine = tts ?: return
        if (!isReady || parts.isEmpty()) return
        runCatching {
            engine.stop()
            parts.forEachIndexed { index, part ->
                if (part.text.isBlank()) return@forEachIndexed
                val locale = Locale.forLanguageTag(part.lang)
                engine.language = locale
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(part.text, mode, null, "pq_${System.nanoTime()}_$index")
            }
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
        _ready.value = false
    }
}

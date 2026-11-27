package com.puzzlequest.app.data.content

import android.content.Context
import android.util.Log
import com.puzzlequest.app.data.model.Question
import com.puzzlequest.app.data.model.Skill
import com.puzzlequest.app.data.model.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ContentBank(
    val skills: List<Skill>,
    val themes: List<Theme>,
    val questions: List<Question>,
) {
    val skillsByCode: Map<String, Skill> = skills.associateBy { it.code }
    val questionsById: Map<String, Question> = questions.associateBy { it.id }
    val reviewedQuestions: List<Question> = questions.filter { it.reviewStatus == "reviewed" }

    fun playableThemes(): List<Theme> {
        val ready = reviewedQuestions.flatMap { it.skills }.toSet()
        return themes.filter { t -> t.skills.any { it in ready } }
    }

    companion object {
        private const val TAG = "ContentBank"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        suspend fun load(context: Context): ContentBank = withContext(Dispatchers.IO) {
            val assets = context.assets
            val skillsText = assets.open("skills.json").bufferedReader().use { it.readText() }
            val skills = json.decodeFromString<List<Skill>>(skillsText)

            val themesText = assets.open("themes.json").bufferedReader().use { it.readText() }
            val themes = json.decodeFromString<List<Theme>>(themesText)

            val questionFiles = assets.list("questions")
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                ?: emptyList()

            val allQuestions = mutableListOf<Question>()
            for (file in questionFiles) {
                try {
                    val text = assets.open("questions/$file").bufferedReader().use { it.readText() }
                    allQuestions += json.decodeFromString<List<Question>>(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse questions/$file", e)
                }
            }

            ContentBank(
                skills = skills,
                themes = themes,
                questions = allQuestions,
            )
        }
    }
}

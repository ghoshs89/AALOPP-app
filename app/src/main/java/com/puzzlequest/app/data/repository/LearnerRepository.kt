package com.puzzlequest.app.data.repository

import android.content.Context
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import com.puzzlequest.app.data.db.AttemptEntity
import com.puzzlequest.app.data.db.DailyChallengeRecord
import com.puzzlequest.app.data.db.ParentNoteRecord
import com.puzzlequest.app.data.db.PuzzleQuestDatabase
import com.puzzlequest.app.data.db.StudentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LearnerRepository(context: Context) {
    private val db = PuzzleQuestDatabase(context)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _students = MutableStateFlow<List<StudentRecord>>(emptyList())
    val students: StateFlow<List<StudentRecord>> = _students.asStateFlow()

    private val _voiceOn = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ON, true))
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

    private val _dataVersion = MutableStateFlow(0L)
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    suspend fun refreshStudents() {
        val list = withContext(Dispatchers.IO) { db.getAllStudents() }
        _students.value = list
    }

    suspend fun addStudent(name: String, avatar: String): StudentRecord {
        val created = withContext(Dispatchers.IO) { db.insertStudent(name, avatar) }
        refreshStudents()
        return created
    }

    suspend fun getAttempts(studentId: String): List<AttemptEntity> = withContext(Dispatchers.IO) {
        db.getAttemptsForStudent(studentId)
    }

    suspend fun recordAttempt(attempt: AttemptEntity, sessionStartedAtMs: Long, daily: DailyChallengeRecord?) {
        withContext(Dispatchers.IO + NonCancellable) {
            db.writableDatabase.transaction {
                db.insertAttempt(attempt, sessionStartedAtMs)
                if (daily != null) db.upsertDailyChallenge(daily)
            }
            _dataVersion.update { it + 1 }
        }
    }

    suspend fun endSession(sessionId: String, studentId: String, startedAtMs: Long, endedAtMs: Long) {
        withContext(Dispatchers.IO) {
            db.upsertSession(sessionId, studentId, startedAtMs, endedAtMs)
        }
    }

    suspend fun getNotes(studentId: String): List<ParentNoteRecord> = withContext(Dispatchers.IO) {
        db.getNotesForStudent(studentId)
    }

    suspend fun addNote(studentId: String, body: String): ParentNoteRecord {
        val note = withContext(Dispatchers.IO) { db.insertNote(studentId, body) }
        _dataVersion.value += 1
        return note
    }

    suspend fun deleteNote(noteId: String) {
        withContext(Dispatchers.IO) { db.deleteNote(noteId) }
        _dataVersion.value += 1
    }

    suspend fun getDailyChallenges(studentId: String): List<DailyChallengeRecord> = withContext(Dispatchers.IO) {
        db.getDailyChallengesForStudent(studentId)
    }

    fun setVoiceOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ON, enabled).apply()
        _voiceOn.value = enabled
    }

    fun getParentPin(): String = prefs.getString(KEY_PARENT_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setParentPin(newPin: String) {
        val clean = newPin.filter { it.isDigit() }.take(8)
        if (clean.length >= 4) {
            prefs.edit().putString(KEY_PARENT_PIN, clean).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "puzzle_quest_prefs"
        private const val KEY_VOICE_ON = "voice_on"
        private const val KEY_PARENT_PIN = "parent_pin"
        const val DEFAULT_PIN = "1234"
    }
}

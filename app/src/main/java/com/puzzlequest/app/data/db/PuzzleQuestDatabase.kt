package com.puzzlequest.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.puzzlequest.app.data.history.HistoryImportResult
import com.puzzlequest.app.data.history.PracticeHistory
import com.puzzlequest.app.engine.Grading
import java.time.Instant
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class StudentRecord(
    val id: String,
    val name: String,
    val avatar: String,
    val createdAtMs: Long,
)

data class AttemptEntity(
    val id: String,
    val studentId: String,
    val sessionId: String,
    val questionId: String,
    val responseJson: String,
    val isCorrect: Boolean,
    val confidence: String?,
    val firstActionMs: Int?,
    val totalMs: Int,
    val listenCount: Int,
    val answeredAtMs: Long,
    val localDate: String,
    val selectionReason: String?,
    val scratchpad: String?,
    val scratchpadStrokes: Int?,
)

data class ParentNoteRecord(
    val id: String,
    val studentId: String,
    val body: String,
    val authorLabel: String,
    val createdAtMs: Long,
)

data class DailyChallengeRecord(
    val id: String,
    val studentId: String,
    val localDate: String,
    val questionId: String,
    val strand: String,
    val difficultyBand: Int,
    val completed: Boolean,
    val isCorrect: Boolean,
    val bonusPoints: Int,
    val completedAtMs: Long?,
)

class PuzzleQuestDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE students (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE practice_sessions (
                id TEXT PRIMARY KEY,
                student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                started_at_ms INTEGER NOT NULL,
                ended_at_ms INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE attempts (
                id TEXT PRIMARY KEY,
                student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                session_id TEXT NOT NULL REFERENCES practice_sessions(id) ON DELETE CASCADE,
                question_id TEXT NOT NULL,
                response_json TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                confidence TEXT,
                first_action_ms INTEGER,
                total_ms INTEGER NOT NULL,
                listen_count INTEGER NOT NULL DEFAULT 0,
                answered_at_ms INTEGER NOT NULL,
                local_date TEXT NOT NULL,
                selection_reason TEXT,
                scratchpad TEXT,
                scratchpad_strokes INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_attempts_student_time ON attempts(student_id, answered_at_ms)")
        db.execSQL(
            """
            CREATE TABLE parent_notes (
                id TEXT PRIMARY KEY,
                student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                author_label TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_notes_student_time ON parent_notes(student_id, created_at_ms)")
        createDailyChallengesTable(db)

        // Seed default learner profile matching prisma/seed.ts
        val seed = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("name", "Aishik")
            put("avatar", "🏎️")
            put("created_at_ms", System.currentTimeMillis())
        }
        db.insert("students", null, seed)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createDailyChallengesTable(db)
        }
    }

    private fun createDailyChallengesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_challenges (
                id TEXT PRIMARY KEY,
                student_id TEXT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                local_date TEXT NOT NULL,
                question_id TEXT NOT NULL,
                strand TEXT NOT NULL,
                difficulty_band INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                is_correct INTEGER NOT NULL DEFAULT 0,
                bonus_points INTEGER NOT NULL DEFAULT 0,
                completed_at_ms INTEGER,
                UNIQUE(student_id, local_date)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_daily_challenges_student_date ON daily_challenges(student_id, local_date)",
        )
    }

    fun getAllStudents(): List<StudentRecord> {
        val result = mutableListOf<StudentRecord>()
        readableDatabase.rawQuery(
            "SELECT id, name, avatar, created_at_ms FROM students ORDER BY created_at_ms ASC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += StudentRecord(
                    id = c.getString(0),
                    name = c.getString(1),
                    avatar = c.getString(2),
                    createdAtMs = c.getLong(3),
                )
            }
        }
        return result
    }

    fun insertStudent(name: String, avatar: String): StudentRecord {
        val record = StudentRecord(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            avatar = avatar,
            createdAtMs = System.currentTimeMillis(),
        )
        val cv = ContentValues().apply {
            put("id", record.id)
            put("name", record.name)
            put("avatar", record.avatar)
            put("created_at_ms", record.createdAtMs)
        }
        writableDatabase.insertOrThrow("students", null, cv)
        return record
    }

    fun upsertSession(sessionId: String, studentId: String, startedAtMs: Long, endedAtMs: Long? = null) {
        val db = writableDatabase
        val existing = ContentValues().apply {
            put("id", sessionId)
            put("student_id", studentId)
            put("started_at_ms", startedAtMs)
            if (endedAtMs != null) put("ended_at_ms", endedAtMs)
        }
        db.insertWithOnConflict("practice_sessions", null, existing, SQLiteDatabase.CONFLICT_IGNORE)
        if (endedAtMs != null) {
            val update = ContentValues().apply { put("ended_at_ms", endedAtMs) }
            db.update("practice_sessions", update, "id = ?", arrayOf(sessionId))
        }
    }

    fun insertAttempt(attempt: AttemptEntity, sessionStartedAtMs: Long) {
        upsertSession(attempt.sessionId, attempt.studentId, sessionStartedAtMs)
        check(writableDatabase.replaceOrThrow("attempts", null, attemptValues(attempt)) != -1L)
    }

    private fun attemptValues(attempt: AttemptEntity) = ContentValues().apply {
        put("id", attempt.id)
        put("student_id", attempt.studentId)
        put("session_id", attempt.sessionId)
        put("question_id", attempt.questionId)
        put("response_json", attempt.responseJson)
        put("is_correct", if (attempt.isCorrect) 1 else 0)
        put("confidence", attempt.confidence)
        if (attempt.firstActionMs != null) put("first_action_ms", attempt.firstActionMs)
        put("total_ms", attempt.totalMs)
        put("listen_count", attempt.listenCount)
        put("answered_at_ms", attempt.answeredAtMs)
        put("local_date", attempt.localDate)
        put("selection_reason", attempt.selectionReason)
        put("scratchpad", attempt.scratchpad)
        if (attempt.scratchpadStrokes != null) put("scratchpad_strokes", attempt.scratchpadStrokes)
    }

    fun importPracticeHistory(studentId: String, history: PracticeHistory): HistoryImportResult {
        val db = writableDatabase
        val prefix = "web:$studentId:${history.learner.id}:"
        var added = 0
        db.transaction {
            for (session in history.sessions) {
                val sessionId = prefix + session.id
                val startedAt = Instant.parse(session.startedAt).toEpochMilli()
                // Preserve the recorded end if an older export is imported again.
                upsertSession(sessionId, studentId, startedAt)
                session.endedAt?.let { ended ->
                    val endMs = Instant.parse(ended).toEpochMilli()
                    val values = ContentValues().apply { put("ended_at_ms", endMs) }
                    db.update(
                        "practice_sessions", values,
                        "id = ? AND (ended_at_ms IS NULL OR ended_at_ms < ?)",
                        arrayOf(sessionId, endMs.toString()),
                    )
                }
                for (row in session.attempts) {
                    val attemptId = prefix + row.id
                    val exists = DatabaseUtils.longForQuery(
                        db, "SELECT EXISTS(SELECT 1 FROM attempts WHERE id = ?)", arrayOf(attemptId),
                    ) == 1L
                    if (exists) continue
                    val attempt = AttemptEntity(
                        id = attemptId,
                        studentId = studentId,
                        sessionId = sessionId,
                        questionId = row.questionId,
                        responseJson = Grading.encodeResponse(row.response),
                        isCorrect = row.isCorrect,
                        confidence = row.confidence,
                        firstActionMs = row.firstActionMs,
                        totalMs = row.totalMs,
                        listenCount = row.listenCount,
                        answeredAtMs = Instant.parse(row.answeredAt).toEpochMilli(),
                        localDate = row.practiceDate(),
                        selectionReason = row.selectionReason,
                        scratchpad = row.scratchpad,
                        scratchpadStrokes = row.scratchpadStrokes,
                    )
                    check(db.insertOrThrow("attempts", null, attemptValues(attempt)) != -1L)
                    added++
                }
            }
        }
        return HistoryImportResult(added, history.answerCount - added)
    }

    fun getAttemptsForStudent(studentId: String): List<AttemptEntity> {
        val result = mutableListOf<AttemptEntity>()
        readableDatabase.rawQuery(
            """
            SELECT id, student_id, session_id, question_id, response_json, is_correct,
                   confidence, first_action_ms, total_ms, listen_count, answered_at_ms,
                   local_date, selection_reason, scratchpad, scratchpad_strokes
            FROM attempts
            WHERE student_id = ?
            ORDER BY answered_at_ms ASC
            """.trimIndent(),
            arrayOf(studentId),
        ).use { c ->
            while (c.moveToNext()) {
                result += AttemptEntity(
                    id = c.getString(0),
                    studentId = c.getString(1),
                    sessionId = c.getString(2),
                    questionId = c.getString(3),
                    responseJson = c.getString(4),
                    isCorrect = c.getInt(5) == 1,
                    confidence = if (c.isNull(6)) null else c.getString(6),
                    firstActionMs = if (c.isNull(7)) null else c.getInt(7),
                    totalMs = c.getInt(8),
                    listenCount = c.getInt(9),
                    answeredAtMs = c.getLong(10),
                    localDate = c.getString(11),
                    selectionReason = if (c.isNull(12)) null else c.getString(12),
                    scratchpad = if (c.isNull(13)) null else c.getString(13),
                    scratchpadStrokes = if (c.isNull(14)) null else c.getInt(14),
                )
            }
        }
        return result
    }

    fun getNotesForStudent(studentId: String): List<ParentNoteRecord> {
        val result = mutableListOf<ParentNoteRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, student_id, body, author_label, created_at_ms
            FROM parent_notes
            WHERE student_id = ?
            ORDER BY created_at_ms DESC
            """.trimIndent(),
            arrayOf(studentId),
        ).use { c ->
            while (c.moveToNext()) {
                result += ParentNoteRecord(
                    id = c.getString(0),
                    studentId = c.getString(1),
                    body = c.getString(2),
                    authorLabel = c.getString(3),
                    createdAtMs = c.getLong(4),
                )
            }
        }
        return result
    }

    fun insertNote(studentId: String, body: String, authorLabel: String = "Parent"): ParentNoteRecord {
        val note = ParentNoteRecord(
            id = UUID.randomUUID().toString(),
            studentId = studentId,
            body = body.trim().take(2000),
            authorLabel = authorLabel,
            createdAtMs = System.currentTimeMillis(),
        )
        val cv = ContentValues().apply {
            put("id", note.id)
            put("student_id", note.studentId)
            put("body", note.body)
            put("author_label", note.authorLabel)
            put("created_at_ms", note.createdAtMs)
        }
        writableDatabase.insertOrThrow("parent_notes", null, cv)
        return note
    }

    fun deleteNote(noteId: String) {
        writableDatabase.delete("parent_notes", "id = ?", arrayOf(noteId))
    }

    fun upsertDailyChallenge(record: DailyChallengeRecord) {
        val cv = ContentValues().apply {
            put("id", record.id)
            put("student_id", record.studentId)
            put("local_date", record.localDate)
            put("question_id", record.questionId)
            put("strand", record.strand)
            put("difficulty_band", record.difficultyBand)
            put("completed", if (record.completed) 1 else 0)
            put("is_correct", if (record.isCorrect) 1 else 0)
            put("bonus_points", record.bonusPoints)
            if (record.completedAtMs != null) {
                put("completed_at_ms", record.completedAtMs)
            } else {
                putNull("completed_at_ms")
            }
        }
        check(writableDatabase.replaceOrThrow("daily_challenges", null, cv) != -1L)
    }

    fun getDailyChallengesForStudent(studentId: String): List<DailyChallengeRecord> {
        val result = mutableListOf<DailyChallengeRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, student_id, local_date, question_id, strand, difficulty_band,
                   completed, is_correct, bonus_points, completed_at_ms
            FROM daily_challenges
            WHERE student_id = ?
            ORDER BY local_date DESC
            """.trimIndent(),
            arrayOf(studentId),
        ).use { c ->
            while (c.moveToNext()) {
                result += DailyChallengeRecord(
                    id = c.getString(0),
                    studentId = c.getString(1),
                    localDate = c.getString(2),
                    questionId = c.getString(3),
                    strand = c.getString(4),
                    difficultyBand = c.getInt(5),
                    completed = c.getInt(6) == 1,
                    isCorrect = c.getInt(7) == 1,
                    bonusPoints = c.getInt(8),
                    completedAtMs = if (c.isNull(9)) null else c.getLong(9),
                )
            }
        }
        return result
    }

    companion object {
        private const val DB_NAME = "puzzle_quest.db"
        private const val DB_VERSION = 2
    }
}

package com.puzzlequest.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.puzzlequest.app.data.db.StudentRecord
import com.puzzlequest.app.data.history.PracticeHistory
import com.puzzlequest.app.data.repository.LearnerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportPracticeHistory(student: StudentRecord, repository: LearnerRepository) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PracticeHistory?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        checkNotNull(resolver.openInputStream(uri)).use(PracticeHistory::read)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message = "Couldn’t read this file. Choose the practice history downloaded from the Puzzle Quest website."
                } finally {
                    busy = false
                }
            }
        }
    }
    Button(
        enabled = !busy,
        onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
    ) {
        Text(if (busy) "Working…" else "Import practice history")
    }

    pending?.let { history ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Import practice history?") },
            text = {
                Text("Add ${history.learner.name}’s web history to ${student.name} on this device? " +
                    "The file contains ${history.answerCount} answers. Existing answers will be kept and duplicates skipped.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    busy = true
                    scope.launch {
                        try {
                            val result = repository.importPracticeHistory(student.id, history)
                            message = "Answers added: ${result.added}. Already imported: ${result.alreadyPresent}."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            message = "Couldn’t save practice history. You can retry the import."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    message?.let { status ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(status) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

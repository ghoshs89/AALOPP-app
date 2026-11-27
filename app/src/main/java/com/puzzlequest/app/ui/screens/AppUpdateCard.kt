package com.puzzlequest.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.puzzlequest.app.BuildConfig
import com.puzzlequest.app.ui.theme.QuestColors
import com.puzzlequest.app.update.Update
import com.puzzlequest.app.update.checkForUpdate
import com.puzzlequest.app.update.downloadUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<Update?>(null) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Checking GitHub for updates…") }
    var checks by remember { mutableIntStateOf(0) }
    LaunchedEffect(checks) {
        busy = true
        try {
            update = checkForUpdate()
            status = update?.let { "Puzzle Quest ${it.tag} is available." }
                ?: "Puzzle Quest ${BuildConfig.VERSION_NAME} is up to date."
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "Couldn’t check GitHub. Try again when online."
        } finally {
            busy = false
        }
    }
    Text(status, color = QuestColors.Cream)
    Button(enabled = !busy, onClick = {
        val target = update
        if (target == null) {
            checks++
        } else {
            busy = true
            scope.launch {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        status = "Allow updates from Puzzle Quest in Android settings, then tap Install update again."
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
                    } else {
                        status = "Downloading and verifying ${target.tag}…"
                        val file = downloadUpdate(context, target)
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
                        val install = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(install)
                        status = "Confirm the update in Android’s installer."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    status = "Couldn’t install the verified update. Check your connection and try again."
                } finally {
                    busy = false
                }
            }
        }
    }) {
        Text(if (update == null) "Check for updates" else "Install update")
    }
}

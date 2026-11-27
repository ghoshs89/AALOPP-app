package com.puzzlequest.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.puzzlequest.app.BuildConfig
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val REPO = "ghoshs89/AALOPP-app"
private const val MAX_APK = 128L * 1024 * 1024
private val tagPattern = Regex("""v(0|[1-9]\d{0,5})\.(0|[1-9]\d?)\.(0|[1-9]\d?)""")

data class Update(val tag: String, val code: Int, val size: Long, val sha256: String) {
    val filename get() = "PuzzleQuest-$tag.apk"
}

internal fun latestUpdate(body: String, installedCode: Int): Update? {
    val release = Json.parseToJsonElement(body).jsonObject
    if (release.getValue("draft").jsonPrimitive.boolean || release.getValue("prerelease").jsonPrimitive.boolean) return null
    val tag = release.getValue("tag_name").jsonPrimitive.content
    val match = requireNotNull(tagPattern.matchEntire(tag)) { "Unsupported release version" }
    val (major, minor, patch) = match.destructured
    require(major.toInt() <= 200000)
    val code = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    if (code <= installedCode) return null
    val asset = release.getValue("assets").jsonArray.map { it.jsonObject }
        .single { it["name"]?.jsonPrimitive?.content == "PuzzleQuest-$tag.apk" }
    val size = asset.getValue("size").jsonPrimitive.long
    val digest = asset.getValue("digest").jsonPrimitive.content
    require(size in 1..MAX_APK && digest.matches(Regex("sha256:[0-9a-f]{64}")))
    return Update(tag, code, size, digest.removePrefix("sha256:"))
}

private fun connection(url: String): HttpsURLConnection =
    (URL(url).openConnection() as HttpsURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "PuzzleQuest-Android/" + BuildConfig.VERSION_NAME)
    }

suspend fun checkForUpdate(): Update? = withContext(Dispatchers.IO) {
    val conn = connection("https://api.github.com/repos/$REPO/releases/latest")
    try {
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        latestUpdate(body, BuildConfig.VERSION_CODE)
    } finally {
        conn.disconnect()
    }
}

@Suppress("DEPRECATION")
private fun signingCertificates(info: PackageInfo): List<Signature> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = info.signingInfo
        if (signingInfo != null) {
            val certs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            if (!certs.isNullOrEmpty()) return certs.toList()
        }
    }
    return info.signatures?.toList().orEmpty()
}

@Suppress("DEPRECATION")
private fun verify(context: Context, file: File, update: Update) {
    require(file.length() == update.size)
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(65536)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256)
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val archive = requireNotNull(pm.getPackageArchiveInfo(file.path, flags))
    val installed = pm.getPackageInfo(context.packageName, flags)
    require(archive.packageName == context.packageName &&
        PackageInfoCompat.getLongVersionCode(archive) == update.code.toLong() &&
        update.code > PackageInfoCompat.getLongVersionCode(installed))
    val archiveCert = signingCertificates(archive).singleOrNull()
    val installedCert = signingCertificates(installed).singleOrNull()
    require(archiveCert != null && archiveCert == installedCert)
}

suspend fun downloadUpdate(context: Context, update: Update): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val apk = File(directory, update.filename)
    if (apk.exists() && runCatching { verify(context, apk, update) }.isSuccess) return@withContext apk
    val tmp = File.createTempFile("download-", ".apk", directory)
    val conn = connection("https://github.com/$REPO/releases/download/${update.tag}/${update.filename}")
    try {
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(65536)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    require(total <= update.size)
                    output.write(buf, 0, n)
                }
            }
        }
        verify(context, tmp, update)
        if (apk.exists()) apk.delete()
        if (!tmp.renameTo(apk)) {
            tmp.copyTo(apk, overwrite = true)
            verify(context, apk, update)
        }
        apk
    } finally {
        conn.disconnect()
        tmp.delete()
    }
}

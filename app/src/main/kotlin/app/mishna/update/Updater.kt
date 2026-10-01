package app.mishna.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import app.mishna.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What the "check for update" row shows. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: Release) : UpdateStatus
    data class Downloading(val release: Release, val progress: Float) : UpdateStatus
    data class Failed(val page: String, val reason: String) : UpdateStatus
}

/** A newer build published on GitHub Releases. */
data class Release(val build: Int, val name: String, val apkUrl: String, val page: String)

/** "Check for update" in Settings: reads the latest GitHub release and installs its APK. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Yairkad/Mishna/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Yairkad/Mishna/releases/latest"

    /** The latest release if it is newer than this app, null if up to date. Throws on network errors. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "mishna-app")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        if (conn.responseCode != 200) error("GitHub החזיר קוד ${conn.responseCode}")
        val body = conn.inputStream.use { it.readBytes().decodeToString() }
        val json = Json.parseToJsonElement(body).jsonObject
        val tag = json["tag_name"]!!.jsonPrimitive.content
        val build = tag.substringAfter("build-").toIntOrNull() ?: return@withContext null
        if (build <= BuildConfig.VERSION_CODE) return@withContext null
        val apk = json["assets"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }
        Release(
            build = build,
            name = json["name"]?.jsonPrimitive?.content ?: tag,
            apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
            page = json["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE,
        )
    }

    /**
     * Downloads the APK with Android's download manager (it follows GitHub's redirects and
     * survives brief network drops), reporting progress 0..1. Throws with a readable reason.
     */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("אין גישה לאחסון")
        val file = File(dir, "mishna-${release.build}.apk")
        dir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
        val dm = context.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle("משנה יומית · ${release.name}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file)),
        )
        while (true) {
            delay(300)
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) error("ההורדה בוטלה")
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) progress(done.toFloat() / total)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> return@withContext file
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        error("ההורדה נכשלה (קוד $reason)")
                    }
                }
            }
        }
        @Suppress("UNREACHABLE_CODE") file
    }

    /** Android asks once per app for permission to install from it. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Opens Android's installer; the user confirms there. Data is kept because every build uses the same key. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openPage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

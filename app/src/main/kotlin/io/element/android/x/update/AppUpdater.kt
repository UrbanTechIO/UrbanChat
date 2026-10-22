/*
 * UrbanChat: checks GitHub for a newer release, downloads it and hands it to the package installer.
 */

package io.element.android.x.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import io.element.android.x.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val downloaded: Long, val total: Long) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState
    data class Failed(val release: ReleaseInfo, val message: String) : UpdateState
}

object AppUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/UrbanTechIO/UrbanChat/releases/latest"
    private const val PREFS = "app_update"
    private const val KEY_LAST_CHECK = "last_check_v2"
    private const val KEY_SKIPPED = "skipped_version"
    private const val CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
    const val ACTION_INSTALL_RESULT = "io.element.android.x.update.INSTALL_RESULT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Looks for a newer release, at most every few hours unless [force] is set. Debug builds never update themselves. */
    fun check(context: Context, force: Boolean = false) {
        if (BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MILLIS) return
        if (force) {
            if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
            _state.value = UpdateState.Checking
        } else if (_state.value != UpdateState.Idle) {
            return
        }
        scope.launch {
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    prefs.edit { putLong(KEY_LAST_CHECK, now) }
                    val newer = release != null && isNewer(release.version, BuildConfig.VERSION_NAME)
                    val skipped = release != null && release.version == prefs.getString(KEY_SKIPPED, null)
                    if (release != null && newer && (force || !skipped)) {
                        _state.value = UpdateState.Available(release)
                    } else {
                        _state.value = if (force) UpdateState.UpToDate(BuildConfig.VERSION_NAME) else UpdateState.Idle
                    }
                }
                .onFailure {
                    Timber.w(it, "Could not check for a new release")
                    _state.value = if (force) UpdateState.CheckFailed else UpdateState.Idle
                }
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    fun skipVersion(context: Context) {
        val release = releaseOf(_state.value) ?: return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SKIPPED, release.version) }
        _state.value = UpdateState.Idle
    }

    fun cancelDownload() {
        val release = releaseOf(_state.value) ?: return
        downloadJob?.cancel()
        _state.value = UpdateState.Available(release)
    }

    fun reportInstallFailure(message: String) {
        val release = releaseOf(_state.value) ?: return
        _state.value = UpdateState.Failed(release, message)
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun startUpdate(context: Context) {
        val appContext = context.applicationContext
        val release = releaseOf(_state.value) ?: return
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release)
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            runCatching {
                val file = download(appContext, release)
                _state.value = UpdateState.Installing(release)
                install(appContext, file)
            }.onFailure {
                if (it is CancellationException) return@onFailure
                Timber.e(it, "Update failed")
                _state.value = UpdateState.Failed(release, it.message ?: "Update failed")
            }
        }
    }

    private fun releaseOf(state: UpdateState): ReleaseInfo? = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.NeedsInstallPermission -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }

    private fun fetchLatest(): ReleaseInfo? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            var best: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (!name.endsWith(".apk")) continue
                if (abi.isNotEmpty() && name.contains(abi)) {
                    best = asset
                    break
                }
                if (best == null || name.contains("universal")) best = asset
            }
            val asset = best ?: return null
            return ReleaseInfo(
                version = version,
                notes = json.optString("body"),
                apkUrl = asset.getString("browser_download_url"),
                apkSize = asset.optLong("size"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(context: Context, release: ReleaseInfo): File {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, "UrbanChat-${release.version}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            check(connection.responseCode == 200) { "Download failed (HTTP ${connection.responseCode})" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var downloaded = 0L
            _state.value = UpdateState.Downloading(release, 0, total)
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (coroutineContext.isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport > 256 * 1024) {
                            lastReport = downloaded
                            _state.value = UpdateState.Downloading(release, downloaded, total)
                        }
                    }
                }
            }
            if (!coroutineContext.isActive) throw CancellationException()
            check(total <= 0 || downloaded == total) { "Download incomplete" }
            return file
        } finally {
            connection.disconnect()
        }
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    internal fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

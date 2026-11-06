/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.autodownload

import android.content.Context
import android.net.ConnectivityManager
import io.element.android.libraries.urbanextras.UrbanExtras

/** The kinds of media the rules can tell apart. */
enum class AutoDownloadKind { Photos, Videos }

/** When received media of one kind is saved to the Gallery without being opened. */
enum class AutoDownloadMode { Always, WifiOnly, Never }

/**
 * Rules for the automatic download of received photos and videos (the "Enable Download to Gallery" setting): always, only
 * on Wi-Fi (or any network that is not metered), or never. Media that is not downloaded automatically still opens, and is
 * fetched from the server when it is opened. Kept on this phone.
 */
object AutoDownloadRules {
    private fun prefs() = UrbanExtras.context.getSharedPreferences("urban_auto_download", Context.MODE_PRIVATE)

    fun mode(kind: AutoDownloadKind): AutoDownloadMode {
        val saved = prefs().getString(kind.name, null)
        return AutoDownloadMode.entries.firstOrNull { it.name == saved } ?: AutoDownloadMode.Always
    }

    fun setMode(kind: AutoDownloadKind, mode: AutoDownloadMode) {
        prefs().edit().putString(kind.name, mode.name).apply()
    }

    /** Whether received media of [kind] may be downloaded automatically right now. */
    fun allowedNow(kind: AutoDownloadKind): Boolean = when (mode(kind)) {
        AutoDownloadMode.Always -> true
        AutoDownloadMode.Never -> false
        AutoDownloadMode.WifiOnly -> {
            val manager = UrbanExtras.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.isActiveNetworkMetered.not()
        }
    }
}

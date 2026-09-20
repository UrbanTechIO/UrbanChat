/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.log

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.call.api.CallLog
import io.element.android.features.call.api.CallLogEntry
import io.element.android.features.call.api.CallLogType
import io.element.android.libraries.androidutils.hash.hash
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private const val MAX_ENTRIES = 300

@Serializable
private data class StoredEntry(
    val id: String,
    val roomId: String,
    val type: String,
    val audio: Boolean,
    val startedAt: Long,
    val duration: Long? = null,
    val name: String? = null,
    val eventId: String? = null,
)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultCallLog(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : CallLog {
    private val store = preferenceDataStoreFactory.create("call_log")
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(sessionId: SessionId) = stringPreferencesKey("entries_${sessionId.value.hash().take(16)}")

    override fun entries(sessionId: SessionId): Flow<List<CallLogEntry>> {
        val key = key(sessionId)
        return store.data.map { prefs -> decode(sessionId, prefs[key]) }
    }

    override suspend fun add(entry: CallLogEntry) {
        runCatchingExceptions {
            val key = key(entry.sessionId)
            store.edit { prefs ->
                val current = decode(entry.sessionId, prefs[key])
                val updated = (listOf(entry) + current.filterNot { it.id == entry.id }).take(MAX_ENTRIES)
                prefs[key] = json.encodeToString(updated.map { it.toStored() })
            }
        }.onFailure { Timber.e(it, "Failed to save call log entry") }
    }

    override suspend fun clear(sessionId: SessionId) {
        runCatchingExceptions { store.edit { it.remove(key(sessionId)) } }
    }

    private fun decode(sessionId: SessionId, raw: String?): List<CallLogEntry> {
        if (raw == null) return emptyList()
        return runCatchingExceptions { json.decodeFromString<List<StoredEntry>>(raw).map { it.toEntry(sessionId) } }
            .onFailure { Timber.e(it, "Failed to decode call log") }
            .getOrDefault(emptyList())
    }

    private fun CallLogEntry.toStored() = StoredEntry(id, roomId.value, type.name, isAudioCall, startedAtMillis, durationSeconds, otherName, eventId)

    private fun StoredEntry.toEntry(sessionId: SessionId) = CallLogEntry(
        id = id,
        sessionId = sessionId,
        roomId = RoomId(roomId),
        type = CallLogType.entries.firstOrNull { it.name == type } ?: CallLogType.Outgoing,
        isAudioCall = audio,
        startedAtMillis = startedAt,
        durationSeconds = duration,
        otherName = name,
        eventId = eventId,
    )
}

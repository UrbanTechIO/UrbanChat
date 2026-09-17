/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.api.notifications.conversations

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId

/**
 * The id used for the per-room Android shortcut (conversation notification bubbles, launcher
 * long-press shortcuts, and Direct Share targets all key off the same shortcut id).
 */
fun createRoomShortcutId(sessionId: SessionId, roomId: RoomId): String = "$sessionId-$roomId"

/**
 * Must match the `<category>` declared on the `<share-target>` in `app/src/main/res/xml/shortcuts.xml`
 * — that's what tells Android's Sharesheet which of our dynamic shortcuts are eligible to show up
 * as Direct Share targets (as opposed to just being launcher/bubble shortcuts).
 */
const val ROOM_SHARE_TARGET_CATEGORY = "io.element.android.category.ROOM_SHARE_TARGET"

/**
 * The reverse of [createRoomShortcutId]. A [RoomId] always starts with `!`, and a [SessionId]
 * (a Matrix user id) never contains that character, so the first `!` in the shortcut id
 * unambiguously marks where the room id starts (with the `-` right before it being the separator).
 */
fun parseRoomShortcutId(shortcutId: String): Pair<SessionId, RoomId>? {
    val bangIndex = shortcutId.indexOf('!')
    if (bangIndex < 2 || shortcutId[bangIndex - 1] != '-') return null
    return runCatching {
        val sessionId = SessionId(shortcutId.substring(0, bangIndex - 1))
        val roomId = RoomId(shortcutId.substring(bangIndex))
        sessionId to roomId
    }.getOrNull()
}

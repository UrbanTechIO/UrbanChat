/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the "frosted glass" appearance option is active for the current screen. Read by leaf
 * composables (e.g. the message composer's text input pill) that would otherwise paint a fully
 * opaque background, masking the blurred backdrop drawn by an ancestor. Kept as a composition
 * local rather than a threaded parameter since the relevant leaves live several modules away from
 * where the setting is read (`features/preferences`) and rendered (`features/messages`).
 */
val LocalIsFrostedGlassEnabled = compositionLocalOf { false }

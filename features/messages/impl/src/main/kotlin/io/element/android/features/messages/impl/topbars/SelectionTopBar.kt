/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings

/** Replaces the chat's top bar while messages are ticked: how many, a way out, and the delete button. */
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onCloseClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(CommonStrings.action_close),
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(CommonPlurals.common_selected_count, selectedCount, selectedCount),
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
            )
        },
        actions = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = CompoundIcons.Delete(),
                    contentDescription = stringResource(CommonStrings.action_delete),
                    tint = ElementTheme.colors.iconCriticalPrimary,
                )
            }
        },
    )
}

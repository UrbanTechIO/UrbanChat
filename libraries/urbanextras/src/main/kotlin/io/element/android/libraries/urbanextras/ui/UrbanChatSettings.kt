/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.disappearing.DisappearingCodec
import io.element.android.libraries.urbanextras.disappearing.DisappearingMessages
import io.element.android.libraries.urbanextras.look.ChatLooks

private val TINTS = listOf(
    0xFF2E7DF6, 0xFF7C4DFF, 0xFFE91E63, 0xFFFF5252, 0xFFFF9800, 0xFFFFC107, 0xFF4CAF50, 0xFF00BCD4, 0xFF607D8B, 0xFF000000,
).map { it.toInt() }

/** The row of ready-made backgrounds in the theme settings: one pick applies to every chat that has not chosen its own. */
@Composable
fun GlobalBackgroundStyles(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ChatLooks.get(context) }
    val looks by store.looks.collectAsState()
    val selected = looks[ChatLooks.GLOBAL_KEY]?.background
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Background style", style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textPrimary)
        Text("Ready-made looks for every chat (a chat can still choose its own).", color = ElementTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(if (selected == null) 3.dp else 1.dp, if (selected == null) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderDisabled, CircleShape)
                    .clickable { store.update(ChatLooks.GLOBAL_KEY) { it.copy(background = null) } },
                contentAlignment = Alignment.Center,
            ) { Text("None", style = ElementTheme.typography.fontBodyXsRegular, color = ElementTheme.colors.textSecondary) }
            ChatLooks.BACKGROUND_PRESETS.forEach { name ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(presetBackgroundBrush(name))
                            .border(if (selected == name) 3.dp else 1.dp, if (selected == name) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderDisabled, CircleShape)
                            .clickable { store.update(ChatLooks.GLOBAL_KEY) { it.copy(background = name) } },
                    )
                    Text(presetBackgroundLabel(name), style = ElementTheme.typography.fontBodyXsRegular, color = ElementTheme.colors.textSecondary)
                }
            }
        }
    }
}

/**
 * The rows added to a chat's details page: how this chat looks, disappearing messages and its notification sound.
 * [onSendText] sends a text message in this chat (used to tell the other people about a disappearing-messages change).
 */
@Composable
fun UrbanChatSettingsCategory(roomId: String, onSendText: (String) -> Unit) {
    val context = LocalContext.current
    val looks by remember { ChatLooks.get(context).looks }.collectAsState()
    val rules by remember { DisappearingMessages.get(context).rules }.collectAsState()
    var showLook by remember { mutableStateOf(false) }
    var showDisappearing by remember { mutableStateOf(false) }
    var showCustomDuration by remember { mutableStateOf(false) }
    val rule = rules[roomId]?.takeIf { it.seconds > 0 }
    val look = looks[roomId]

    PreferenceCategory(title = "UrbanChat") {
        ListItem(
            content = { Text("Chat look") },
            supportingContent = { Text(if (look == null) "Default" else "Customised") },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
            onClick = { showLook = true },
        )
        ListItem(
            content = { Text("Disappearing messages") },
            supportingContent = { Text(rule?.let { DisappearingMessages.label(it.seconds) } ?: "Off") },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
            onClick = { showDisappearing = true },
        )
    }
    if (showLook) ChatLookSheet(roomId = roomId, onDismiss = { showLook = false })
    if (showDisappearing) {
        DisappearingSheet(
            current = rule?.seconds ?: 0L,
            onSelect = { seconds ->
                showDisappearing = false
                DisappearingMessages.get(context).setLocal(roomId, seconds)
                onSendText(DisappearingCodec.announcementText(seconds))
            },
            onCustom = {
                showDisappearing = false
                showCustomDuration = true
            },
            onDismiss = { showDisappearing = false },
        )
    }
    if (showCustomDuration) {
        DurationPickerSheet(
            onConfirm = { seconds ->
                showCustomDuration = false
                DisappearingMessages.get(context).setLocal(roomId, seconds)
                onSendText(DisappearingCodec.announcementText(seconds))
            },
            onDismiss = { showCustomDuration = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisappearingSheet(current: Long, onSelect: (Long) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("Disappearing messages", style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "New messages in this chat disappear after the time you choose. Everyone in the chat is told, and UrbanChat on their phones follows the same rule.",
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            DisappearingMessages.OPTIONS.forEach { (label, seconds) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(seconds) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == seconds, onClick = { onSelect(seconds) })
                    Text(label, color = ElementTheme.colors.textPrimary)
                }
            }
            val isCustom = DisappearingMessages.OPTIONS.none { it.second == current }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCustom() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isCustom, onClick = { onCustom() })
                Text(
                    if (isCustom) "Custom: ${DisappearingMessages.label(current)}" else "Custom hours and minutes…",
                    color = ElementTheme.colors.textPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatLookSheet(roomId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ChatLooks.get(context) }
    val looks by store.looks.collectAsState()
    val look = looks[roomId] ?: io.element.android.libraries.urbanextras.look.ChatLook()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            store.importPhoto(roomId, uri)?.let { path -> store.update(roomId) { it.copy(background = "photo", photoPath = path) } }
        }
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            store.update(roomId) { it.copy(soundUri = picked?.toString()) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Chat look", style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Text("Only on your phone.", color = ElementTheme.colors.textSecondary)

            Spacer(Modifier.height(12.dp))
            Text("Tint", style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (look.tintArgb == null) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderDisabled, CircleShape)
                        .clickable { store.update(roomId) { it.copy(tintArgb = null) } },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = ElementTheme.colors.textSecondary) }
                TINTS.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (look.tintArgb == argb) 3.dp else 1.dp,
                                color = if (look.tintArgb == argb) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderDisabled,
                                shape = CircleShape,
                            )
                            .clickable { store.update(roomId) { it.copy(tintArgb = argb) } },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            var hue by remember { mutableStateOf(0f) }
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    store.update(roomId) { look -> look.copy(tintArgb = Color.hsv(it * 360f, 0.7f, 0.9f).toArgb()) }
                },
            )

            Spacer(Modifier.height(8.dp))
            Text("Background", style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = look.background == null,
                    onClick = { store.update(roomId) { it.copy(background = null, photoPath = null) } },
                    label = { Text("Default") },
                )
                ChatLooks.BACKGROUND_PRESETS.forEach { name ->
                    FilterChip(
                        selected = look.background == name,
                        onClick = { store.update(roomId) { it.copy(background = name, photoPath = null) } },
                        label = { Text(presetBackgroundLabel(name)) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(presetBackgroundBrush(name))
                            )
                        },
                    )
                }
                FilterChip(
                    selected = look.background == "photo",
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    label = { Text("My photo…") },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Bubble see-through", style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textPrimary)
            Slider(
                value = look.bubbleOpacity ?: 0f,
                onValueChange = { v -> store.update(roomId) { it.copy(bubbleOpacity = v) } },
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification sound", style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textPrimary)
                    Text(
                        text = look.soundUri?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) } ?: "Default",
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                TextButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, look.soundUri?.let(Uri::parse))
                        soundPicker.launch(intent)
                    }
                ) { Text("Change") }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { store.reset(roomId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to default", color = ElementTheme.colors.textCriticalPrimary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

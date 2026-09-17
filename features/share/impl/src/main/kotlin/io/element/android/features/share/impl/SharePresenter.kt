/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.features.lockscreen.impl.biometric.ChatLockUnlockTracker
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.features.lockscreen.impl.biometric.rememberDeviceAuthState
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditor
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditorState
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEdits
import io.element.android.features.messages.impl.attachments.preview.imageeditor.EditedLocalMedia
import io.element.android.features.share.api.OnSharedData
import io.element.android.features.share.api.ShareIntentData
import io.element.android.features.share.api.UriToShare
import io.element.android.libraries.androidutils.file.safeDelete
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeVideo
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderRoomFactory
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

@AssistedInject
class SharePresenter(
    @Assisted private val shareIntentData: ShareIntentData,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val matrixClient: MatrixClient,
    private val mediaSenderRoomFactory: MediaSenderRoomFactory,
    private val activeRoomsHolder: ActiveRoomsHolder,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
    private val onSharedData: OnSharedData,
    private val localMediaFactory: LocalMediaFactory,
    private val attachmentImageEditor: AttachmentImageEditor,
    private val dispatchers: CoroutineDispatchers,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val biometricAuthenticatorManager: BiometricAuthenticatorManager,
    private val chatLockUnlockTracker: ChatLockUnlockTracker,
) : Presenter<ShareState> {
    @AssistedFactory
    fun interface Factory {
        fun create(shareIntentData: ShareIntentData): SharePresenter
    }

    private data class PendingAuth(val roomIds: List<RoomId>, val attempt: Int)

    private val shareActionState: MutableState<AsyncAction<List<RoomId>>> = mutableStateOf(AsyncAction.Uninitialized)
    private val imageEditorState: MutableState<AttachmentImageEditorState?> = mutableStateOf(null)
    private val isApplyingImageEdits: MutableState<Boolean> = mutableStateOf(false)
    private val displayImageEditError: MutableState<Boolean> = mutableStateOf(false)
    private val pendingAuth: MutableState<PendingAuth?> = mutableStateOf(null)

    private var pendingRoomIds: List<RoomId>? = null
    private var pendingEditableMedia: EditedLocalMedia? = null

    fun onRoomSelected(roomIds: List<RoomId>) {
        sessionCoroutineScope.launch {
            val lockedRoomIds = sessionPreferencesStore.lockedRoomIds().first()
            if (roomIds.any { it in lockedRoomIds }) {
                // At least one destination is locked: require the device's own unlock (biometric/
                // PIN/pattern) before sending anything, rather than hiding those rooms from the
                // picker outright.
                pendingAuth.value = PendingAuth(roomIds, attempt = 0)
            } else {
                proceedWithShare(roomIds)
            }
        }
    }

    private fun proceedWithShare(roomIds: List<RoomId>) {
        val singleImageUri = (shareIntentData as? ShareIntentData.Uris)
            ?.uris
            ?.singleOrNull()
            ?.takeIf { it.mimeType.isMimeTypeImage() }
        if (singleImageUri != null) {
            sessionCoroutineScope.launch { openImageEditor(singleImageUri, roomIds) }
        } else {
            sessionCoroutineScope.share(shareIntentData, roomIds)
        }
    }

    private suspend fun openImageEditor(uriToShare: UriToShare, roomIds: List<RoomId>) {
        val localMedia = localMediaFactory.createFromUri(
            uri = uriToShare.uri,
            mimeType = uriToShare.mimeType,
            name = null,
            formattedFileSize = null,
        )
        if (!attachmentImageEditor.canEdit(localMedia)) {
            sessionCoroutineScope.share(shareIntentData, roomIds)
            return
        }
        val editableMedia = attachmentImageEditor.createEditableCopy(localMedia).getOrElse {
            Timber.w(it, "Failed to create an editable copy of the shared image, sending it as-is")
            sessionCoroutineScope.share(shareIntentData, roomIds)
            return
        }
        pendingRoomIds = roomIds
        pendingEditableMedia = editableMedia
        imageEditorState.value = AttachmentImageEditorState(
            localMedia = editableMedia.localMedia,
            edits = AttachmentImageEdits(),
            previewDebug = false,
        )
    }

    @Composable
    override fun present(): ShareState {
        val currentPendingAuth = pendingAuth.value
        val authState = rememberDeviceAuthState(biometricAuthenticatorManager, chatLockUnlockTracker, authTrigger = currentPendingAuth)

        LaunchedEffect(authState, currentPendingAuth) {
            if (currentPendingAuth != null && authState == DeviceAuthState.Unlocked) {
                pendingAuth.value = null
                proceedWithShare(currentPendingAuth.roomIds)
            }
        }

        fun handleEvent(event: ShareEvents) {
            when (event) {
                ShareEvents.ClearError -> shareActionState.value = AsyncAction.Uninitialized
                ShareEvents.RetryAuth -> pendingAuth.value = pendingAuth.value?.let { it.copy(attempt = it.attempt + 1) }
                ShareEvents.CancelAuth -> pendingAuth.value = null
                ShareEvents.CloseImageEditor -> {
                    imageEditorState.value = null
                    pendingEditableMedia?.file?.safeDelete()
                    pendingEditableMedia = null
                    pendingRoomIds = null
                }
                is ShareEvents.UpdateImageCropRect -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(edits = pendingState.edits.copy(cropRect = event.cropRect))
                }
                ShareEvents.RotateImageToTheLeft -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(edits = pendingState.edits.rotateAntiClockwise())
                }
                ShareEvents.FlipImageHorizontally -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(edits = pendingState.edits.flipHorizontally())
                }
                ShareEvents.FlipImageVertically -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(edits = pendingState.edits.flipVertically())
                }
                ShareEvents.ResetImageEdits -> {
                    imageEditorState.value = imageEditorState.value?.copy(edits = AttachmentImageEdits())
                }
                is ShareEvents.SelectImageEditorTool -> {
                    imageEditorState.value = imageEditorState.value?.copy(selectedTool = event.tool)
                }
                is ShareEvents.SelectImageAnnotationColor -> {
                    imageEditorState.value = imageEditorState.value?.copy(annotationColor = event.color)
                }
                is ShareEvents.SelectImageAnnotationStrokeWidth -> {
                    imageEditorState.value = imageEditorState.value?.copy(annotationStrokeWidth = event.strokeWidth)
                }
                is ShareEvents.AddImageAnnotation -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(
                        edits = pendingState.edits.copy(annotations = pendingState.edits.annotations + event.annotation)
                    )
                }
                ShareEvents.UndoImageAnnotation -> {
                    val pendingState = imageEditorState.value ?: return
                    imageEditorState.value = pendingState.copy(
                        edits = pendingState.edits.copy(annotations = pendingState.edits.annotations.dropLast(1))
                    )
                }
                ShareEvents.ApplyImageEdits -> applyImageEdits()
                ShareEvents.ClearImageEditError -> displayImageEditError.value = false
            }
        }

        return ShareState(
            shareAction = shareActionState.value,
            imageEditorState = imageEditorState.value,
            isApplyingImageEdits = isApplyingImageEdits.value,
            displayImageEditError = displayImageEditError.value,
            pendingAuthState = currentPendingAuth?.let { authState },
            eventSink = ::handleEvent,
        )
    }

    private fun applyImageEdits() {
        val pendingState = imageEditorState.value ?: return
        val roomIds = pendingRoomIds ?: return
        if (!pendingState.edits.hasChanges) {
            imageEditorState.value = null
            pendingEditableMedia = null
            pendingRoomIds = null
            val editedShareData = ShareIntentData.Uris(
                text = (shareIntentData as? ShareIntentData.Uris)?.text,
                uris = listOf(UriToShare(uri = pendingState.localMedia.uri, mimeType = pendingState.localMedia.info.mimeType)),
            )
            sessionCoroutineScope.share(editedShareData, roomIds, originalShareIntentData = shareIntentData)
            return
        }
        isApplyingImageEdits.value = true
        displayImageEditError.value = false
        sessionCoroutineScope.launch {
            val result = withContext(dispatchers.io) {
                attachmentImageEditor.exportEdits(localMedia = pendingState.localMedia, edits = pendingState.edits)
            }
            result.fold(
                onSuccess = { editedMedia ->
                    pendingEditableMedia?.file?.safeDelete()
                    imageEditorState.value = null
                    pendingEditableMedia = null
                    pendingRoomIds = null
                    val editedShareData = ShareIntentData.Uris(
                        text = (shareIntentData as? ShareIntentData.Uris)?.text,
                        uris = listOf(UriToShare(uri = editedMedia.localMedia.uri, mimeType = editedMedia.localMedia.info.mimeType)),
                    )
                    sessionCoroutineScope.share(editedShareData, roomIds, originalShareIntentData = shareIntentData)
                },
                onFailure = {
                    Timber.e(it, "Failed to apply image edits before sharing")
                    displayImageEditError.value = true
                }
            )
            isApplyingImageEdits.value = false
        }
    }

    private suspend fun getJoinedRoom(roomId: RoomId): JoinedRoom? {
        return activeRoomsHolder.getActiveRoom(matrixClient.sessionId)
            ?.takeIf { it.roomId == roomId }
            ?: matrixClient.getJoinedRoom(roomId)
    }

    private fun CoroutineScope.share(
        shareIntentData: ShareIntentData,
        roomIds: List<RoomId>,
        originalShareIntentData: ShareIntentData = shareIntentData,
    ) = launch {
        suspend {
            val result = when (shareIntentData) {
                is ShareIntentData.PlainText -> {
                    roomIds
                        .map { roomId ->
                            getJoinedRoom(roomId)?.liveTimeline?.sendMessage(
                                body = shareIntentData.content,
                                htmlBody = null,
                                intentionalMentions = emptyList(),
                            )?.isSuccess.orFalse()
                        }
                        .all { it }
                }
                is ShareIntentData.Uris -> {
                    val filesToShare = shareIntentData.uris
                    if (filesToShare.isEmpty()) {
                        false
                    } else {
                        roomIds
                            .map { roomId ->
                                val room = getJoinedRoom(roomId) ?: return@map false
                                val mediaSender = mediaSenderRoomFactory.create(room = room)
                                filesToShare
                                    .map { fileToShare ->
                                        // The share sheet has no attachment preview/HD picker, unlike the in-app
                                        // attach flow, so there's no way for the user to opt into original quality.
                                        // Treat "share this exact file" as already meaning "send it as-is": skip
                                        // video recompression here rather than silently applying the default preset.
                                        val mediaOptimizationConfig = mediaOptimizationConfigProvider.get().let { config ->
                                            if (fileToShare.mimeType.isMimeTypeVideo()) {
                                                config.copy(videoCompressionPreset = null)
                                            } else {
                                                config
                                            }
                                        }
                                        val result = mediaSender.sendMedia(
                                            caption = shareIntentData.text,
                                            uri = fileToShare.uri,
                                            mimeType = fileToShare.mimeType,
                                            mediaOptimizationConfig = mediaOptimizationConfig,
                                        )
                                        // If the coroutine was cancelled, destroy the room and rethrow the exception
                                        val cancellationException = result.exceptionOrNull() as? CancellationException
                                        if (cancellationException != null) {
                                            if (activeRoomsHolder.getActiveRoomMatching(matrixClient.sessionId, roomId) == null) {
                                                room.destroy()
                                            }
                                            throw cancellationException
                                        }
                                        result.isSuccess
                                    }
                                    .all { isSuccess -> isSuccess }
                                    .also {
                                        if (activeRoomsHolder.getActiveRoomMatching(matrixClient.sessionId, roomId) == null) {
                                            room.destroy()
                                        }
                                    }
                            }
                            .all { it }
                    }
                }
            }

            // Handle post-processing of the originally shared data (revokes the incoming Uri grants,
            // deletes the file-provider copy) even when an edited copy was actually sent.
            onSharedData(originalShareIntentData)

            if (!result) {
                error("Failed to handle incoming share intent")
            }
            roomIds
        }.runCatchingUpdatingState(shareActionState)
    }
}

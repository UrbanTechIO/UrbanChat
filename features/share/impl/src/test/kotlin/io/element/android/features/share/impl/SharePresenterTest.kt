/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import android.net.Uri
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.features.lockscreen.impl.biometric.ChatLockUnlockTracker
import io.element.android.features.lockscreen.test.biometric.FakeBiometricAuthenticatorManager
import io.element.android.features.lockscreen.test.biometric.FakeChatLockUnlockTracker
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditor
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEdits
import io.element.android.features.messages.impl.attachments.preview.imageeditor.EditedLocalMedia
import io.element.android.features.share.api.OnSharedData
import io.element.android.features.share.api.ShareIntentData
import io.element.android.features.share.api.UriToShare
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaSenderRoomFactory
import io.element.android.libraries.mediaupload.test.FakeMediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.test.FakeMediaSender
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.mediaviewer.api.MediaInfo
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import io.element.android.services.appnavstate.impl.DefaultActiveRoomsHolder
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SharePresenterTest : RobolectricTest() {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createSharePresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - on room selected error then clear error`() = runTest {
        val presenter = createSharePresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val failure = awaitItem()
            assertThat(failure.shareAction.isFailure()).isTrue()
            failure.eventSink.invoke(ShareEvents.ClearError)
            assertThat(awaitItem().shareAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - on room selected ok`() = runTest {
        val joinedRoom = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendMessageLambda = { _, _, _, _, _ -> Result.success(Unit) }
            },
        )
        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, joinedRoom)
        }
        val presenter = createSharePresenter(
            matrixClient = matrixClient,
            shareIntentData = ShareIntentData.PlainText(A_MESSAGE),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val success = awaitItem()
            assertThat(success.shareAction.isSuccess()).isTrue()
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - send text ok`() = runTest {
        val joinedRoom = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendMessageLambda = { _, _, _, _, _ -> Result.success(Unit) }
            },
        )
        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, joinedRoom)
        }
        val presenter = createSharePresenter(
            matrixClient = matrixClient,
            shareIntentData = ShareIntentData.PlainText(A_MESSAGE),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val success = awaitItem()
            assertThat(success.shareAction.isSuccess()).isTrue()
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
        }
    }

    @Test
    fun `present - send media ok`() = runTest {
        val sendMediaResult = lambdaRecorder<Result<Unit>> { Result.success(Unit) }
        val joinedRoom = FakeJoinedRoom(
            liveTimeline = FakeTimeline(),
        )
        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, joinedRoom)
        }
        val mediaSender = FakeMediaSender(
            sendMediaResult = sendMediaResult,
        )
        val presenter = createSharePresenter(
            matrixClient = matrixClient,
            shareIntentData = ShareIntentData.Uris(
                text = A_MESSAGE,
                listOf(
                    UriToShare(
                        uri = Uri.parse("content://image.jpg"),
                        mimeType = MimeTypes.Jpeg,
                    )
                )
            ),
            mediaSenderRoomFactory = MediaSenderRoomFactory { mediaSender },
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.shareAction.isUninitialized()).isTrue()
            presenter.onRoomSelected(listOf(A_ROOM_ID))
            assertThat(awaitItem().shareAction.isLoading()).isTrue()
            val success = awaitItem()
            assertThat(success.shareAction.isSuccess()).isTrue()
            assertThat(success.shareAction).isEqualTo(AsyncAction.Success(listOf(A_ROOM_ID)))
            sendMediaResult.assertions().isCalledOnce()
        }
    }
}

internal fun TestScope.createSharePresenter(
    shareIntentData: ShareIntentData = ShareIntentData.PlainText(A_MESSAGE),
    matrixClient: MatrixClient = FakeMatrixClient(),
    activeRoomsHolder: ActiveRoomsHolder = DefaultActiveRoomsHolder(),
    mediaSenderRoomFactory: MediaSenderRoomFactory = MediaSenderRoomFactory { FakeMediaSender() },
    mediaOptimizationConfigProvider: MediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(),
    onSharedData: OnSharedData = OnSharedData {},
    localMediaFactory: LocalMediaFactory = NoOpLocalMediaFactory,
    attachmentImageEditor: AttachmentImageEditor = FakeAttachmentImageEditor(canEditResult = false),
    sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(),
    biometricAuthenticatorManager: BiometricAuthenticatorManager = FakeBiometricAuthenticatorManager(),
    chatLockUnlockTracker: ChatLockUnlockTracker = FakeChatLockUnlockTracker(),
): SharePresenter {
    return SharePresenter(
        shareIntentData = shareIntentData,
        sessionCoroutineScope = this,
        matrixClient = matrixClient,
        activeRoomsHolder = activeRoomsHolder,
        mediaSenderRoomFactory = mediaSenderRoomFactory,
        mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
        onSharedData = onSharedData,
        localMediaFactory = localMediaFactory,
        attachmentImageEditor = attachmentImageEditor,
        dispatchers = testCoroutineDispatchers(),
        sessionPreferencesStore = sessionPreferencesStore,
        biometricAuthenticatorManager = biometricAuthenticatorManager,
        chatLockUnlockTracker = chatLockUnlockTracker,
    )
}

private object NoOpLocalMediaFactory : LocalMediaFactory {
    override fun createFromMediaFile(mediaFile: MediaFile, mediaInfo: MediaInfo): LocalMedia = error("not used")

    // Wraps the already-constructed Uri passed in rather than parsing a new one, so this stays safe
    // to use as a default arg even outside Robolectric (android.net.Uri.parse isn't mocked there).
    override fun createFromUri(uri: Uri, mimeType: String?, name: String?, formattedFileSize: String?): LocalMedia {
        return LocalMedia(
            uri = uri,
            info = MediaInfo(
                filename = name ?: "file",
                fileSize = null,
                caption = null,
                formattedCaption = null,
                mimeType = mimeType ?: "application/octet-stream",
                formattedFileSize = formattedFileSize ?: "0B",
                fileExtension = "",
                senderId = null,
                senderName = null,
                senderAvatar = null,
                dateSent = null,
                dateSentFull = null,
                waveform = null,
                duration = null,
            ),
        )
    }
}

private class FakeAttachmentImageEditor(
    private val canEditResult: Boolean = true,
    private val result: () -> Result<EditedLocalMedia> = { error("not used") },
) : AttachmentImageEditor {
    override suspend fun canEdit(localMedia: LocalMedia): Boolean = canEditResult
    override suspend fun exportEdits(localMedia: LocalMedia, edits: AttachmentImageEdits): Result<EditedLocalMedia> = result()
    override suspend fun createEditableCopy(localMedia: LocalMedia): Result<EditedLocalMedia> = result()
}

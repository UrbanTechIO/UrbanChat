/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.viewonce

import kotlinx.coroutines.flow.MutableStateFlow

/** Whether the photo or video that is being prepared for sending should be sent as "view once". Reset after every send. */
object ViewOnceDraft {
    val enabled = MutableStateFlow(false)
}

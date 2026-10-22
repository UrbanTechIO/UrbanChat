/*
 * UrbanChat: lets a settings screen ask the app to check for a new release without depending on the app module.
 */

package io.element.android.libraries.core.update

object AppUpdateTrigger {
    @Volatile
    var listener: (() -> Unit)? = null

    fun requestCheck() {
        listener?.invoke()
    }
}

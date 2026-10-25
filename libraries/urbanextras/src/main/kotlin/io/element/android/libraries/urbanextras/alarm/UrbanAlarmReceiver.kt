/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.bindings

/** Woken by [UrbanAlarms] (and by reboots or clock changes): hands over to [UrbanAlarmHandler] while the app is allowed to run. */
class UrbanAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: UrbanAlarmHandler

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        context.bindings<UrbanAlarmReceiverBindings>().inject(this)
        val pending = goAsync()
        handler.handle(context.applicationContext, intent) { pending.finish() }
    }
}

@ContributesTo(AppScope::class)
interface UrbanAlarmReceiverBindings {
    fun inject(receiver: UrbanAlarmReceiver)
}

package com.guard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class GuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val api = GuardApi(context)
        if (api.deviceToken != null) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DeviceService::class.java))
            } catch (_: Exception) {
                // Android may restrict background foreground-service starts on some OS/device builds.
            }
        }
    }
}

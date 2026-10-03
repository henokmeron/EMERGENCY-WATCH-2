package com.example.emergencywatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emergencywatch.data.security.SecureAuthManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val authManager = SecureAuthManager(context)
            if (authManager.isPaired()) {
                SensorForegroundService.startService(context)
                HeartbeatWorker.schedule(context)
            }
        }
    }
}

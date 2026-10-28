package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.data.EncryptedPreferencesManager
import com.example.data.MairaLogger
import com.example.service.MairaOverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MairaLogger.log(MairaLogger.Category.TRIGGER, MairaLogger.Level.INFO, "ডিভাইস রিবুট হয়েছে")
            val prefsManager = EncryptedPreferencesManager(context)
            if (prefsManager.isOverlayEnabled() && Settings.canDrawOverlays(context)) {
                MairaOverlayService.startService(context)
            }
        }
    }
}

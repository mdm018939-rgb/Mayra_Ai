package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.MairaLogger

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderText = intent.getStringExtra("REMINDER_TEXT") ?: "আপনার একটি রিমাইন্ডার অ্যালার্ম রয়েছে"
        MairaLogger.log(MairaLogger.Category.TRIGGER, MairaLogger.Level.INFO, "রিমাইন্ডার ট্রিগার ফায়ার করেছে: $reminderText")

        showNotification(context, reminderText)
    }

    private fun showNotification(context: Context, text: String) {
        val channelId = "maira_reminder_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Maira Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("মাইরা রিমাইন্ডার")
            .setContentText(text)
            .setSmallIcon(R.drawable.maira_app_icon_1793185004076)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}

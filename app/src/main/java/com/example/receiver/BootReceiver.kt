package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.MairaLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MairaLogger.log(MairaLogger.Category.TRIGGER, MairaLogger.Level.INFO, "ডিভাইস রিবুট হয়েছে: রিমাইন্ডারসমূহ পুনঃনির্ধারণ করা প্রয়োজন")
            // Exact alarm rescheduling can be called from database here
        }
    }
}

package com.example

import android.app.Application
import com.example.data.MairaCrashHandler
import com.example.data.MairaLogger

class MairaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MairaLogger.init(this)
        MairaCrashHandler.init(this)
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "মাইরা অ্যাপ্লিকেশন শুরু হয়েছে")
    }
}

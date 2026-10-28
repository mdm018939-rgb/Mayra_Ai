package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.EncryptedPreferencesManager
import com.example.data.MairaLogger

class MairaOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "ACTION_HIDE_OVERLAY"
        const val CHANNEL_ID = "maira_overlay_channel"
        const val NOTIFICATION_ID = 2002

        @Volatile
        var isOverlayShowing = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, MairaOverlayService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MairaOverlayService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var prefsManager: EncryptedPreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = EncryptedPreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                startForegroundNotification()
                showOverlayWindow()
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlayWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "ওভারলে পারমিশন নাই, ভাসমান মেয়ে দেখানো সম্ভব হয়নি")
            return
        }

        if (isOverlayShowing && overlayView != null) {
            // Already showing, avoid duplicating
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            removeOverlayInternal() // Remove any existing view safety check

            val displayMetrics = resources.displayMetrics
            val screenHeightPx = displayMetrics.heightPixels
            val heightPx = (screenHeightPx * 0.30f).toInt().coerceAtLeast(240)
            val widthPx = (heightPx * 0.65f).toInt()

            val params = WindowManager.LayoutParams(
                widthPx, heightPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            val img = ImageView(this).apply {
                setImageResource(R.drawable.maira_body)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            img.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            try {
                                windowManager?.updateViewLayout(img, params)
                            } catch (e: Exception) {}
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = Math.abs(event.rawX - initialTouchX)
                            val diffY = Math.abs(event.rawY - initialTouchY)
                            if (diffX < 12 && diffY < 12) {
                                // Single tap -> Open main app
                                val appIntent = Intent(this@MairaOverlayService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(appIntent)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            overlayView = img
            windowManager?.addView(overlayView, params)
            isOverlayShowing = true
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান অ্যাভাটার ওভারলে উইন্ডো চালু হয়েছে (৩০% উচ্চতা)")
        } catch (e: Exception) {
            isOverlayShowing = false
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.ERROR, "ভাসমান অ্যাভাটার যোগ করতে সমস্যা: ${e.message}")
        }
    }

    private fun removeOverlayInternal() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        overlayView = null
        isOverlayShowing = false
    }

    private fun hideOverlayWindow() {
        removeOverlayInternal()
        MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান অ্যাভাটার ওভারলে বন্ধ করা হয়েছে")
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("মাইরা ভাসমান অ্যাভাটার")
            .setContentText("অন্যান্য অ্যাপের উপরে ভাসমান অ্যাভাটার সক্রিয় রয়েছে")
            .setSmallIcon(R.drawable.maira_app_icon_1793185004076)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maira Floating Avatar",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        removeOverlayInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

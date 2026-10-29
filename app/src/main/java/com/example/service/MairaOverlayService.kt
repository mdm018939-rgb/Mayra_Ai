package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.EncryptedPreferencesManager
import com.example.data.MairaLogger
import com.example.ui.views.MairaAvatarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * MairaOverlayService: Displays a floating animated AI avatar above other apps
 * with real-time lip-sync, blinking eyes, state auras, drag & magnetic edge-snap,
 * and an accessible close button.
 */
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
            val prefs = EncryptedPreferencesManager(context)
            if (!prefs.isOverlayEnabled()) {
                MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান মেয়ে সেটিংসে বন্ধ রয়েছে, ওভারলে সার্ভিস চালু করা হবে না")
                return
            }
            if (!Settings.canDrawOverlays(context)) {
                MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "ওভারলে পারমিশন নেই, সার্ভিস শুরু করা যাবে না")
                return
            }
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
    private var overlayContainer: FrameLayout? = null
    private var avatarView: MairaAvatarView? = null
    private var snapAnimator: ValueAnimator? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefsManager: EncryptedPreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = EncryptedPreferencesManager(this)
        createNotificationChannel()
        try {
            startForegroundNotification()
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.ERROR, "startForegroundNotification ব্যর্থ: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefsManager.isOverlayEnabled() || !Settings.canDrawOverlays(this)) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ওভারলে সেটিংসে নিষ্ক্রিয় বা পারমিশন নেই: সার্ভিস ও উইন্ডো বন্ধ করা হচ্ছে")
            hideOverlayWindow()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundNotification()
        } catch (e: Exception) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.ERROR, "onStartCommand startForeground ত্রুটি: ${e.message}")
        }
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                showOverlayWindow()
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlayWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @Synchronized
    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlayWindow() {
        if (!prefsManager.isOverlayEnabled()) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান অ্যাভাটার সেটিংসে বন্ধ রয়েছে, উইন্ডো দেখানো হবে না")
            hideOverlayWindow()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.WARN, "ওভারলে পারমিশন নেই, ভাসমান অ্যাভাটার দেখানো সম্ভব হয়নি")
            hideOverlayWindow()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        if (isOverlayShowing && overlayContainer != null) {
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            removeOverlayInternal()

            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            val screenHeight = displayMetrics.heightPixels

            // Avatar container sized ~30% of screen height
            val containerHeight = (screenHeight * 0.30f).toInt().coerceIn((240 * density).toInt(), (420 * density).toInt())
            val containerWidth = (containerHeight * 0.78f).toInt()

            val params = WindowManager.LayoutParams(
                containerWidth,
                containerHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16 * density).toInt()
                y = (screenHeight * 0.25f).toInt()
            }

            // 1. Root Container
            val root = FrameLayout(this).apply {
                clipChildren = false
                clipToPadding = false
            }

            // 2. Animated Custom Avatar View with Lip-sync
            val avatar = MairaAvatarView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }
            avatarView = avatar
            root.addView(avatar)

            // 3. Drag & Double-Tap Handling with Magnetic Edge Snapping
            // - Dragging moves the avatar overlay smoothly and snaps to nearest edge on release.
            // - Single tap does NOT open MainActivity.
            // - Double tap within doubleTapTimeout opens or brings MainActivity to foreground.
            // - Dragging or gesture cancellation resets double-tap tracking.
            val viewConfig = ViewConfiguration.get(this)
            val touchSlop = viewConfig.scaledTouchSlop
            val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
            val doubleTapSlop = viewConfig.scaledDoubleTapSlop
            val doubleTapSlopSq = doubleTapSlop * doubleTapSlop

            root.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false
                private var lastTapUpTime = 0L
                private var lastTapUpX = 0f
                private var lastTapUpY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            snapAnimator?.cancel()
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                                isDragging = true
                                lastTapUpTime = 0L
                            }
                            if (isDragging) {
                                params.x = initialX + dx.toInt()
                                params.y = initialY + dy.toInt()
                                try {
                                    windowManager?.updateViewLayout(root, params)
                                } catch (_: Exception) {}
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isDragging) {
                                lastTapUpTime = 0L
                                snapToNearestEdge(params, root)
                            } else {
                                val upTime = SystemClock.uptimeMillis()
                                val upX = event.rawX
                                val upY = event.rawY
                                val timeDiff = upTime - lastTapUpTime
                                val distX = upX - lastTapUpX
                                val distY = upY - lastTapUpY
                                val distSq = distX * distX + distY * distY

                                if (lastTapUpTime > 0L && timeDiff in 40..doubleTapTimeout && distSq <= doubleTapSlopSq) {
                                    // Valid double tap -> Open / bring MainActivity to foreground
                                    lastTapUpTime = 0L
                                    val appIntent = Intent(this@MairaOverlayService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    }
                                    startActivity(appIntent)
                                } else {
                                    // First/single tap: do NOT open MainActivity. Track for potential double tap.
                                    lastTapUpTime = upTime
                                    lastTapUpX = upX
                                    lastTapUpY = upY
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            lastTapUpTime = 0L
                            return true
                        }
                    }
                    return false
                }
            })

            overlayContainer = root
            windowManager?.addView(overlayContainer, params)
            isOverlayShowing = true

            // 6. Connect StateFlows from MairaVoiceService
            observeVoiceServiceStates()

            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.INFO, "ভাসমান অ্যানিমেটেড AI অ্যাভাটার সক্রিয় হয়েছে (লিপ-সিঙ্ক ও ফেসিয়াল এক্সপ্রেশনসহ)")
        } catch (e: Exception) {
            isOverlayShowing = false
            try {
                prefsManager.setOverlayEnabled(false)
            } catch (_: Exception) {}
            MairaLogger.log(MairaLogger.Category.UI, MairaLogger.Level.ERROR, "ভাসমান অ্যাভাটার উইন্ডো যোগ করতে সমস্যা: ${e.message}")
            stopSelf()
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams, rootView: View) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = rootView.width.coerceAtLeast(1)
        val density = displayMetrics.density

        val margin = (12 * density).toInt()
        val targetX = if (params.x + viewWidth / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - viewWidth - margin
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(rootView, params)
                } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun observeVoiceServiceStates() {
        serviceScope.launch {
            MairaVoiceService.micRmsFlow.collectLatest { rms ->
                avatarView?.setMicRms(rms)
            }
        }

        serviceScope.launch {
            MairaVoiceService.speakerRmsFlow.collectLatest { rms ->
                avatarView?.setSpeakerRms(rms)
            }
        }

        serviceScope.launch {
            MairaVoiceService.sessionState.collectLatest { state ->
                avatarView?.setSessionState(state)
            }
        }

        serviceScope.launch {
            MairaVoiceService.statusMessage.collectLatest { status ->
                avatarView?.setStatusMessage(status)
            }
        }
    }

    private fun removeOverlayInternal() {
        snapAnimator?.cancel()
        snapAnimator = null
        overlayContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayContainer = null
        avatarView = null
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
            .setContentText("অন্যান্য অ্যাপের উপরে লাইভ লিপ-সিঙ্ক অ্যাভাটার সক্রিয় রয়েছে")
            .setSmallIcon(R.drawable.maira_app_icon_1793185004076)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        serviceScope.cancel()
        removeOverlayInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

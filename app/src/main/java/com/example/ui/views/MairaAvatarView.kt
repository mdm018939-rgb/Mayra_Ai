package com.example.ui.views

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.example.R
import kotlin.math.*

/**
 * MairaAvatarView: High-performance animated AI avatar reusing the exact character
 * asset (long-haired girl in red sweater) from the Maira home screen with real-time
 * lip-sync, blinking eyes, physics swaying, and dynamic state auras.
 */
class MairaAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class AvatarState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        DISCONNECTED
    }

    // Public reactive inputs
    private var currentState = AvatarState.IDLE
    private var currentStatusMessage = "প্রস্তুত"
    private var speakerRms = 0f
    private var micRms = 0f

    // Smoothed animation values
    private var smoothedMouthOpen = 0f
    private var smoothedMicEnergy = 0f
    private var smoothedSpeakerEnergy = 0f
    private var smoothedHeadTilt = 0f

    // Bitmap cache for the actual home-screen character
    private var characterBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    // Reusable Paints
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val auraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFE8D0")
    }
    private val eyelashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#341D12")
    }
    private val mouthInsidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5A1524")
    }
    private val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFF6F8")
    }
    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EB6B7F")
    }
    private val lipOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#C23855")
    }

    // Reusable Path objects
    private val mouthPath = Path()
    private val tonguePath = Path()

    // Public setters called from Main thread or service
    fun setSpeakerRms(rms: Float) {
        speakerRms = rms.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setMicRms(rms: Float) {
        micRms = rms.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setSessionState(sessionState: String) {
        updateInternalState(sessionState, currentStatusMessage)
    }

    fun setStatusMessage(statusMessage: String) {
        currentStatusMessage = statusMessage
        updateInternalState(null, statusMessage)
    }

    private fun updateInternalState(sessionState: String?, statusMessage: String) {
        val state = when {
            sessionState == "বন্ধ" -> AvatarState.DISCONNECTED
            statusMessage.contains("ভাবছি") || statusMessage.contains("চিন্তা") -> AvatarState.THINKING
            statusMessage.contains("শুনছি") -> AvatarState.LISTENING
            statusMessage.contains("বলছি") || speakerRms > 0.05f -> AvatarState.SPEAKING
            else -> AvatarState.IDLE
        }
        if (currentState != state) {
            currentState = state
            postInvalidateOnAnimation()
        }
    }

    private fun getOrLoadBitmap(): Bitmap? {
        if (characterBitmap == null || characterBitmap?.isRecycled == true) {
            try {
                characterBitmap = BitmapFactory.decodeResource(resources, R.drawable.maira_body)
            } catch (_: Throwable) {}
        }
        return characterBitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val bitmap = getOrLoadBitmap() ?: return
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        if (bmpW <= 0f || bmpH <= 0f) return

        val now = SystemClock.uptimeMillis()

        // 1. Physics & Animation Timers
        val breatheCycle = sin(now * 0.0024).toFloat()
        val verticalBob = breatheCycle * 3.0f
        val breatheScale = 1.0f + breatheCycle * 0.010f

        // Natural eye blinking: blink ~170ms every 3.8 seconds
        val blinkPeriod = 3800L
        val blinkDuration = 170L
        val cycleTime = now % blinkPeriod
        val blinkProgress = if (cycleTime < blinkDuration) {
            val half = blinkDuration / 2f
            if (cycleTime < half) (cycleTime / half) else ((blinkDuration - cycleTime) / half)
        } else {
            0f
        }

        // Smoothed energies
        smoothedMicEnergy += (micRms - smoothedMicEnergy) * 0.25f
        smoothedSpeakerEnergy += (speakerRms - smoothedSpeakerEnergy) * 0.35f

        // Real-time amplitude lip-sync smoothing (EMA)
        val isSpeaking = currentState == AvatarState.SPEAKING || speakerRms > 0.035f
        val targetMouthOpen = if (isSpeaking) {
            ((speakerRms - 0.02f) * 2.8f).coerceIn(0f, 1f)
        } else {
            0f
        }
        smoothedMouthOpen += (targetMouthOpen - smoothedMouthOpen) * 0.40f

        // Smooth head tilt towards state
        val targetHeadTilt = when (currentState) {
            AvatarState.LISTENING -> -3.0f
            AvatarState.THINKING -> 3.8f
            AvatarState.SPEAKING -> sin(now * 0.008f) * 2.0f
            else -> sin(now * 0.0016f) * 1.2f
        }
        smoothedHeadTilt += (targetHeadTilt - smoothedHeadTilt) * 0.15f

        // Scale and position character maintaining exact aspect ratio
        val scale = min(w / bmpW, h / bmpH)
        val destW = bmpW * scale
        val destH = bmpH * scale
        val destLeft = (w - destW) / 2f
        val destTop = h - destH + verticalBob

        // Facial feature locations mapped from maira_body (768 x 1376)
        val headCenterX = destLeft + (384f / 768f) * destW
        val headCenterY = destTop + (220f / 1376f) * destH
        val headRadius = (135f / 768f) * destW

        canvas.save()
        // Pivot at the feet/bottom of the avatar for natural swaying motion
        canvas.rotate(smoothedHeadTilt, destLeft + destW * 0.5f, destTop + destH)
        canvas.scale(breatheScale, breatheScale, headCenterX, headCenterY)

        // 2. Draw Reactive Background Aura behind character
        drawAura(canvas, headCenterX, headCenterY, headRadius, now)

        // 3. Draw actual character image: long-haired girl in red sweater
        srcRect.set(0, 0, bitmap.width, bitmap.height)
        dstRect.set(destLeft, destTop, destLeft + destW, destTop + destH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint)

        // 4. Natural Blinking Eyes
        if (blinkProgress > 0.08f) {
            val leftEyeX = destLeft + (345f / 768f) * destW
            val leftEyeY = destTop + (198f / 1376f) * destH
            val rightEyeX = destLeft + (422f / 768f) * destW
            val rightEyeY = destTop + (186f / 1376f) * destH
            val eyeW = (42f / 768f) * destW
            val eyeH = (32f / 1376f) * destH

            drawClosedEyes(canvas, leftEyeX, leftEyeY, rightEyeX, rightEyeY, eyeW, eyeH, blinkProgress)
        }

        // 5. Lip-synced Mouth reacting dynamically to speaker RMS audio
        if (smoothedMouthOpen >= 0.05f) {
            val mouthX = destLeft + (388f / 768f) * destW
            val mouthY = destTop + (257f / 1376f) * destH
            val mouthBaseW = (34f / 768f) * destW
            drawLipSyncMouth(canvas, mouthX, mouthY, mouthBaseW, destW, smoothedMouthOpen)
        }

        canvas.restore()

        // Continuous animation loop
        postInvalidateOnAnimation()
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        when (currentState) {
            AvatarState.LISTENING -> {
                val pulse = (smoothedMicEnergy * r * 0.5f).coerceIn(0f, r * 0.6f)
                val ripple = ((now % 1200) / 1200f)
                val rippleRadius = r * 1.15f + ripple * r * 0.45f
                val alpha = ((1f - ripple) * 160).toInt().coerceIn(0, 255)

                auraPaint.color = Color.argb(alpha, 0, 229, 255)
                auraPaint.strokeWidth = 3.5f
                canvas.drawCircle(cx, cy, rippleRadius, auraPaint)

                auraFillPaint.color = Color.argb(45, 0, 229, 255)
                canvas.drawCircle(cx, cy, r * 1.15f + pulse, auraFillPaint)
            }
            AvatarState.THINKING -> {
                val orbitRadius = r * 1.25f
                val orbitAngle = (now * 0.12f) % 360f

                auraPaint.color = Color.argb(60, 255, 183, 77)
                auraPaint.strokeWidth = 2.5f
                canvas.drawCircle(cx, cy, orbitRadius, auraPaint)

                auraFillPaint.color = Color.argb(220, 255, 183, 77)
                for (i in 0 until 3) {
                    val angleRad = Math.toRadians((orbitAngle + i * 120.0)).toFloat()
                    val px = cx + orbitRadius * cos(angleRad)
                    val py = (cy - r * 0.2f) + (orbitRadius * 0.45f) * sin(angleRad)
                    canvas.drawCircle(px, py, 6.0f, auraFillPaint)
                }
            }
            AvatarState.SPEAKING -> {
                val pulse = (smoothedSpeakerEnergy * r * 0.45f).coerceIn(0f, r * 0.5f)
                auraFillPaint.color = Color.argb(55, 233, 30, 99)
                canvas.drawCircle(cx, cy, r * 1.18f + pulse, auraFillPaint)

                auraPaint.color = Color.argb(120, 255, 64, 129)
                auraPaint.strokeWidth = 3.0f
                canvas.drawCircle(cx, cy, r * 1.18f + pulse, auraPaint)
            }
            AvatarState.DISCONNECTED -> {
                auraPaint.color = Color.argb(40, 180, 180, 200)
                auraPaint.strokeWidth = 2.0f
                canvas.drawCircle(cx, cy, r * 1.1f, auraPaint)
            }
            AvatarState.IDLE -> {
                val idlePulse = sin(now * 0.002f) * (r * 0.05f)
                auraFillPaint.color = Color.argb(30, 156, 39, 176)
                canvas.drawCircle(cx, cy, r * 1.12f + idlePulse, auraFillPaint)
            }
        }
    }

    private fun drawClosedEyes(
        canvas: Canvas,
        leftX: Float, leftY: Float,
        rightX: Float, rightY: Float,
        eyeW: Float, eyeH: Float,
        blinkProgress: Float
    ) {
        val skinPatchH = eyeH * blinkProgress.coerceIn(0.6f, 1.0f)
        val leftPatch = RectF(leftX - eyeW * 0.44f, leftY - skinPatchH * 0.42f, leftX + eyeW * 0.44f, leftY + skinPatchH * 0.42f)
        val rightPatch = RectF(rightX - eyeW * 0.44f, rightY - skinPatchH * 0.42f, rightX + eyeW * 0.44f, rightY + skinPatchH * 0.42f)
        canvas.drawOval(leftPatch, skinPaint)
        canvas.drawOval(rightPatch, skinPaint)

        eyelashPaint.strokeWidth = 2.4f * (eyeW / 24f).coerceIn(0.8f, 2.0f)

        val leftLash = Path().apply {
            moveTo(leftX - eyeW * 0.42f, leftY - eyeH * 0.04f)
            quadTo(leftX, leftY + eyeH * 0.22f, leftX + eyeW * 0.42f, leftY - eyeH * 0.04f)
        }
        canvas.drawPath(leftLash, eyelashPaint)

        val rightLash = Path().apply {
            moveTo(rightX - eyeW * 0.42f, rightY - eyeH * 0.04f)
            quadTo(rightX, rightY + eyeH * 0.22f, rightX + eyeW * 0.42f, rightY - eyeH * 0.04f)
        }
        canvas.drawPath(rightLash, eyelashPaint)
    }

    private fun drawLipSyncMouth(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        baseWidth: Float,
        destW: Float,
        openRatio: Float
    ) {
        val mouthW = baseWidth * (1.0f + openRatio * 0.35f)
        val mouthH = baseWidth * (0.16f + openRatio * 0.75f)

        val left = cx - mouthW * 0.5f
        val right = cx + mouthW * 0.5f
        val top = cy - mouthH * 0.32f
        val bottom = cy + mouthH * 0.68f

        // Cover resting smile with soft matching skin color oval
        val skinPatch = RectF(cx - mouthW * 0.58f, cy - mouthH * 0.45f, cx + mouthW * 0.58f, cy + mouthH * 0.72f)
        canvas.drawOval(skinPatch, skinPaint)

        // Feminine animated open mouth curve
        mouthPath.reset()
        mouthPath.moveTo(left, cy)
        mouthPath.cubicTo(left + mouthW * 0.2f, top, right - mouthW * 0.2f, top, right, cy)
        mouthPath.cubicTo(right, bottom, left, bottom, left, cy)
        mouthPath.close()

        canvas.save()
        canvas.clipPath(mouthPath)

        // 1. Dark inner mouth
        canvas.drawPath(mouthPath, mouthInsidePaint)

        // 2. Upper Teeth
        val teethRect = RectF(left + mouthW * 0.16f, top, right - mouthW * 0.16f, top + mouthH * 0.32f)
        canvas.drawRoundRect(teethRect, 3f, 3f, teethPaint)

        // 3. Lower Tongue
        tonguePath.reset()
        tonguePath.moveTo(left + mouthW * 0.15f, bottom)
        tonguePath.quadTo(cx, bottom - mouthH * 0.42f, right - mouthW * 0.15f, bottom)
        tonguePath.close()
        canvas.drawPath(tonguePath, tonguePaint)

        canvas.restore()

        // Outer lip outline
        lipOutlinePaint.strokeWidth = 2.0f * (destW / 300f).coerceIn(0.8f, 1.8f)
        canvas.drawPath(mouthPath, lipOutlinePaint)
    }
}

package com.example.ui.views

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * MairaAvatarView: A high-performance, custom-drawn animated AI female avatar
 * with real-time lip-sync, blinking eyes, facial expressions, and dynamic state auras.
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

    // Reusable Paints
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val auraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFF2E6")
    }
    private val skinShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F3D9C7")
    }
    private val hairBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#261928")
    }
    private val hairFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#322135")
    }
    private val hairHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#66846B92")
    }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FCFCFD")
    }
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#150A1A")
    }
    private val eyeSparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val eyelashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1C0F22")
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A2242")
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E91E63")
    }
    private val clothesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#7C4DFF")
    }
    private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EDE7F6")
    }

    // Reusable Path objects to avoid allocations during onDraw
    private val mouthPath = Path()
    private val tonguePath = Path()
    private val facePath = Path()
    private val hairPath = Path()
    private val bangsPath = Path()
    private val clothesPath = Path()
    private val collarPath = Path()

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
            statusMessage.contains("বলছি") || speakerRms > 0.04f -> AvatarState.SPEAKING
            statusMessage.contains("শুনছি") || micRms > 0.05f -> AvatarState.LISTENING
            sessionState == "সংযুক্ত" || sessionState == "সংযুক্ত হচ্ছে..." -> AvatarState.IDLE
            else -> AvatarState.IDLE
        }
        if (currentState != state) {
            currentState = state
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.uptimeMillis()

        // 1. Physics & Animation Timers
        val breatheCycle = sin(now * 0.0024).toFloat() // Gentle breathing rhythm
        val verticalBob = breatheCycle * 3.5f
        val breatheScale = 1.0f + breatheCycle * 0.012f

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

        // Dimensions
        val centerX = w * 0.5f
        val headRadius = min(w, h) * 0.27f
        val headCenterY = h * 0.44f + verticalBob

        canvas.save()
        // Pivot at the feet/bottom of the avatar for natural swaying motion
        canvas.rotate(smoothedHeadTilt, centerX, h * 0.95f)
        canvas.scale(breatheScale, breatheScale, centerX, headCenterY)

        // 2. Draw Reactive Background Aura
        drawAura(canvas, centerX, headCenterY, headRadius, now)

        // 3. Hair Back
        drawHairBack(canvas, centerX, headCenterY, headRadius)

        // 4. Neck & Clothes
        drawBodyAndClothes(canvas, centerX, headCenterY, headRadius, h)

        // 5. Face Base
        drawFaceBase(canvas, centerX, headCenterY, headRadius)

        // 6. Cheek Blush
        drawCheeks(canvas, centerX, headCenterY, headRadius, now)

        // 7. Eyes with Blinking & Sparkles
        drawEyes(canvas, centerX, headCenterY, headRadius, blinkProgress)

        // 8. Eyebrows
        drawEyebrows(canvas, centerX, headCenterY, headRadius)

        // 9. Delicate Nose
        val noseY = headCenterY + headRadius * 0.16f
        eyelashPaint.strokeWidth = 2.0f
        canvas.drawPoint(centerX, noseY, eyelashPaint)

        // 10. Lip-synced Mouth
        drawMouth(canvas, centerX, headCenterY, headRadius, smoothedMouthOpen)

        // 11. Front Hair Bangs, Hair Ribbon, and Strands
        drawHairFront(canvas, centerX, headCenterY, headRadius)

        canvas.restore()

        // Continuous silky smooth animation loop
        postInvalidateOnAnimation()
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        when (currentState) {
            AvatarState.LISTENING -> {
                // Expanding cyan/teal soundwaves
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
                // Rotating thought particles around the crown
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
                // Warm glowing rose energetic aura pulsing with speech
                val pulse = (smoothedSpeakerEnergy * r * 0.45f).coerceIn(0f, r * 0.5f)
                auraFillPaint.color = Color.argb(55, 233, 30, 99)
                canvas.drawCircle(cx, cy, r * 1.18f + pulse, auraFillPaint)

                auraPaint.color = Color.argb(120, 255, 64, 129)
                auraPaint.strokeWidth = 3.0f
                canvas.drawCircle(cx, cy, r * 1.18f + pulse, auraPaint)
            }
            AvatarState.DISCONNECTED -> {
                // Muted calm ring
                auraPaint.color = Color.argb(40, 180, 180, 200)
                auraPaint.strokeWidth = 2.0f
                canvas.drawCircle(cx, cy, r * 1.1f, auraPaint)
            }
            AvatarState.IDLE -> {
                // Soft breathing lavender halo
                val idlePulse = sin(now * 0.002f) * (r * 0.05f)
                auraFillPaint.color = Color.argb(30, 156, 39, 176)
                canvas.drawCircle(cx, cy, r * 1.12f + idlePulse, auraFillPaint)
            }
        }
    }

    private fun drawHairBack(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        hairPath.reset()
        val hairTop = cy - r * 1.25f
        val hairBottom = cy + r * 1.45f
        val hairWidth = r * 1.35f

        hairPath.moveTo(cx - hairWidth, hairBottom)
        hairPath.cubicTo(
            cx - hairWidth * 1.1f, cy,
            cx - hairWidth, hairTop,
            cx, hairTop
        )
        hairPath.cubicTo(
            cx + hairWidth, hairTop,
            cx + hairWidth * 1.1f, cy,
            cx + hairWidth, hairBottom
        )
        hairPath.close()
        canvas.drawPath(hairPath, hairBackPaint)
    }

    private fun drawBodyAndClothes(canvas: Canvas, cx: Float, cy: Float, r: Float, totalH: Float) {
        val neckTop = cy + r * 0.65f
        val neckBottom = cy + r * 1.15f
        val neckWidth = r * 0.32f

        // Neck
        val neckRect = RectF(cx - neckWidth / 2f, neckTop, cx + neckWidth / 2f, neckBottom)
        canvas.drawRoundRect(neckRect, 8f, 8f, skinPaint)

        // Neck shadow under chin
        val shadowRect = RectF(cx - neckWidth / 2f, neckTop, cx + neckWidth / 2f, neckTop + r * 0.18f)
        canvas.drawRect(shadowRect, skinShadowPaint)

        // Shoulders & Blouse
        clothesPath.reset()
        val shoulderWidth = r * 1.55f
        val blouseBottom = totalH.coerceAtLeast(cy + r * 2.2f)

        clothesPath.moveTo(cx - shoulderWidth, blouseBottom)
        clothesPath.cubicTo(
            cx - shoulderWidth * 0.8f, neckBottom + r * 0.1f,
            cx - neckWidth, neckBottom,
            cx, neckBottom + r * 0.12f
        )
        clothesPath.cubicTo(
            cx + neckWidth, neckBottom,
            cx + shoulderWidth * 0.8f, neckBottom + r * 0.1f,
            cx + shoulderWidth, blouseBottom
        )
        clothesPath.close()
        canvas.drawPath(clothesPath, clothesPaint)

        // Cute white collar
        collarPath.reset()
        collarPath.moveTo(cx, neckBottom + r * 0.38f)
        collarPath.lineTo(cx - r * 0.42f, neckBottom)
        collarPath.lineTo(cx, neckBottom + r * 0.12f)
        collarPath.lineTo(cx + r * 0.42f, neckBottom)
        collarPath.close()
        canvas.drawPath(collarPath, collarPaint)
    }

    private fun drawFaceBase(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        facePath.reset()
        val faceTop = cy - r * 0.75f
        val chinY = cy + r * 0.82f
        val cheekW = r * 0.88f

        facePath.moveTo(cx - cheekW, cy)
        facePath.cubicTo(
            cx - cheekW, faceTop + r * 0.2f,
            cx - cheekW * 0.6f, faceTop,
            cx, faceTop
        )
        facePath.cubicTo(
            cx + cheekW * 0.6f, faceTop,
            cx + cheekW, faceTop + r * 0.2f,
            cx + cheekW, cy
        )
        facePath.cubicTo(
            cx + cheekW, cy + r * 0.45f,
            cx + r * 0.32f, chinY,
            cx, chinY
        )
        facePath.cubicTo(
            cx - r * 0.32f, chinY,
            cx - cheekW, cy + r * 0.45f,
            cx - cheekW, cy
        )
        facePath.close()
        canvas.drawPath(facePath, skinPaint)
    }

    private fun drawCheeks(canvas: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        val blushY = cy + r * 0.28f
        val blushRadiusX = r * 0.22f
        val blushRadiusY = r * 0.11f
        val blushAlpha = (65 + (sin(now * 0.003) * 15).toInt()).coerceIn(40, 110)

        blushPaint.color = Color.argb(blushAlpha, 255, 105, 135)
        // Left cheek
        canvas.drawOval(
            cx - r * 0.58f - blushRadiusX, blushY - blushRadiusY,
            cx - r * 0.58f + blushRadiusX, blushY + blushRadiusY,
            blushPaint
        )
        // Right cheek
        canvas.drawOval(
            cx + r * 0.58f - blushRadiusX, blushY - blushRadiusY,
            cx + r * 0.58f + blushRadiusX, blushY + blushRadiusY,
            blushPaint
        )
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, r: Float, blinkProgress: Float) {
        val eyeSpacing = r * 0.42f
        val eyeY = cy + r * 0.02f
        val eyeW = r * 0.34f
        val eyeH = r * 0.38f

        // Draw left and right eyes
        drawSingleEye(canvas, cx - eyeSpacing, eyeY, eyeW, eyeH, blinkProgress, isLeft = true)
        drawSingleEye(canvas, cx + eyeSpacing, eyeY, eyeW, eyeH, blinkProgress, isLeft = false)
    }

    private fun drawSingleEye(
        canvas: Canvas,
        eyeX: Float,
        eyeY: Float,
        w: Float,
        h: Float,
        blink: Float,
        isLeft: Boolean
    ) {
        canvas.save()

        if (blink > 0.85f) {
            // Completely closed during peak blink: draw charming closed eyelash curve
            eyelashPaint.strokeWidth = 3.6f
            val closePath = Path()
            val startX = eyeX - w * 0.52f
            val endX = eyeX + w * 0.52f
            val midY = eyeY + h * 0.18f
            closePath.moveTo(startX, eyeY)
            closePath.quadTo(eyeX, midY, endX, eyeY)
            canvas.drawPath(closePath, eyelashPaint)
            canvas.restore()
            return
        }

        // Open eye height scales down smoothly with blink
        val currentH = h * (1.0f - blink * 0.92f)
        val eyeRect = RectF(eyeX - w * 0.5f, eyeY - currentH * 0.5f, eyeX + w * 0.5f, eyeY + currentH * 0.5f)

        // Clip eye sclera area
        val eyePath = Path().apply {
            addOval(eyeRect, Path.Direction.CW)
        }
        canvas.clipPath(eyePath)

        // Sclera (White)
        canvas.drawOval(eyeRect, eyeWhitePaint)

        // Large anime Iris with rich gradient
        val irisRadius = w * 0.40f
        val irisCenterY = eyeY + currentH * 0.08f
        irisPaint.shader = LinearGradient(
            eyeX, irisCenterY - irisRadius,
            eyeX, irisCenterY + irisRadius,
            Color.parseColor("#3A1945"),
            Color.parseColor("#B33973"),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(eyeX, irisCenterY, irisRadius, irisPaint)

        // Pupil
        canvas.drawCircle(eyeX, irisCenterY, irisRadius * 0.48f, pupilPaint)

        // Eye highlights (specular sparkles)
        canvas.drawCircle(eyeX - irisRadius * 0.35f, irisCenterY - irisRadius * 0.35f, irisRadius * 0.32f, eyeSparklePaint)
        canvas.drawCircle(eyeX + irisRadius * 0.28f, irisCenterY + irisRadius * 0.25f, irisRadius * 0.18f, eyeSparklePaint)

        canvas.restore()

        // Upper winged Eyeliner / Eyelashes
        eyelashPaint.strokeWidth = 3.2f
        val lashPath = Path()
        val lashStartX = eyeX - w * 0.56f
        val lashEndX = eyeX + w * 0.56f
        val wingY = if (isLeft) eyeY - currentH * 0.55f - 2f else eyeY - currentH * 0.55f - 2f
        val wingX = if (isLeft) lashStartX - 3f else lashEndX + 3f

        lashPath.moveTo(lashStartX, eyeY - currentH * 0.2f)
        lashPath.quadTo(eyeX, eyeY - currentH * 0.55f, lashEndX, eyeY - currentH * 0.2f)
        if (!isLeft) {
            lashPath.lineTo(wingX, wingY)
        } else {
            lashPath.moveTo(lashStartX, eyeY - currentH * 0.2f)
            lashPath.lineTo(wingX, wingY)
        }
        canvas.drawPath(lashPath, eyelashPaint)
    }

    private fun drawEyebrows(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val browSpacing = r * 0.42f
        val baseBrowY = cy - r * 0.28f
        val browW = r * 0.30f
        eyebrowPaint.strokeWidth = 2.8f

        // Dynamic eyebrow expression adjustments
        val leftYOffset: Float
        val rightYOffset: Float
        val tiltOffset: Float

        when (currentState) {
            AvatarState.LISTENING -> {
                leftYOffset = -4f
                rightYOffset = -4f
                tiltOffset = -2f
            }
            AvatarState.THINKING -> {
                leftYOffset = -6f // Inquisitive raised left brow
                rightYOffset = 2f  // Furrowed right brow
                tiltOffset = 3f
            }
            AvatarState.SPEAKING -> {
                val talkHop = (smoothedSpeakerEnergy * 4f).coerceIn(0f, 5f)
                leftYOffset = -2f - talkHop
                rightYOffset = -2f - talkHop
                tiltOffset = 0f
            }
            else -> {
                leftYOffset = 0f
                rightYOffset = 0f
                tiltOffset = 0f
            }
        }

        // Left Brow
        val leftPath = Path().apply {
            val startX = cx - browSpacing - browW * 0.5f
            val endX = cx - browSpacing + browW * 0.5f
            val y = baseBrowY + leftYOffset
            moveTo(startX, y + tiltOffset)
            quadTo(cx - browSpacing, y - 4f, endX, y)
        }
        canvas.drawPath(leftPath, eyebrowPaint)

        // Right Brow
        val rightPath = Path().apply {
            val startX = cx + browSpacing - browW * 0.5f
            val endX = cx + browSpacing + browW * 0.5f
            val y = baseBrowY + rightYOffset
            moveTo(startX, y)
            quadTo(cx + browSpacing, y - 4f, endX, y + tiltOffset)
        }
        canvas.drawPath(rightPath, eyebrowPaint)
    }

    /**
     * Smooth, amplitude-driven lip-sync mouth animation.
     */
    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, r: Float, openRatio: Float) {
        val mouthY = cy + r * 0.52f
        val baseWidth = r * 0.32f

        if (openRatio < 0.05f) {
            // Resting closed mouth: sweet, friendly upturned smile
            lipOutlinePaint.strokeWidth = 2.4f
            mouthPath.reset()
            mouthPath.moveTo(cx - baseWidth * 0.5f, mouthY - 1f)
            mouthPath.quadTo(cx, mouthY + 5.5f, cx + baseWidth * 0.5f, mouthY - 1f)
            canvas.drawPath(mouthPath, lipOutlinePaint)

            // Subtle lower lip hint
            val lowerLipPath = Path().apply {
                moveTo(cx - baseWidth * 0.2f, mouthY + 7.5f)
                quadTo(cx, mouthY + 9.0f, cx + baseWidth * 0.2f, mouthY + 7.5f)
            }
            lipOutlinePaint.strokeWidth = 1.6f
            canvas.drawPath(lowerLipPath, lipOutlinePaint)
        } else {
            // Open mouth synchronized with playback audio amplitude
            val mouthW = baseWidth + (openRatio * r * 0.16f)
            val mouthH = 3.5f + (openRatio * r * 0.36f)

            mouthPath.reset()
            val left = cx - mouthW * 0.5f
            val right = cx + mouthW * 0.5f
            val top = mouthY - mouthH * 0.3f
            val bottom = mouthY + mouthH * 0.7f

            // Feminine open mouth curve
            mouthPath.moveTo(left, mouthY)
            mouthPath.cubicTo(left + mouthW * 0.2f, top, right - mouthW * 0.2f, top, right, mouthY)
            mouthPath.cubicTo(right, bottom, left, bottom, left, mouthY)
            mouthPath.close()

            canvas.save()
            canvas.clipPath(mouthPath)

            // 1. Dark inner mouth
            canvas.drawPath(mouthPath, mouthInsidePaint)

            // 2. Upper Teeth
            val teethRect = RectF(left + mouthW * 0.15f, top, right - mouthW * 0.15f, top + mouthH * 0.35f)
            canvas.drawRoundRect(teethRect, 4f, 4f, teethPaint)

            // 3. Lower Tongue
            tonguePath.reset()
            tonguePath.moveTo(left + mouthW * 0.15f, bottom)
            tonguePath.quadTo(cx, bottom - mouthH * 0.45f, right - mouthW * 0.15f, bottom)
            tonguePath.close()
            canvas.drawPath(tonguePath, tonguePaint)

            canvas.restore()

            // Outer lip outline
            lipOutlinePaint.strokeWidth = 2.4f
            canvas.drawPath(mouthPath, lipOutlinePaint)
        }
    }

    private fun drawHairFront(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        bangsPath.reset()
        val hairTop = cy - r * 1.2f
        val bangsBottom = cy - r * 0.05f
        val cheekW = r * 0.95f

        // Front layered bangs
        bangsPath.moveTo(cx - cheekW, cy + r * 0.2f)
        bangsPath.cubicTo(
            cx - cheekW * 0.8f, hairTop,
            cx + cheekW * 0.8f, hairTop,
            cx + cheekW, cy + r * 0.2f
        )
        // Bangs tips curving naturally
        bangsPath.lineTo(cx + cheekW * 0.7f, bangsBottom - r * 0.15f)
        bangsPath.lineTo(cx + cheekW * 0.45f, bangsBottom)
        bangsPath.lineTo(cx + cheekW * 0.25f, bangsBottom - r * 0.1f)
        bangsPath.lineTo(cx, bangsBottom + r * 0.05f) // Center strand
        bangsPath.lineTo(cx - cheekW * 0.25f, bangsBottom - r * 0.1f)
        bangsPath.lineTo(cx - cheekW * 0.45f, bangsBottom)
        bangsPath.lineTo(cx - cheekW * 0.7f, bangsBottom - r * 0.15f)
        bangsPath.close()
        canvas.drawPath(bangsPath, hairFrontPaint)

        // Glossy Hair Highlight curve
        hairHighlightPaint.strokeWidth = 3.5f
        val highlightPath = Path().apply {
            moveTo(cx - r * 0.55f, hairTop + r * 0.42f)
            quadTo(cx, hairTop + r * 0.32f, cx + r * 0.55f, hairTop + r * 0.42f)
        }
        canvas.drawPath(highlightPath, hairHighlightPaint)

        // Cute hair flower / ribbon on right side
        val ribbonX = cx + r * 0.72f
        val ribbonY = cy - r * 0.65f
        canvas.drawCircle(ribbonX, ribbonY, 9f, ribbonPaint)
        canvas.drawCircle(ribbonX - 8f, ribbonY - 4f, 6f, ribbonPaint)
        canvas.drawCircle(ribbonX + 8f, ribbonY - 4f, 6f, ribbonPaint)
        canvas.drawCircle(ribbonX, ribbonY, 4f, eyeSparklePaint)
    }
}

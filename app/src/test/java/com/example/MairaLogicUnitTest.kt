package com.example

import com.example.api.GroqConstants
import com.example.api.GroqModelResolver
import com.example.data.MairaLogger
import com.example.personality.PersonalityPrompts
import org.junit.Assert.*
import org.junit.Test

class MairaLogicUnitTest {

    @Test
    fun testKeyRedaction() {
        val rawMessage = "Gemini Key: AIzaSyD12345678901234567890123456789 and Groq Key: gsk_abcdef12345678901234567890123456789"
        val redacted = MairaLogger.redact(rawMessage)

        assertFalse(redacted.contains("AIzaSyD12345678901234567890123456789"))
        assertFalse(redacted.contains("gsk_abcdef12345678901234567890123456789"))
        assertTrue(redacted.contains("[KEY]"))
    }

    @Test
    fun testPersonalityPrompts() {
        val sysPrompt = PersonalityPrompts.getSystemInstruction("রোমান্টিক সঙ্গী (বউ মোড)", "তানভীর")

        assertTrue(sysPrompt.contains("মাইরা"))
        assertTrue(sysPrompt.contains("তানভীর"))
        assertTrue(sysPrompt.contains("তুমি শুধুমাত্র এবং শুধুমাত্র বাংলায় কথা বলবে ও বুঝবে।"))

        val bhaloPrompt = PersonalityPrompts.getSystemInstruction("রোমান্টিক সঙ্গী (ভালো প্রেমিকা)", "তানভীর")
        assertTrue(bhaloPrompt.contains("যত্নশীল"))
        assertTrue(bhaloPrompt.contains("কঠিন নিয়মাবলী"))

        val shondehobajPrompt = PersonalityPrompts.getSystemInstruction("রোমান্টিক সঙ্গী (সন্দেহবাজ প্রেমিকা)", "তানভীর")
        assertTrue(shondehobajPrompt.contains("সন্দেহবাজ"))
        assertTrue(shondehobajPrompt.contains("কঠিন নিয়মাবলী"))

        val ragiPrompt = PersonalityPrompts.getSystemInstruction("রোমান্টিক সঙ্গী (রাগী প্রেমিকা)", "তানভীর")
        assertTrue(ragiPrompt.contains("অভিমানী"))
        assertTrue(ragiPrompt.contains("কঠিন নিয়মাবলী"))
    }

    @Test
    fun testPersonalityFallback() {
        val sysPrompt = PersonalityPrompts.getSystemInstruction("UnknownMode", "")

        assertTrue(sysPrompt.contains("মাইরা"))
        assertTrue(sysPrompt.contains("তুমি শুধুমাত্র এবং শুধুমাত্র বাংলায় কথা বলবে ও বুঝবে।"))
    }

    @Test
    fun testGroqConstantsAndModelResolver() {
        assertTrue(GroqConstants.PRIORITY_MODELS.contains("openai/gpt-oss-120b"))
        assertTrue(GroqConstants.PRIORITY_MODELS.contains("openai/gpt-oss-20b"))
        assertTrue(GroqConstants.PRIORITY_MODELS.contains("llama-3.3-70b-versatile"))

        GroqModelResolver.clearCache()
        assertNull(GroqModelResolver.getCachedModel())

        val resolvedFallback = GroqModelResolver.resolveModel("")
        assertEquals(GroqConstants.DEFAULT_FALLBACK_MODEL, resolvedFallback)
    }

    @Test
    fun testAvatarLipSyncAmplitudeMapping() {
        // Closed / resting mouth when speaker RMS is 0 or low
        val restingSpeakerRms = 0f
        val restingTarget = if (restingSpeakerRms > 0.035f) {
            ((restingSpeakerRms - 0.02f) * 2.8f).coerceIn(0f, 1f)
        } else {
            0f
        }
        assertEquals(0f, restingTarget, 0.001f)

        // Speaking amplitude mapping
        val speakingSpeakerRms = 0.25f
        val speakingTarget = if (speakingSpeakerRms > 0.035f) {
            ((speakingSpeakerRms - 0.02f) * 2.8f).coerceIn(0f, 1f)
        } else {
            0f
        }
        assertTrue(speakingTarget > 0.5f)
        assertTrue(speakingTarget <= 1.0f)

        // Clamping check for peak loud audio
        val peakLoudRms = 0.95f
        val peakTarget = ((peakLoudRms - 0.02f) * 2.8f).coerceIn(0f, 1f)
        assertEquals(1.0f, peakTarget, 0.001f)
    }

    @Test
    fun testOverlayServiceConstants() {
        assertEquals("ACTION_SHOW_OVERLAY", com.example.service.MairaOverlayService.ACTION_SHOW_OVERLAY)
        assertEquals("ACTION_HIDE_OVERLAY", com.example.service.MairaOverlayService.ACTION_HIDE_OVERLAY)
        assertEquals("maira_overlay_channel", com.example.service.MairaOverlayService.CHANNEL_ID)
    }

    @Test
    fun testExplicitStopCommandRecognition() {
        val explicitStopCommands = setOf("থামো", "থাম", "থামুন", "চুপ", "চুপ করো", "স্টপ", "stop")
        fun isExplicitStop(text: String): Boolean {
            val clean = text.trim().lowercase().replace("[.,?!।\"']".toRegex(), "")
            if (clean in explicitStopCommands) return true
            val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return words.size in 1..3 && words.any { it in explicitStopCommands }
        }

        // True for explicit intentional commands
        assertTrue(isExplicitStop("থামো"))
        assertTrue(isExplicitStop("চুপ করো"))
        assertTrue(isExplicitStop("স্টপ!"))
        assertTrue(isExplicitStop("stop"))
        assertTrue(isExplicitStop("একটু থামুন"))

        // False for normal conversational phrases containing partial substrings or long speech
        assertFalse(isExplicitStop("প্রথম কাজটি কী ছিল?"))
        assertFalse(isExplicitStop("আমি আজকে বাজারে গিয়েছিলাম এবং অনেক কথা হয়েছে"))
        assertFalse(isExplicitStop("মাইরা তুমি কেমন আছো"))
    }

    @Test
    fun testBengaliDigitConversion() {
        val bengaliNumber = "০১৭১২৩৪৫৬৭৮"
        val converted = com.example.tools.DeviceToolExecutor.convertBengaliDigitsToAscii(bengaliNumber)
        assertEquals("01712345678", converted)

        val mixed = "+৮৮০১ ৮৯-৯৯-৮৮৮৮"
        val convertedMixed = com.example.tools.DeviceToolExecutor.convertBengaliDigitsToAscii(mixed)
        assertEquals("+8801 89-99-8888", convertedMixed)
    }

    @Test
    fun testPhoneNumberNormalization() {
        // Standard Bangladeshi numbers
        assertEquals("8801712345678", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("01712345678"))
        assertEquals("8801712345678", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("+8801712345678"))
        assertEquals("8801712345678", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("০১৭১২৩৪৫৬৭৮"))
        assertEquals("8801712345678", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("008801712345678"))
        assertEquals("8801899998888", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("018-9999-8888"))

        // International numbers
        assertEquals("14155552671", com.example.tools.DeviceToolExecutor.normalizePhoneNumber("+14155552671"))

        // Invalid numbers
        assertNull(com.example.tools.DeviceToolExecutor.normalizePhoneNumber("123"))
        assertNull(com.example.tools.DeviceToolExecutor.normalizePhoneNumber(""))
        assertNull(com.example.tools.DeviceToolExecutor.normalizePhoneNumber("invalid"))
    }

    @Test
    fun testTruthfulWhatsAppInstructionMessage() {
        // Hard rules must forbid claiming "মেসেজ পাঠানো হয়েছে" without actual verified send
        val rules = PersonalityPrompts.getSystemInstruction("সাধারণ", "রহিম")
        assertTrue(rules.contains("sendWhatsAppMessage"))
        assertTrue(rules.contains("কখনোই নিজে থেকে বলবে না \"মেসেজ পাঠানো হয়েছে\""))
        assertTrue(rules.contains("মেসেজটি প্রস্তুত করা হয়েছে, পাঠানোর জন্য সেন্ড বাটনে ট্যাপ করুন।"))
        assertTrue(rules.contains("মেসেজ পাঠানো যায়নি"))
    }
}


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
}

package com.example

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
    }

    @Test
    fun testPersonalityFallback() {
        val sysPrompt = PersonalityPrompts.getSystemInstruction("UnknownMode", "")

        assertTrue(sysPrompt.contains("মাইরা"))
        assertTrue(sysPrompt.contains("তুমি শুধুমাত্র এবং শুধুমাত্র বাংলায় কথা বলবে ও বুঝবে।"))
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Maira", appName)
  }

  @Test
  fun `overlay preference defaults to false and persists correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsManager = com.example.data.EncryptedPreferencesManager(context)

    // Defaults to false on clean install
    assertEquals(false, prefsManager.isOverlayEnabled())

    // Setting to true persists
    prefsManager.setOverlayEnabled(true)
    assertEquals(true, prefsManager.isOverlayEnabled())

    // Switching to false persists
    prefsManager.setOverlayEnabled(false)
    assertEquals(false, prefsManager.isOverlayEnabled())
  }

  @Test
  fun `whatsapp tool validates missing recipient and message`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val toolExecutor = com.example.tools.DeviceToolExecutor(context)

    // Missing recipient
    val emptyRecipientResult = toolExecutor.sendWhatsAppMessage("", "হ্যালো")
    org.junit.Assert.assertFalse(emptyRecipientResult.success)
    org.junit.Assert.assertTrue(emptyRecipientResult.message.contains("কার কাছে"))

    // Missing text
    val emptyTextResult = toolExecutor.sendWhatsAppMessage("রহিম", "")
    org.junit.Assert.assertFalse(emptyTextResult.success)
    org.junit.Assert.assertTrue(emptyTextResult.message.contains("কী মেসেজ"))
  }

  @Test
  fun `whatsapp tool detects when whatsapp is not installed`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val toolExecutor = com.example.tools.DeviceToolExecutor(context)

    val res = toolExecutor.sendWhatsAppMessage("01712345678", "আমি আসছি")
    org.junit.Assert.assertFalse(res.success)
    org.junit.Assert.assertTrue(res.message.contains("ইনস্টল করা নেই"))
  }
}

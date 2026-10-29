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
}

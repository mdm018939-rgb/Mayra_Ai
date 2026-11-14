package com.example

import com.example.updater.AppUpdateChecker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateCheckerUnitTest {

    @Test
    fun `compareVersions compares numeric segments accurately`() {
        assertTrue(AppUpdateChecker.compareVersions("v1.1", "1.0") > 0)
        assertTrue(AppUpdateChecker.compareVersions("1.10", "1.9") > 0)
        assertTrue(AppUpdateChecker.compareVersions("v2.0.0", "1.99.99") > 0)
        assertEquals(0, AppUpdateChecker.compareVersions("v1.0", "1.0"))
        assertEquals(0, AppUpdateChecker.compareVersions("v1.0.0", "1.0"))
        assertTrue(AppUpdateChecker.compareVersions("v1.0", "1.1") < 0)
    }

    @Test
    fun `parseReleaseJson returns AppUpdateInfo for newer release with apk asset`() {
        val json = """
            {
              "tag_name": "v1.10",
              "html_url": "https://github.com/mdm018939-rgb/Mayra_Ai/releases/tag/v1.10",
              "body": "নতুন ফিচার যোগ করা হয়েছে এবং বাগ ফিক্স করা হয়েছে।",
              "assets": [
                {
                  "name": "source.zip",
                  "browser_download_url": "https://example.com/source.zip"
                },
                {
                  "name": "maira-v1.10.apk",
                  "browser_download_url": "https://github.com/mdm018939-rgb/Mayra_Ai/releases/download/v1.10/maira-v1.10.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateChecker.parseReleaseJson(json, "1.9")
        assertNotNull(info)
        assertEquals("1.10", info!!.latestVersion)
        assertEquals("নতুন ফিচার যোগ করা হয়েছে এবং বাগ ফিক্স করা হয়েছে।", info.releaseNotes)
        assertEquals(
            "https://github.com/mdm018939-rgb/Mayra_Ai/releases/download/v1.10/maira-v1.10.apk",
            info.downloadUrl
        )
        assertFalse(info.isForceUpdate)
    }

    @Test
    fun `parseReleaseJson detects force tag in body and strips marker`() {
        val json = """
            {
              "tag_name": "v1.2",
              "html_url": "https://github.com/mdm018939-rgb/Mayra_Ai/releases/tag/v1.2",
              "body": "[force] জরুরি নিরাপত্তা আপডেট",
              "assets": [
                {
                  "name": "app-release.apk",
                  "browser_download_url": "https://example.com/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateChecker.parseReleaseJson(json, "1.0")
        assertNotNull(info)
        assertTrue(info!!.isForceUpdate)
        assertEquals("জরুরি নিরাপত্তা আপডেট", info.releaseNotes)
    }

    @Test
    fun `parseReleaseJson returns null when version is same or older or json invalid`() {
        val sameVersionJson = """
            {
              "tag_name": "v1.0",
              "body": "Same version",
              "assets": [
                {
                  "name": "app.apk",
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()

        assertNull(AppUpdateChecker.parseReleaseJson(sameVersionJson, "1.0"))
        assertNull(AppUpdateChecker.parseReleaseJson("not-json", "1.0"))
    }
}

package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.AppLockUtils
import com.example.security.AppLockViewModel
import com.example.security.PasswordRemoteConfigSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("maira_lock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sha256 and verifyPassword validate matching and non-matching inputs`() {
        val sampleSecret = "demo-secret-123"
        val computedHash = AppLockUtils.sha256(sampleSecret)

        assertEquals(64, computedHash.length)
        assertTrue(AppLockUtils.verifyPassword(sampleSecret, computedHash))
        assertTrue(AppLockUtils.verifyPassword(sampleSecret, computedHash.uppercase()))
        assertFalse(AppLockUtils.verifyPassword("wrong-secret", computedHash))
        assertFalse(AppLockUtils.verifyPassword("", computedHash))
        assertFalse(AppLockUtils.verifyPassword(sampleSecret, ""))
    }

    @Test
    fun `toBengaliDigits converts numbers accurately`() {
        assertEquals("৩০", AppLockUtils.toBengaliDigits(30))
        assertEquals("০", AppLockUtils.toBengaliDigits(0))
        assertEquals("১২৩", AppLockUtils.toBengaliDigits(123))
    }

    @Test
    fun `unlocks with correct password and relocks when remote hash changes`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val firstPass = "first-demo-pass"
        val secondPass = "second-demo-pass"
        var remoteHash = AppLockUtils.sha256(firstPass)

        val fakeSource = object : PasswordRemoteConfigSource {
            override suspend fun fetchAndActivate(): Result<String> = Result.success(remoteHash)
            override fun getCachedPasswordHash(): String = remoteHash
        }

        val viewModel = AppLockViewModel(
            application = app,
            remoteConfigSource = fakeSource,
            networkChecker = { true }
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.hasValidConfigHash)

        viewModel.submitPassword(firstPass)
        assertTrue(viewModel.uiState.value.isUnlocked)

        // Simulate admin updating password_hash in Firebase Remote Config
        remoteHash = AppLockUtils.sha256(secondPass)
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("পাসওয়ার্ড পরিবর্তন") == true)

        viewModel.submitPassword(firstPass)
        assertFalse(viewModel.uiState.value.isUnlocked)

        viewModel.submitPassword(secondPass)
        assertTrue(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `three failed attempts trigger 30 second lockout in Bengali`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val correctPass = "valid-pass"
        val hash = AppLockUtils.sha256(correctPass)

        val fakeSource = object : PasswordRemoteConfigSource {
            override suspend fun fetchAndActivate(): Result<String> = Result.success(hash)
            override fun getCachedPasswordHash(): String = hash
        }

        val viewModel = AppLockViewModel(
            application = app,
            remoteConfigSource = fakeSource,
            networkChecker = { true }
        )
        advanceUntilIdle()

        viewModel.submitPassword("bad-1")
        assertEquals(1, viewModel.uiState.value.failedAttempts)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("ভুল পাসওয়ার্ড") == true)

        viewModel.submitPassword("bad-2")
        assertEquals(2, viewModel.uiState.value.failedAttempts)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("ভুল পাসওয়ার্ড") == true)

        viewModel.submitPassword("bad-3")
        assertTrue(viewModel.uiState.value.lockoutSecondsRemaining in 29..30)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("সেকেন্ড অপেক্ষা করুন") == true)

        // Even the right password cannot unlock during lockout
        viewModel.submitPassword(correctPass)
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun `offline mode uses cached hash when available and shows Bengali error when absent`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cachedPass = "cached-pass"
        val cachedHash = AppLockUtils.sha256(cachedPass)

        val noCacheSource = object : PasswordRemoteConfigSource {
            override suspend fun fetchAndActivate(): Result<String> = Result.failure(RuntimeException("Offline"))
            override fun getCachedPasswordHash(): String = ""
        }

        val noCacheVm = AppLockViewModel(
            application = app,
            remoteConfigSource = noCacheSource,
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertTrue(noCacheVm.uiState.value.isOffline)
        assertFalse(noCacheVm.uiState.value.hasValidConfigHash)
        assertEquals("ইন্টারনেট সংযোগ নেই", noCacheVm.uiState.value.errorMessage)

        val cachedSource = object : PasswordRemoteConfigSource {
            override suspend fun fetchAndActivate(): Result<String> = Result.failure(RuntimeException("Offline"))
            override fun getCachedPasswordHash(): String = cachedHash
        }

        val cachedVm = AppLockViewModel(
            application = app,
            remoteConfigSource = cachedSource,
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertTrue(cachedVm.uiState.value.isOffline)
        assertTrue(cachedVm.uiState.value.isUsingCachedConfig)
        assertTrue(cachedVm.uiState.value.hasValidConfigHash)

        cachedVm.submitPassword(cachedPass)
        assertTrue(cachedVm.uiState.value.isUnlocked)
    }
}

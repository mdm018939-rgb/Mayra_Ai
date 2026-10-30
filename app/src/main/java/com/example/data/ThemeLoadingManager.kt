package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object ThemeLoadingManager {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var themeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun triggerThemeChange() {
        themeJob?.cancel()
        themeJob = scope.launch {
            _isLoading.value = true
            try {
                withTimeoutOrNull(1500L) {
                    delay(750L)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissThemeLoading() {
        themeJob?.cancel()
        _isLoading.value = false
    }
}

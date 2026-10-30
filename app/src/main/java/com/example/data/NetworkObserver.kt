package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

class NetworkObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    private val _isConnectedState = MutableStateFlow(isCurrentlyConnected())
    val isConnectedState: StateFlow<Boolean> = _isConnectedState.asStateFlow()

    private val rawStateFlow = MutableStateFlow(isCurrentlyConnected())

    init {
        // Debounce network changes by 1000ms to avoid UI flickering during Wi-Fi to mobile data switching
        rawStateFlow
            .debounce(1000L)
            .distinctUntilChanged()
            .onEach { isConnected ->
                _isConnectedState.value = isConnected
            }
            .launchIn(scope)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkAndUpdateState()
            }

            override fun onLost(network: Network) {
                checkAndUpdateState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                checkAndUpdateState()
            }
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            MairaLogger.log(
                MairaLogger.Category.CONNECTION,
                MairaLogger.Level.ERROR,
                "NetworkCallback রেজিস্ট্রেশন করতে ব্যর্থ: ${e.message}"
            )
        }
    }

    fun checkAndUpdateState() {
        val currentConnected = isCurrentlyConnected()
        rawStateFlow.value = currentConnected
    }

    fun forceRefresh() {
        val currentConnected = isCurrentlyConnected()
        rawStateFlow.value = currentConnected
        _isConnectedState.value = currentConnected
    }
}

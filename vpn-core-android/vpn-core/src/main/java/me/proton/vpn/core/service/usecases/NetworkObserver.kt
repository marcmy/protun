/*
 * Copyright (c) 2025 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.vpn.core.service.usecases

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.proton.vpn.core.api.Logger
import uniffi.protun.ConnectivityEvent
import uniffi.protun.LogLevel

/**
 * Emits [ConnectivityEvent] for VPN's default underlying network, which VPN's protected sockets
 * should bind to.
 *
 * Implementations will use dedicated HandlerThread("NetworkObserver") to run network callbacks on.
 */
internal interface NetworkObserver {

    /**
     * Whether an underlying network is currently available. A snapshot, read once when starting a
     * connection to seed its initial config; ongoing changes are delivered via [events].
     */
    val isNetworkAvailable: Boolean

    /**
     * Connectivity transitions of the active underlying network ([ConnectivityEvent.UP] /
     * [ConnectivityEvent.DOWN] / [ConnectivityEvent.NETWORK_SWITCH]), to forward to the connection.
     */
    val events: SharedFlow<ConnectivityEvent>
}

internal fun NetworkObserver(appContext: Context, logger: Logger): NetworkObserver {
    val connectivityManager = appContext.getSystemService<ConnectivityManager>()
        ?: error("ConnectivityManager is not available.")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BestMatchingNetworkObserver(connectivityManager, logger)
    } else {
        LegacyNetworkObserver(appContext, connectivityManager, logger)
    }
}

/**
 * API 31+ implementation. [ConnectivityManager.registerBestMatchingNetworkCallback] tracks the
 * single best network matching the request (here: an internet, non-VPN network), letting the
 * framework apply its own scoring - which should match what [android.net.VpnService.protect] binds to.
 */
@RequiresApi(31)
private class BestMatchingNetworkObserver(
    private val connectivityManager: ConnectivityManager,
    private val logger: Logger,
) : NetworkObserver {

    // The current best network reported by the framework (may not be validated yet).
    private var bestNetwork: Network? = null
    // The last usable (validated) network we reported; the identity used to detect transitions.
    private var activeNetwork = MutableStateFlow<Network?>(null)

    private val _events = MutableSharedFlow<ConnectivityEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ConnectivityEvent> = _events.asSharedFlow()
    override val isNetworkAvailable get() = activeNetwork.value != null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            bestNetwork = network
            evaluate(network, connectivityManager.getNetworkCapabilities(network))
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == bestNetwork)
                evaluate(network, capabilities)
        }

        override fun onLost(network: Network) {
            if (network == bestNetwork) {
                bestNetwork = null
                update(null)
            }
        }
    }

    private fun evaluate(network: Network, capabilities: NetworkCapabilities?) {
        if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            if (network != activeNetwork.value) {
                logger.logNetwork(network, capabilities, connectivityManager.getLinkProperties(network))
                update(network)
            }
        } else {
            update(null)
        }
    }

    private fun update(newActiveNetwork: Network?) {
        val oldActiveNetwork = activeNetwork.value
        val event = when {
            oldActiveNetwork == null && newActiveNetwork != null -> ConnectivityEvent.UP
            oldActiveNetwork != null && newActiveNetwork == null -> ConnectivityEvent.DOWN
            oldActiveNetwork != newActiveNetwork -> ConnectivityEvent.NETWORK_SWITCH
            else -> null
        }
        activeNetwork.value = newActiveNetwork
        if (event != null)
            _events.tryEmit(event)
    }

    init {
        // registerBestMatchingNetworkCallback requires a Handler; use a dedicated background
        // thread so callbacks don't run on the main thread.
        val handlerThread = HandlerThread("NetworkObserver").apply { start() }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager.registerBestMatchingNetworkCallback(request, networkCallback, Handler(handlerThread.looper))
    }
}

/**
 * API 25-30 implementation based on deprecated [NetworkInfo].
 */
@Suppress("DEPRECATION")
private class LegacyNetworkObserver(
    appContext: Context,
    private val connectivityManager: ConnectivityManager,
    private val logger: Logger,
) : NetworkObserver {

    private val _events = MutableSharedFlow<ConnectivityEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<ConnectivityEvent> = _events.asSharedFlow()

    private var lastActiveNetworkInfo: NetworkInfo? = null

    override val isNetworkAvailable get() = lastActiveNetworkInfo?.isConnected == true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION != intent.action) return

            val eventInfo = intent.extras?.get(ConnectivityManager.EXTRA_NETWORK_INFO) as? NetworkInfo
            if (eventInfo != null && eventInfo.type != ConnectivityManager.TYPE_VPN) {
                val newActiveNetworkInfo = connectivityManager.activeNetworkInfo

                // Ignore if connectivity action is not about connecting, but an active network is
                // present to avoid duplicated switch events. On some network switches we'll receive
                // DISCONNECTED for the old network first followed by CONNECTED for the new one.
                if (eventInfo.state != NetworkInfo.State.CONNECTED && newActiveNetworkInfo != null)
                    return

                val oldActiveNetworkInfo = lastActiveNetworkInfo
                updateNetworkInfo(newActiveNetworkInfo)
                val event = when {
                    oldActiveNetworkInfo != null && newActiveNetworkInfo == null -> ConnectivityEvent.DOWN
                    oldActiveNetworkInfo == null && newActiveNetworkInfo != null -> ConnectivityEvent.UP
                    oldActiveNetworkInfo != null && newActiveNetworkInfo != null -> ConnectivityEvent.NETWORK_SWITCH
                    else -> null
                }
                event?.let { _events.tryEmit(it) }
            }
        }
    }

    init {
        val handlerThread = HandlerThread("NetworkObserver").apply { start() }
        val handler = Handler(handlerThread.looper)
        handler.post {
            updateNetworkInfo(connectivityManager.activeNetworkInfo)
        }
        appContext.registerReceiver(
            receiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
            null,
            handler
        )
    }

    private fun updateNetworkInfo(newNetworkInfo: NetworkInfo?) {
        logger.log(LogLevel.INFO, "NetworkObserver: new network=$newNetworkInfo")
        lastActiveNetworkInfo = newNetworkInfo
    }
}

fun Logger.logNetwork(
    network: Network,
    capabilities: NetworkCapabilities,
    linkProperties: LinkProperties?
) {
    val isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    val type = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Other"
    }
    log(LogLevel.INFO, "NetworkObserver: network validated $network $type, VPN: $isVpn, addresses: ${linkProperties?.linkAddresses}")
}
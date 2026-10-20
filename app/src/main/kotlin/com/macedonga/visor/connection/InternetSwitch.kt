package com.macedonga.visor.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.Responder
import com.macedonga.visor.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * svc41108, internet_switch: the Display asks how the phone's internet is (type 6) and is told (type 3): connected,
 * metered, and the phone's DNS servers. Told it is connected, the Display may carry its apps' traffic to the phone.
 */
@Singleton
class InternetSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: Provider<EventLog>,
) : Responder {

    override suspend fun respond(message: DataXMessage, connection: DataXConnection) {
        if (message.info.messageType != MT_LISTEN) return
        val state = phoneInternet()
        log.get().add("Internet: the Display asked; the phone is ${if (state.connected) "online" else "offline"}" + if (state.metered) ", metered" else "")
        connection.send(DataXConnection.mirror(message.info.channelId), encode(state), messageType = MT_COMPANION_STATE)
    }

    private data class PhoneInternet(val connected: Boolean, val metered: Boolean, val dns: List<String>)

    private fun phoneInternet(): PhoneInternet {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return PhoneInternet(false, false, emptyList())
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val dns = network?.let { cm.getLinkProperties(it) }?.dnsServers.orEmpty().mapNotNull { it.hostAddress }
        return PhoneInternet(online, cm.isActiveNetworkMetered, dns)
    }

    private fun encode(s: PhoneInternet): ByteArray =
        Wire.fieldVarint(1, if (s.connected) 1 else 0) + Wire.fieldVarint(2, if (s.metered) 1 else 0) +
            Wire.fieldBytes(3, s.dns.fold(ByteArray(0)) { acc, a -> acc + Wire.fieldBytes(1, a.encodeToByteArray()) })

    companion object {
        const val SVC = 41108
        private const val MT_COMPANION_STATE = 3
        private const val MT_LISTEN = 6
    }
}

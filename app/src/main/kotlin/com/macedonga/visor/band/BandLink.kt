package com.macedonga.visor.band

import android.content.Context
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.identity.BundleCredentials
import com.macedonga.visor.core.identity.BundleState
import com.macedonga.visor.core.identity.IdentityBundle
import com.macedonga.visor.core.identity.IdentityStore
import com.macedonga.visor.core.identity.IdentityTrustExchange
import com.macedonga.visor.core.identity.nowIso
import com.macedonga.visor.core.protocol.Band
import com.macedonga.visor.core.protocol.ServiceId
import com.macedonga.visor.core.session.GlassesSession
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.core.session.LinkStateChanged
import com.macedonga.visor.core.session.ProtocolError
import com.macedonga.visor.core.session.ServiceData
import com.macedonga.visor.core.session.SessionCommand
import com.macedonga.visor.core.transport.BluetoothLeTransport
import com.macedonga.visor.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The band as last heard from. */
data class BandState(
    val serial: String? = null,
    val link: LinkState = LinkState.DISCONNECTED,
    val battery: Int? = null,
    val charging: Boolean? = null,
    val leftHanded: Boolean? = null,
    val at: Long? = null,
    // The Display holds the band; the phone steps back, as Meta's app does.
    val leased: Boolean = false,
)

/**
 * The phone's own link to the Neural Band: up while Visor runs for the band's Display, down while the Display holds a
 * lease on it (svc150). It reads the battery and the wrist over svc52822 and sets the wrist.
 */
@Singleton
class BandLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connection: GlassesConnection,
    private val bands: Bands,
    private val store: IdentityStore,
    private val log: EventLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()
    private val seq = AtomicInteger(1)
    private val leased = MutableStateFlow(false)
    private val held = MutableStateFlow(true)

    @Volatile private var session: GlassesSession? = null
    private var job: Job? = null

    private val _state = MutableStateFlow(BandState())
    val state: StateFlow<BandState> = _state.asStateFlow()

    init {
        scope.launch {
            // A lease lives on the Display's link: it ends with it.
            connection.state.collect { if (it.link != LinkState.READY && leased.value) leased.value = false }
        }
        scope.launch {
            combine(connection.running, connection.pair, bands.all, leased, held) { running, pair, _, lease, hold ->
                bands.forGlasses(pair)?.takeIf { running && hold && !lease }
            }.distinctUntilChanged().collect { band -> gate.withLock { switchTo(band) } }
        }
        scope.launch { leased.collect { lease -> _state.update { it.copy(leased = lease) } } }
    }

    /** The Display's lease on the band, from svc150: a non-relay one sends the phone's link away until it ends. */
    fun leased(on: Boolean) {
        leased.value = on
    }

    /** Let go of the band for a while, so another session can have it: setting it up again. */
    suspend fun release() {
        held.value = false
        gate.withLock { switchTo(null) }
    }

    fun hold() {
        held.value = true
    }

    /** Put the band on [left] or the right wrist; read back at once. */
    fun setLeftHanded(left: Boolean): Boolean {
        val running = session ?: return false
        if (_state.value.link != LinkState.READY) return false
        running.submit(
            SessionCommand { s ->
                val mux = s.connection ?: return@SessionCommand
                mux.sendOnService(ServiceId.WIRELESS_INPUT.value, Band.REQUEST_TYPE, Band.configRequest(seq.getAndIncrement(), left))
                mux.sendOnService(ServiceId.WIRELESS_INPUT.value, Band.REQUEST_TYPE, Band.configRequest(seq.getAndIncrement()))
            },
        )
        return true
    }

    private suspend fun switchTo(band: BandRecord?) {
        if (band != null && _state.value.serial == band.serial && session != null) return
        session?.let {
            log.add("Band: link down")
            it.stop()
        }
        job?.cancel()
        session = null
        job = null
        if (band == null) {
            _state.update { it.copy(link = LinkState.DISCONNECTED) }
            return
        }
        val key = band.key ?: store.ownerKeyPem() ?: return
        if (_state.value.serial != band.serial) _state.value = BandState(serial = band.serial, leased = leased.value)
        val identity = IdentityBundle(key, band.address, band.serial, nowIso(), "band", BundleState.VERIFIED)
        val opened = GlassesSession(
            transport = BluetoothLeTransport(context, band.address, defaultPsm = Band.PSM),
            trust = IdentityTrustExchange(BundleCredentials(identity)),
        )
        session = opened
        job = scope.launch {
            launch { poll(opened) }
            opened.events.collect { event ->
                when (event) {
                    is LinkStateChanged -> {
                        _state.update { it.copy(link = event.state) }
                        if (event.state == LinkState.READY) {
                            log.add("Band: link up")
                            ask(opened)
                        }
                    }
                    is ServiceData -> if (event.serviceId == ServiceId.WIRELESS_INPUT.value) onBand(event)
                    is ProtocolError -> log.add("Band: ${event.detail}")
                    else -> Unit
                }
            }
        }
        log.add("Band: connecting to ${band.serial}")
        opened.start(scope)
    }

    // The band pushes battery changes too; a slow read keeps the reading fresh when it does not.
    private suspend fun poll(opened: GlassesSession) {
        while (true) {
            delay(POLL_MS)
            if (_state.value.link == LinkState.READY && session === opened) ask(opened)
        }
    }

    private fun ask(opened: GlassesSession) = opened.submit(
        SessionCommand { s ->
            val mux = s.connection ?: return@SessionCommand
            mux.sendOnService(ServiceId.WIRELESS_INPUT.value, Band.REQUEST_TYPE, Band.batteryRequest(seq.getAndIncrement()))
            mux.sendOnService(ServiceId.WIRELESS_INPUT.value, Band.REQUEST_TYPE, Band.configRequest(seq.getAndIncrement()))
        },
    )

    private fun onBand(event: ServiceData) {
        val battery = runCatching {
            when (event.messageType and 0xFF) {
                Band.RESPONSE -> Band.response(event.payload).also { r ->
                    if (r.leftHanded != null) _state.update { it.copy(leftHanded = r.leftHanded) }
                }.battery
                Band.STREAM_UPDATE -> Band.streamBattery(event.payload)
                else -> null
            }
        }.onFailure { log.add("Band: unreadable message type ${event.messageType}: ${it.message}") }.getOrNull() ?: return
        _state.update { it.copy(battery = battery.percent, charging = battery.charging, at = System.currentTimeMillis()) }
    }

    private companion object {
        const val POLL_MS = 5 * 60_000L
    }
}

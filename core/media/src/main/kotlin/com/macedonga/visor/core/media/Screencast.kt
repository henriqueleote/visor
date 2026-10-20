package com.macedonga.visor.core.media

import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.core.session.ServiceData
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

const val SVC_HN_SCREENCAST_MANAGER = 23464
const val SVC_HN_SCREENCAST = 23463

// The MobileConfig switch the glasses check before they run the screencast service.
const val SCREENCAST_FLAG = "hn_demos.enable_hn_screencast"

enum class ScreencastPhase { IDLE, STARTING, LIVE, FAILED }

enum class ScreencastFault { NOT_CONNECTED, NOT_READ, NO_FLAG, FLAG_NOT_SENT, NEEDS_RESTART, NO_WIFI, NO_ANSWER, ASLEEP, GLASSES_TIMEOUT, UNKNOWN }

data class ScreencastState(val phase: ScreencastPhase = ScreencastPhase.IDLE, val fault: ScreencastFault? = null, val detail: String = "")

/** One whole H.264 frame, its parameter sets in band. */
class ScreencastFrame(val timestampMs: Long, val key: Boolean, val data: ByteArray)

/**
 * The Display's own screen, streamed to the phone on svc23463 and driven on svc23464, over Wi-Fi as the Meta app runs
 * it: the manager opened and answered, then the encoder settings and their answer, then the frames opened. The service
 * runs only while the MobileConfig switch is on, and the glasses read that switch when they start.
 */
class Screencast(private val connection: GlassesConnection, private val config: GlassesConfig, private val live: LiveStream) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ScreencastState())
    val state: StateFlow<ScreencastState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<ScreencastFrame>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames: SharedFlow<ScreencastFrame> = _frames.asSharedFlow()

    private var job: Job? = null
    private var answer: CompletableDeferred<Pair<Boolean, Int>>? = null
    private var stopping = CompletableDeferred<Unit>()

    // The frame being assembled: its time, key flag and the chunks still owed.
    private var timestamp = 0L
    private var key = false
    private var chunksLeft = 0
    private var frame = ByteArrayOutputStream()

    init {
        scope.launch { connection.serviceData.collect { onData(it) } }
    }

    /** Switch the glasses' screencast on and wait for their answer; the state says how it went. */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** Stop the screencast on the glasses and forget the state. */
    @Synchronized
    fun stop() {
        // Live, the run sends the stop itself and lets Wi-Fi go; still starting, it is cut short.
        if (_state.value.phase != ScreencastPhase.LIVE) job?.cancel()
        stopping.complete(Unit)
        _state.value = ScreencastState()
    }

    private suspend fun run() {
        fun fail(fault: ScreencastFault, detail: String = "") {
            _state.value = ScreencastState(ScreencastPhase.FAILED, fault, detail)
        }
        _state.value = ScreencastState(ScreencastPhase.STARTING)
        if (connection.state.value.link != LinkState.READY) return fail(ScreencastFault.NOT_CONNECTED)
        val cfg = config.state.value
        if (cfg.params.isEmpty()) return fail(ScreencastFault.NOT_READ)
        if (SCREENCAST_FLAG !in cfg.params) return fail(ScreencastFault.NO_FLAG)
        if (cfg.values[SCREENCAST_FLAG] != true) {
            config.send(mapOf(SCREENCAST_FLAG to true))?.let { return fail(ScreencastFault.FLAG_NOT_SENT, it) }
            return fail(ScreencastFault.NEEDS_RESTART)
        }
        stopping = CompletableDeferred()
        val why = live.overWifi { mux ->
            coroutineScope {
                val listening = launch { live.wifiData.collect { onData(it) } }
                // A service that is not running answers 0xC001: the switch is on but the glasses have not restarted since.
                val absent = launch {
                    live.notes.first { it.contains("service $SVC_HN_SCREENCAST_MANAGER answered status 0xc001") }
                    answer?.complete(Pair(false, NOT_RUNNING))
                }
                try {
                    // The open is not answered; the Meta app goes straight on to the settings, which are.
                    mux.sendOnService(SVC_HN_SCREENCAST_MANAGER, MT_CONTROL, byteArrayOf(0))
                    delay(OPEN_MS)
                    val configured = ask { mux.sendOnService(SVC_HN_SCREENCAST_MANAGER, MT_CONFIGURE, configure()) }
                    if (configured?.first == true) {
                        mux.sendOnService(SVC_HN_SCREENCAST, MT_CONTROL, byteArrayOf(0))
                        _state.value = ScreencastState(ScreencastPhase.LIVE)
                        stopping.await()
                        mux.sendOnService(SVC_HN_SCREENCAST_MANAGER, MT_CONTROL, byteArrayOf(3))
                        delay(STOP_MS)
                    } else refused(configured)
                } finally {
                    listening.cancel()
                    absent.cancel()
                }
            }
            null
        }
        if (why != null) fail(ScreencastFault.NO_WIFI, why)
    }

    /** Send, and wait for the manager's answer: success, and why not. */
    private suspend fun ask(send: suspend () -> Unit): Pair<Boolean, Int>? {
        val reply = CompletableDeferred<Pair<Boolean, Int>>().also { answer = it }
        send()
        return withTimeoutOrNull(ANSWER_MS) { reply.await() }.also { answer = null }
    }

    private fun refused(got: Pair<Boolean, Int>?) {
        _state.value = ScreencastState(
            ScreencastPhase.FAILED,
            when {
                got == null -> ScreencastFault.NO_ANSWER
                got.second == NOT_RUNNING -> ScreencastFault.NEEDS_RESTART
                got.second == 2 -> ScreencastFault.ASLEEP
                got.second == 3 -> ScreencastFault.GLASSES_TIMEOUT
                else -> ScreencastFault.UNKNOWN
            },
        )
    }

    private fun onData(m: ServiceData) {
        when (m.serviceId) {
            SVC_HN_SCREENCAST_MANAGER -> {
                val reply = runCatching { Wire.parse(m.payload) }.getOrNull() ?: return
                answer?.complete(Pair(reply.varint(1) != 0L, reply.varint(2).toInt()))
            }
            SVC_HN_SCREENCAST -> chunk(m.payload)
        }
    }

    // A 32-byte message opens a frame; every other one is the next chunk of the open frame.
    private fun chunk(p: ByteArray) {
        if (p.size == HEADER_BYTES) {
            val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
            timestamp = b.getLong(0)
            key = b.getInt(8) and KEY_FLAG != 0
            chunksLeft = b.getInt(24)
            frame = ByteArrayOutputStream()
            return
        }
        if (chunksLeft <= 0) return
        frame.write(p)
        if (--chunksLeft == 0) _frames.tryEmit(ScreencastFrame(timestamp, key, frame.toByteArray()))
    }

    // Five little-endian int32s: bitrate, I-frame interval, 2^profile, 2^level, framerate. These are the Meta app's defaults.
    private fun configure(): ByteArray = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(100_000).putInt(1_000_000).putInt(1).putInt(1).putInt(5)
        .array()

    private companion object {
        const val MT_CONTROL = 129
        const val MT_CONFIGURE = 1
        const val HEADER_BYTES = 32
        const val KEY_FLAG = 1
        const val ANSWER_MS = 15_000L
        const val STOP_MS = 300L
        const val OPEN_MS = 500L
        const val NOT_RUNNING = -1
    }
}

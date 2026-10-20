package com.macedonga.visor.media

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macedonga.visor.R
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.media.BroadcastCodec
import com.macedonga.visor.core.media.BroadcastTarget
import com.macedonga.visor.core.media.LADDER
import com.macedonga.visor.core.media.LiveOverlay
import com.macedonga.visor.core.media.LiveSettings
import com.macedonga.visor.core.media.LiveSettingsStore
import com.macedonga.visor.core.media.LiveStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * One thing worth saying over the picture for a moment. [uri] is something saved that can be opened; [glassesHost]
 * offers to move the stream onto the glasses' own network.
 */
data class Notice(
    @StringRes val text: Int,
    val arg: String? = null,
    val uri: Uri? = null,
    val warning: Boolean = false,
    val glassesHost: Boolean = false,
    val at: Long = System.currentTimeMillis(),
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    val live: LiveStream,
    val connection: GlassesConnection,
    val settings: LiveSettingsStore,
    val assistant: com.macedonga.visor.core.assistant.Assistant,
) : ViewModel() {
    val outputs = live.outputs

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    fun start() {
        val s = settings.settings.value
        outputs.replay.windowUs = s.replaySeconds * 1_000_000L
        live.start(s)
    }

    fun stop() = live.stop()

    /** Whether the Live has a reason to go on with nobody watching: something is being made of it. */
    fun keepsGoing(): Boolean = outputs.recording.value != null || outputs.broadcast.running

    fun say(@StringRes text: Int, arg: String? = null, uri: Uri? = null, warning: Boolean = false, glassesHost: Boolean = false) {
        _notice.value = Notice(text, arg, uri, warning, glassesHost)
    }

    /** From now on the glasses host the Wi-Fi, and the stream running now moves there. */
    fun useGlassesHost() {
        settings.update { it.copy(glassesHost = true) }
        live.useGlassesHost()
        _notice.value = null
    }

    fun dismiss(notice: Notice) {
        if (_notice.value == notice) _notice.value = null
    }

    fun toggleRecording() {
        viewModelScope.launch {
            if (outputs.recording.value != null) {
                outputs.stopRecording()?.let { say(R.string.live_saved_video, uri = it) }
            } else if (!outputs.startRecording()) {
                say(R.string.live_nothing_yet)
            }
        }
    }

    fun saveReplay() {
        viewModelScope.launch {
            val held = outputs.replay.heldUs
            val uri = outputs.saveReplay()
            if (uri == null) say(R.string.live_nothing_yet) else say(R.string.live_saved_replay, spanWords(held / 1_000_000), uri)
        }
    }

    /** The picture on screen saved as a photo; from the replay ring when nothing is on screen. */
    fun snapshot(video: LiveVideo?) {
        val now = System.currentTimeMillis()
        val save = { jpeg: ByteArray? ->
            viewModelScope.launch {
                val uri = if (jpeg != null) outputs.savePhoto(jpeg, now) else outputs.saveFrame()
                if (uri == null) say(R.string.live_nothing_yet) else say(R.string.live_saved_photo, uri = uri)
            }
            Unit
        }
        if (video != null) video.grab(save) else save(null)
    }

    /** A real photo from the glasses' camera, at their capture size, while the stream goes on. */
    fun glassesPhoto() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val heic = live.capturePhoto()
            val uri = heic?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { outputs.savePhoto(it, now, heic = true) } }
            if (uri == null) say(R.string.live_photo_failed, warning = true) else say(R.string.live_saved_glasses_photo, uri = uri)
        }
    }

    fun startBroadcast() {
        val s = settings.settings.value
        val config = outputs.config ?: return say(R.string.live_nothing_yet)
        val rung = live.rung.value ?: LADDER.first()
        // H.264 needs about half as much again for the same picture.
        val bitrate = if (s.rtmpCodec == BroadcastCodec.H264) minOf(6_000_000, rung.bitrate * 3 / 2) else rung.bitrate
        outputs.broadcast.start(BroadcastTarget(s.rtmpUrl, s.rtmpKey, s.rtmpCodec), config, rung.frameRate, bitrate) { outputs.aac }
    }

    fun stopBroadcast() = outputs.broadcast.stop()

    fun update(change: (LiveSettings) -> LiveSettings) = settings.update(change)

    fun setOverlay(overlay: LiveOverlay, on: Boolean) = settings.update { it.copy(overlays = if (on) it.overlays + overlay else it.overlays - overlay) }

    /** Notices go after a few seconds, warnings after longer, and one that offers a switch after longer still. */
    init {
        viewModelScope.launch {
            notice.collectLatest { n ->
                if (n == null) return@collectLatest
                delay(if (n.glassesHost) 12_000 else if (n.warning) 6_000 else 3_500)
                dismiss(n)
            }
        }
        // A stream on the phone's group that keeps stopping: the glasses' own network may hold.
        viewModelScope.launch {
            live.suggestGlassesHost.collect { if (it) say(R.string.live_drops, warning = true, glassesHost = true) }
        }
    }
}

/** "2 min", "47 s": how long a span of the Live is. */
fun spanWords(seconds: Long): String = if (seconds >= 60 && seconds % 60 == 0L) "${seconds / 60} min" else if (seconds >= 60) "${seconds / 60}:${"%02d".format(seconds % 60)}" else "$seconds s"

/** Elapsed time as 0:42, 12:05 or 1:02:09. */
fun clockWords(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

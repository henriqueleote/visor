package com.macedonga.visor.metaai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.macedonga.visor.core.connection.GlassesConnection
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A greeting when the glasses go back on after a while off ("Welcome back boss"). Spoken by the phone's
 * text-to-speech, which plays through the glasses like any Bluetooth audio: the glasses' own voice only answers a
 * question they asked for, and a chat message sent to them only gets their notification sound.
 */
class Greeting(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("greeting", Context.MODE_PRIVATE)

    var on: Boolean
        get() = prefs.getBoolean(ON, false)
        set(v) = prefs.edit().putBoolean(ON, v).apply()
    var text: String
        get() = prefs.getString(TEXT, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TEXT
        set(v) = prefs.edit().putString(TEXT, v.trim()).apply()
    /** How long the glasses must have been off for the greeting. */
    var minutes: Int
        get() = prefs.getInt(MINUTES, 30)
        set(v) = prefs.edit().putInt(MINUTES, v).apply()

    /** Watches the glasses come off and go back on, for as long as the connection runs. */
    fun keep(scope: CoroutineScope, connection: GlassesConnection) {
        scope.launch {
            var worn: Boolean? = null
            connection.state.map { it.device?.worn }.distinctUntilChanged().collect { now ->
                val was = worn
                worn = now
                // Off, out of reach or in the case: the time counts from here.
                if (was == true && now != true) prefs.edit().putLong(LAST_OFF, System.currentTimeMillis()).apply()
                if (now == true && was != true && on) {
                    val off = prefs.getLong(LAST_OFF, 0L)
                    // After the glasses' own power-on sound, which would cover it.
                    if (off == 0L || System.currentTimeMillis() - off >= minutes * 60_000L) scope.launch { delay(GREET_DELAY_MS); speak(text) }
                }
            }
        }
    }

    /** [line] through the glasses' speakers, with the music ducked for it. */
    suspend fun speak(line: String) = withContext(Dispatchers.Main) {
        val audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        var tts: TextToSpeech? = null
        try {
            val ready = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { done -> tts = TextToSpeech(context) { status -> if (done.isActive) done.resume(status == TextToSpeech.SUCCESS) } }
            } == true
            val engine = tts ?: return@withContext
            if (!ready) return@withContext
            engine.setAudioAttributes(attributes)
            audio.requestAudioFocus(focus)
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { done ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { if (done.isActive) done.resume(Unit) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) { if (done.isActive) done.resume(Unit) }
                    })
                    engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, "greeting")
                }
            }
        } finally {
            audio.abandonAudioFocusRequest(focus)
            tts?.shutdown()
        }
    }

    companion object {
        const val DEFAULT_TEXT = "Welcome back boss"
        val MINUTE_CHOICES = listOf(15, 30, 60, 120)
        private const val ON = "on"
        private const val TEXT = "text"
        private const val MINUTES = "minutes"
        private const val GREET_DELAY_MS = 3_000L
        private const val LAST_OFF = "last_off"
    }
}

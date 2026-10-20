package com.macedonga.visor.connection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.Responder
import com.macedonga.visor.log.EventLog
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.DeflaterOutputStream
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * svc9000, the data fetcher: the Display's apps (Instagram, messaging, the prompter) ask the phone for a URL and get
 * its bytes back on the same channel, a ResponseData {1 data, 2 compressed, 3 total} per chunk, then an OK status.
 * Plain http(s) streams; with an image transformation the whole image is scaled first. Other schemes are refused.
 */
@Singleton
class DataFetcher @Inject constructor(private val log: Provider<EventLog>, private val requests: RequestLog) : Responder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Int, Job>()
    private val slots = Semaphore(PARALLEL)

    override suspend fun respond(message: DataXMessage, connection: DataXConnection) {
        val channel = message.info.channelId
        if (message.info.flags.isClosed) {
            running.remove(channel)?.cancel()
            if (message.payload.isEmpty()) return
        }
        if (message.info.messageType != MT_REQUEST) return
        val query = runCatching { Query.decode(message.payload) }.getOrNull() ?: return
        val reply = DataXConnection.mirror(channel)
        // Off the reader: a download must not hold up everything else on the link.
        running[channel] = scope.launch {
            try {
                slots.withPermit { serve(query, reply, connection) }
            } finally {
                running.remove(channel)
            }
        }
    }

    private suspend fun serve(q: Query, reply: Int, mux: DataXConnection) {
        val id = requests.start(q.url, q.source, q.options())
        var sent = 0L
        val scheme = q.url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") {
            requests.finish(id, "refused, no $scheme here", failed = true)
            mux.sendStatus(reply, UNKNOWN_TYPE)
            return
        }
        val chunk = (if (q.chunkSize > 0) q.chunkSize else DEFAULT_CHUNK).coerceAtMost(MAX_CHUNK)
        val c = runCatching { open(q.url) }.getOrElse {
            log.get().add("Fetch: ${host(q.url)} failed: ${it.message}")
            requests.finish(id, "failed: ${it.message}", failed = true)
            mux.sendStatus(reply, INTERNAL_ERROR)
            return
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                log.get().add("Fetch: ${host(q.url)} answered $code")
                requests.finish(id, "HTTP $code", failed = true)
                mux.sendStatus(reply, INTERNAL_ERROR)
                return
            }
            if (q.scale == null) {
                sent = stream(c.inputStream, c.contentLengthLong, chunk, reply, mux)
            } else {
                var body = c.inputStream.use { it.readBytes() }
                body = scaled(body, q.scale) ?: body
                val compressed = q.compress
                if (compressed) body = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(body) } }.toByteArray()
                for (at in body.indices step chunk) {
                    send(mux, reply, body.copyOfRange(at, minOf(body.size, at + chunk)), body.size.toLong(), compressed)
                }
                sent = body.size.toLong()
            }
            mux.sendStatus(reply, OK)
            requests.finish(id, "HTTP $code" + if (q.scale != null) ", scaled" else "", failed = false, bytes = sent)
        } catch (error: kotlinx.coroutines.CancellationException) {
            requests.finish(id, "cancelled", failed = false, bytes = sent)
            throw error
        } catch (error: Exception) {
            log.get().add("Fetch: ${host(q.url)} broke off: ${error.message}")
            requests.finish(id, "broke off: ${error.message}", failed = true, bytes = sent)
            runCatching { mux.sendStatus(reply, INTERNAL_ERROR) }
        } finally {
            c.disconnect()
        }
    }

    /** The body as it comes, a chunk per message; how much went. */
    private suspend fun stream(input: InputStream, total: Long, chunk: Int, reply: Int, mux: DataXConnection): Long {
        var sent = 0L
        input.use {
            val buffer = ByteArray(chunk)
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val n = it.read(buffer)
                if (n < 0) break
                if (n > 0) {
                    send(mux, reply, buffer.copyOf(n), total, false)
                    sent += n
                }
            }
        }
        return sent
    }

    private suspend fun send(mux: DataXConnection, reply: Int, data: ByteArray, total: Long, compressed: Boolean) {
        val body = Wire.fieldBytes(1, data) + Wire.fieldVarint(2, if (compressed) 1 else 0) + Wire.fieldVarint(3, total.coerceIn(-1, Int.MAX_VALUE.toLong()))
        mux.send(reply, body, messageType = MT_REQUEST)
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
    }

    /** The image fitted to the asked size and format; null to send it as it came, also when that is no bigger. */
    private fun scaled(image: ByteArray, s: Scale): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth <= 0 || s.width <= 0 || s.height <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= s.width && bounds.outHeight / (sample * 2) >= s.height) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val (w, h) = if (s.keepAspect) {
            val k = minOf(s.width.toFloat() / decoded.width, s.height.toFloat() / decoded.height, 1f)
            max(1, (decoded.width * k).roundToInt()) to max(1, (decoded.height * k).roundToInt())
        } else s.width to s.height
        val fitted = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
        val format = when (s.format) {
            FORMAT_PNG -> Bitmap.CompressFormat.PNG
            FORMAT_WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        val out = ByteArrayOutputStream().also { fitted.compress(format, if (s.quality in 1..100) s.quality else DEFAULT_QUALITY, it) }.toByteArray()
        return out.takeIf { it.size < image.size }
    }

    private fun host(url: String) = runCatching { URL(url).host }.getOrDefault("?")

    private class Scale(val width: Int, val height: Int, val format: Int, val keepAspect: Boolean, val quality: Int)

    /** RequestQuery: 1 url, 2 compress, 3 transformations (1 image scale), 4 chunk size, 6 who asked. */
    private class Query(val url: String, val compress: Boolean, val scale: Scale?, val chunkSize: Int, val source: Int) {
        fun options(): String = listOfNotNull(
            scale?.let { s -> "${s.width}x${s.height} ${listOf("jpeg", "png", "webp").getOrElse(s.format) { "format ${s.format}" }}" },
            "zlib".takeIf { compress },
            "chunk $chunkSize".takeIf { chunkSize > 0 },
        ).joinToString(", ")

        companion object {
            fun decode(payload: ByteArray): Query {
                val f = Wire.parse(payload)
                val scale = f.repeatedBytes(3).firstNotNullOfOrNull { t ->
                    Wire.parse(t).takeIf { it.has(1) }?.let { Wire.parse(it.bytes(1)) }?.let {
                        Scale(it.varint(1).toInt(), it.varint(2).toInt(), it.varint(3).toInt(), it.varint(5) != 0L, it.varint(6).toInt())
                    }
                }
                return Query(f.bytes(1).decodeToString(), f.varint(2) != 0L, scale, f.varint(4).toInt(), f.varint(6).toInt())
            }
        }
    }

    companion object {
        const val SVC = 9000
        private const val MT_REQUEST = 0
        private const val OK = 0
        private const val INTERNAL_ERROR = 0xC004
        private const val UNKNOWN_TYPE = 0xD001
        private const val DEFAULT_CHUNK = 1024
        // A DataX message holds at most 16383 bytes, header included.
        private const val MAX_CHUNK = 16_000
        private const val FORMAT_PNG = 1
        private const val FORMAT_WEBP = 2
        private const val DEFAULT_QUALITY = 85
        private const val PARALLEL = 4
        private const val TIMEOUT_MS = 20_000
    }
}

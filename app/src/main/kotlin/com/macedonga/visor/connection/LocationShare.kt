package com.macedonga.visor.connection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.Responder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * svc41102, locationservice: the Display asks for the phone's location (type 3) and gets fixes (type 4,
 * LocationResult {1 status, 2 Location}) until it says stop (type 5). Its register message on connect (26) needs no
 * answer. Without the location permission the answer says so.
 */
@Singleton
class LocationShare @Inject constructor(
    @ApplicationContext private val context: Context,
    private val requests: RequestLog,
) : Responder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val listening = ConcurrentHashMap<Int, Subscription>()

    override suspend fun respond(message: DataXMessage, connection: DataXConnection) {
        val channel = message.info.channelId
        if (message.info.flags.isClosed) {
            listening.remove(channel)?.stop()
            return
        }
        when (message.info.messageType) {
            MT_REQUEST -> if (!listening.containsKey(channel)) {
                val precise = runCatching { Wire.parse(message.payload).varint(3) }.getOrDefault(0L) == ACCURACY_BEST
                listening[channel] = Subscription(connection, DataXConnection.mirror(channel), precise).also { it.start() }
            }
            MT_STOP -> listening.remove(channel)?.stop()
        }
    }

    private inner class Subscription(private val mux: DataXConnection, private val reply: Int, private val precise: Boolean) : LocationListener {
        private val manager = context.getSystemService(LocationManager::class.java)
        private val logId = requests.start("location://${if (precise) "precise" else "approximate"}", SOURCE_LOCATION)
        private var fixes = 0L

        @SuppressLint("MissingPermission")
        fun start() {
            val permission = if (precise) Manifest.permission.ACCESS_FINE_LOCATION else Manifest.permission.ACCESS_COARSE_LOCATION
            if (manager == null || context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                send(Wire.fieldVarint(1, STATUS_NO_PERMISSION))
                requests.finish(logId, "no location permission", failed = true)
                return
            }
            val provider = when {
                manager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }
            manager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
            manager.requestLocationUpdates(provider, INTERVAL_MS, 0f, this, Looper.getMainLooper())
        }

        fun stop() {
            manager?.removeUpdates(this)
            requests.finish(logId, "stopped after $fixes fixes", failed = false)
        }

        override fun onLocationChanged(location: Location) {
            fixes++
            send(Wire.fieldVarint(1, STATUS_OK) + Wire.fieldBytes(2, encode(location)))
        }

        private fun send(body: ByteArray) {
            scope.launch { runCatching { mux.send(reply, body, messageType = MT_RESULT) } }
        }
    }

    /** Location: 1 latitude, 2 longitude, 3 accuracy m, 5 time ms, 8 altitude, 9 its accuracy, 10 bearing, 11 its accuracy, 12 speed, 13 its accuracy. */
    private fun encode(l: Location): ByteArray {
        var b = Wire.fieldDouble(1, l.latitude) + Wire.fieldDouble(2, l.longitude) + Wire.fieldFloat(3, l.accuracy) + Wire.fieldVarint(5, l.time)
        if (l.hasAltitude()) b += Wire.fieldDouble(8, l.altitude)
        if (l.hasVerticalAccuracy()) b += Wire.fieldFloat(9, l.verticalAccuracyMeters)
        if (l.hasBearing()) b += Wire.fieldFloat(10, l.bearing)
        if (l.hasBearingAccuracy()) b += Wire.fieldFloat(11, l.bearingAccuracyDegrees)
        if (l.hasSpeed()) b += Wire.fieldFloat(12, l.speed)
        if (l.hasSpeedAccuracy()) b += Wire.fieldFloat(13, l.speedAccuracyMetersPerSecond)
        return b
    }

    companion object {
        const val SVC = 41102
        private const val MT_REQUEST = 3
        private const val MT_RESULT = 4
        private const val MT_STOP = 5
        private const val ACCURACY_BEST = 0L
        private const val STATUS_OK = 0L
        private const val STATUS_NO_PERMISSION = 1L
        private const val INTERVAL_MS = 2_000L
        private const val SOURCE_LOCATION = -3
    }
}

package com.macedonga.visor.gallery

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.media.Place
import com.macedonga.visor.core.media.PlaceLog
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the phone may say where it is, now and from the background, where the glasses' captures happen. */
fun placesAllowed(context: Context): Boolean = context.allows(Manifest.permission.ACCESS_FINE_LOCATION) &&
    (Build.VERSION.SDK_INT < 29 || context.allows(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

fun Context.allows(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/**
 * Notes where the phone is each time the glasses start a photo or a video, so the import can put the place on
 * it later. Runs in the glasses' service, for as long as the connection does.
 */
fun notePlaces(context: Context, scope: CoroutineScope, connection: GlassesConnection, log: PlaceLog) {
    scope.launch {
        connection.state.map { it.device?.camera }.distinctUntilChanged().collect { camera ->
            if (camera != "CAPTURE_PHOTO_STARTED" && camera != "CAPTURE_VIDEO_STARTED") return@collect
            val at = System.currentTimeMillis()
            here(context)?.let { log.record(Place(at, it.latitude, it.longitude, it.accuracy)) }
        }
    }
}

/** The phone's position: the last known one if it is fresh, otherwise a new fix, within [timeoutMs]. */
@SuppressLint("MissingPermission")
internal suspend fun here(context: Context, timeoutMs: Long = FIX_TIMEOUT_MS): Location? {
    if (!context.allows(Manifest.permission.ACCESS_FINE_LOCATION) && !context.allows(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val provider = when {
        Build.VERSION.SDK_INT >= 31 && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> LocationManager.NETWORK_PROVIDER
    }
    val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    if (last != null && System.currentTimeMillis() - last.time < FRESH_MS && last.accuracy < 100f) return last
    if (Build.VERSION.SDK_INT < 30) return last
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { done ->
            val cancel = CancellationSignal()
            done.invokeOnCancellation { cancel.cancel() }
            runCatching {
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> if (done.isActive) done.resume(location) }
            }.onFailure { if (done.isActive) done.resume(null) }
        }
    } ?: last
}

/** Place names from the platform's geocoder, kept for as long as the app runs. */
object PlaceNames {
    private val names = ConcurrentHashMap<Long, String>()

    private fun key(lat: Double, lon: Double) = ((lat * 1000).toLong() shl 32) xor (lon * 1000).toLong()

    fun cached(lat: Double, lon: Double): String? = names[key(lat, lon)]

    /** The town, or the nearest named thing to it. */
    suspend fun of(context: Context, lat: Double, lon: Double): String? {
        cached(lat, lon)?.let { return it }
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine { done ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) { if (done.isActive) done.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (done.isActive) done.resume(null) }
                    })
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) { runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() }
        }
        val name = address?.run { locality ?: subAdminArea ?: adminArea ?: featureName } ?: return null
        names[key(lat, lon)] = name
        return name
    }
}

private const val FRESH_MS = 60_000L
private const val FIX_TIMEOUT_MS = 20_000L

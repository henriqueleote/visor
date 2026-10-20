package com.macedonga.visor.connection

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import androidx.annotation.RequiresApi
import com.macedonga.visor.core.connection.GlassesLink
import com.macedonga.visor.core.connection.GlassesTarget
import com.macedonga.visor.core.identity.IdentityStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Bound by the system when the associated glasses appear, so the connection starts even with Visor in the background.
 * Nothing is done when they disappear: the reconnect ladder handles that.
 */
@AndroidEntryPoint
class GlassesPresence : CompanionDeviceService() {

    @Inject lateinit var store: IdentityStore

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event != DevicePresenceEvent.EVENT_BLE_APPEARED && event.event != DevicePresenceEvent.EVENT_BT_CONNECTED) return
        val address = getSystemService(CompanionDeviceManager::class.java).myAssociations
            .firstOrNull { it.id == event.associationId }?.deviceMacAddress?.toString()
        appeared(address)
    }

    @RequiresApi(33)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = appeared(associationInfo.deviceMacAddress?.toString())

    // Before Android 13, the association is known by its address.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) = appeared(address)

    /** Starts the service for the stored pair at [address], and for no other glasses. */
    private fun appeared(address: String?) {
        if (address == null) return
        val bundle = runBlocking(Dispatchers.IO) { store.all() }
            .firstOrNull { it.glassesAddress.equals(address, ignoreCase = true) } ?: return
        GlassesService.start(this, GlassesTarget(GlassesLink.Bluetooth(bundle.glassesAddress), bundle.serial))
    }
}

package com.macedonga.visor.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.Responder
import com.macedonga.visor.log.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The glasses' svc46 requests: an app's icon for a notification (type 9), and their acks (types 2 and 1027). */
class NotifyIcons @Inject constructor(
    @ApplicationContext private val context: Context,
    // A Provider, because EventLog needs the connection this responder is registered on.
    private val log: Provider<EventLog>,
) : Responder {

    override suspend fun respond(message: DataXMessage, connection: DataXConnection) {
        when (message.info.messageType) {
            MT_ICON_REQUEST -> {
                val pkg = runCatching { String(Wire.parse(message.payload).bytes(1)) }.getOrDefault("")
                val jpeg = withContext(Dispatchers.Default) { render(pkg) }
                val answer = if (jpeg != null) {
                    Wire.fieldVarint(2, 1) + Wire.fieldBytes(3, jpeg)
                } else {
                    Wire.fieldVarint(2, 0) + Wire.fieldBytes(4, "unknown package".toByteArray())
                }
                connection.send(DataXConnection.mirror(message.info.channelId), Wire.fieldBytes(1, pkg.toByteArray()) + answer, messageType = MT_ICON_RESPONSE)
            }
            MT_RESPONSE, MT_RESPONSE_ACK -> log.get().add("Glasses answered a notification (type ${message.info.messageType})")
        }
    }

    /** The app's icon as a 78 x 78 JPEG, or null when the package has none. */
    private fun render(pkg: String): ByteArray? = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON, ICON)
        d.draw(Canvas(bmp))
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }.getOrNull()

    private companion object {
        const val MT_RESPONSE = 2
        const val MT_ICON_REQUEST = 9
        const val MT_ICON_RESPONSE = 10
        const val MT_RESPONSE_ACK = 1027
        const val ICON = 78
    }
}

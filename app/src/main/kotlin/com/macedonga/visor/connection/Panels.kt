package com.macedonga.visor.connection

import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.Responder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * svc9002, panels: the Display asks which panels the phone offers for its home (type 1) and waits for the list (type 2),
 * timing out after 10 s without it. The Meta app's one panel is a Briefing drawn from Meta's server; Visor offers none,
 * so the Display stops waiting.
 */
@Singleton
class Panels @Inject constructor() : Responder {
    override suspend fun respond(message: DataXMessage, connection: DataXConnection) {
        val reply = DataXConnection.mirror(message.info.channelId)
        when (message.info.messageType) {
            MT_GET_ENABLED -> connection.send(reply, ByteArray(0), messageType = MT_ENABLED)
            else -> if (!message.info.flags.isClosed) connection.sendStatus(reply, BAD_TYPE)
        }
    }

    companion object {
        const val SVC = 9002
        private const val MT_GET_ENABLED = 1
        private const val MT_ENABLED = 2
        private const val BAD_TYPE = 0xD004
    }
}

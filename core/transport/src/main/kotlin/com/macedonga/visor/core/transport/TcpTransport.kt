package com.macedonga.visor.core.transport

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The glasses' own socket on Wi-Fi. It carries raw SDUs, framed exactly as on RFCOMM. */
class TcpTransport(
    private val host: String,
    private val port: Int,
    private val meter: WireLog? = null,
    // A network other than the phone's default, such as the glasses' own, hands out its own sockets.
    private val sockets: javax.net.SocketFactory? = null,
) : Transport {

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var stream: SduStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val opening = sockets?.createSocket() ?: Socket()
        try {
            opening.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            opening.tcpNoDelay = true
        } catch (error: IOException) {
            opening.close()
            throw TransportConnectException(ConnectFailure.NO_ANSWER, "nothing answered on $host:$port (${error.message})", error)
        }
        socket = opening
        stream = SduStream(BufferedInputStream(opening.getInputStream(), 1 shl 16), opening.getOutputStream(), meter)
    }

    override suspend fun send(frame: ByteArray) = withContext(Dispatchers.IO) { open().send(frame) }

    override suspend fun receive(): ByteArray = withContext(Dispatchers.IO) { open().receive() }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        stream = null
    }

    private fun open(): SduStream = stream ?: throw TransportClosedException("not connected")

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
    }
}

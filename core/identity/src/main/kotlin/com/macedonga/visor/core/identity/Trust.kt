package com.macedonga.visor.core.identity

import com.macedonga.visor.core.protocol.HandshakeBinding
import com.macedonga.visor.core.protocol.IdentityMsgType
import com.macedonga.visor.core.protocol.IdentityStatus
import com.macedonga.visor.core.protocol.RESIGN_PROVISIONING_CAPABILITIES
import com.macedonga.visor.core.protocol.ServiceId
import com.macedonga.visor.core.protocol.Wire
import com.macedonga.visor.core.session.DataXConnection
import com.macedonga.visor.core.session.DataXMessage
import com.macedonga.visor.core.session.TrustExchange
import com.macedonga.visor.core.session.TrustResult
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Signature
import kotlinx.coroutines.withTimeoutOrNull

/** The client proves it holds the owner key the glasses already know. */

/** The secp256r1 group order. */
private val SECP256R1_ORDER = BigInteger(
    "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16,
)

private val HALF_ORDER: BigInteger = SECP256R1_ORDER.shiftRight(1)

/** The trust credential the glasses push at us, by message type. */
private val TRUST_PUSH_TYPES = setOf(
    IdentityMsgType.ENABLE_TRUST_EC.value,
    IdentityMsgType.ENABLE_TRUST.value,
)

/** `run_trust_exchange`'s `timeout: float = 5.0`. */
const val TRUST_TIMEOUT_MILLIS: Long = 5_000

/** ENABLE_TRUST, message type 0x1000. */
fun buildEnableTrust(credentials: Credentials, txChallenge: ByteArray): ByteArray {
    require(txChallenge.size == 32) {
        "the channel binding is a SHA-256 digest and must be 32 bytes, got ${txChallenge.size}"
    }
    val signer = Signature.getInstance("NONEwithECDSA")
    signer.initSign(credentials.appEcKey)
    signer.update(txChallenge)
    return enableTrustPayload(credentials.appPublicKey, signer.sign())
}

/** The message body, given a DER signature. */
internal fun enableTrustPayload(publicKey64: ByteArray, derSignature: ByteArray): ByteArray {
    val (r, s) = decodeDerSignature(derSignature)
    val canonical = if (s > HALF_ORDER) SECP256R1_ORDER.subtract(s) else s
    val raw = fixedWidth(r) + fixedWidth(canonical)
    return Wire.fieldBytes(1, MessageDigest.getInstance("SHA-256").digest(publicKey64)) +
        Wire.fieldBytes(2, raw) +
        Wire.fieldVarint(3, RESIGN_PROVISIONING_CAPABILITIES.toLong())
}

/** `utils.decode_dss_signature`: a SEQUENCE of two INTEGERs. */
private fun decodeDerSignature(der: ByteArray): Pair<BigInteger, BigInteger> = try {
    val sequence = DerReader(DerReader(der).read(DerReader.SEQUENCE))
    sequence.readInteger() to sequence.readInteger()
} catch (error: DerException) {
    throw CredentialsException("the signature is not in the expected format", error)
}

/** 32 bytes, big endian, left padded. */
private fun fixedWidth(value: BigInteger): ByteArray {
    val bytes = value.toByteArray()
    return when {
        bytes.size == 32 -> bytes
        bytes.size == 33 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, 33)
        bytes.size < 32 -> ByteArray(32 - bytes.size) + bytes
        else -> throw CredentialsException("the signature does not fit a P-256 coordinate")
    }
}

/** The svc36 exchange, filling `:core:session`'s seam. */
class IdentityTrustExchange internal constructor(
    private val timeoutMillis: Long,
    // A seam for the golden frame tests, and the reason is the same one the vector generator has:
    // ECDSA is randomised, so a freshly signed body is different bytes every run and cannot be
    // compared to a recorded frame.
    private val enableTrust: (txChallenge: ByteArray) -> ByteArray,
) : TrustExchange {

    constructor(
        credentials: Credentials,
        timeoutMillis: Long = TRUST_TIMEOUT_MILLIS,
    ) : this(timeoutMillis, { challenge -> buildEnableTrust(credentials, challenge) })

    override suspend fun run(
        connection: DataXConnection,
        binding: HandshakeBinding,
    ): TrustResult {
        require(connection.hasReader) {
            "the trust exchange bounds its wait, and a bounded wait needs the session's " +
                "reader. Without one, giving up would abandon a read that carries on."
        }

        val ourChannel = connection.openChannel(ServiceId.IDENTITY.value)
        connection.send(
            ourChannel,
            enableTrust(binding.primary()),
            messageType = IdentityMsgType.ENABLE_TRUST.value,
            serviceId = ServiceId.IDENTITY.value,
        )
        val ourStatusChannel = DataXConnection.mirror(ourChannel)

        var txOk = false
        var rxOk = false
        var refusal: Int? = null
        // Anything this loop does not consume belongs to the caller.
        val deferred = mutableListOf<DataXMessage>()
        try {
            withTimeoutOrNull(timeoutMillis) {
                while (!(txOk && rxOk)) {
                    val message = connection.receive()
                    val info = message.info
                    when {
                        info.flags.hasError && info.channelId == ourStatusChannel -> {
                            // Consumed either way, as the reference's if/elif chain does.
                            if (info.errorId == IdentityStatus.SUCCESS.value) txOk = true else refusal = info.errorId
                        }

                        (info.channelId and 0x8000) != 0 &&
                            info.serviceId == ServiceId.IDENTITY.value &&
                            info.messageType in TRUST_PUSH_TYPES -> {
                            connection.sendStatus(
                                DataXConnection.mirror(info.channelId),
                                IdentityStatus.SUCCESS.value,
                            )
                            rxOk = true
                        }

                        else -> deferred += message
                    }
                }
            }
        } finally {
            connection.pushback(deferred)
        }

        return TrustResult(txOk = txOk, rxOk = rxOk, refusal = refusal)
    }
}

// DIVERGENCE: the reference tests its 5.0 second deadline only BETWEEN reads, and its socket
// timeout is 10.0 seconds, so a peer that goes silent costs up to 10 seconds there and 5 here.

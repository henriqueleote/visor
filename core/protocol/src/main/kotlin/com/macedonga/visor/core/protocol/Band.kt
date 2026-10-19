package com.macedonga.visor.core.protocol

/**
 * The Neural Band's RPC on svc52822, as Meta AI 294.1 speaks it (com.oculus.wearableinputservice). A request goes out
 * as DataX type 0x0314, whose low byte is the band's REQUEST; what the band sends carries its own type in the low byte.
 */
object Band {
    const val REQUEST_TYPE = 0x0314
    const val RESPONSE = 21
    const val STREAM_UPDATE = 22

    /** The PSM the band's GATT names; the glasses' is 128. */
    const val PSM = 129

    class Battery(val percent: Int, val charging: Boolean)

    /** RpcResponse {1: seq, 2: code, 3: BatteryInfoResp, 6: ConfigResp, ...}, as far as Visor reads it. */
    class Response(val seq: Int, val code: Int, val battery: Battery?, val leftHanded: Boolean?)

    /** RpcRequest {1: seq, 2: BatteryInfoReq {}}. */
    fun batteryRequest(seq: Int): ByteArray = request(seq, 2, ByteArray(0))

    /** RpcRequest {1: seq, 5: ConfigReq}: with [leftHanded] it sets the wrist (field 10), empty it reads the config. */
    fun configRequest(seq: Int, leftHanded: Boolean? = null): ByteArray =
        request(seq, 5, leftHanded?.let { Wire.fieldVarint(10, if (it) 1 else 0) } ?: ByteArray(0))

    fun response(payload: ByteArray): Response {
        val f = Wire.parse(payload)
        val battery = if (f.has(3)) battery(Wire.parse(f.bytes(3)).bytes(1)) else null
        val left = if (f.has(6)) Wire.parse(f.bytes(6)).let { c -> if (c.has(10)) c.varint(10) != 0L else null } else null
        return Response(f.varint(1).toInt(), f.varint(2).toInt(), battery, left)
    }

    /** RpcStreamUpdate field 2, BatteryUpdate {1: BatteryData}. */
    fun streamBattery(payload: ByteArray): Battery? {
        val f = Wire.parse(payload)
        return if (f.has(2)) battery(Wire.parse(f.bytes(2)).bytes(1)) else null
    }

    // BatteryData {1: level, 2: charging, 3: temperature (float), 4: mV, 5: mA, 6: RTC seconds}.
    private fun battery(data: ByteArray): Battery? {
        val d = Wire.parse(data)
        return if (d.has(1)) Battery(d.varint(1).toInt(), d.varint(2) != 0L) else null
    }

    private fun request(seq: Int, field: Int, body: ByteArray) = Wire.fieldVarint(1, seq.toLong()) + Wire.fieldBytes(field, body)
}

/** svc150's NotifyLease {1: peerKeyHint, 2: leaseType}: the Display taking its band. Type 1 on the channel it opens. */
class NotifyLease(val keyHint: ByteArray, val type: Int) {
    /** A relay lease leaves the band's own phone link up; the others ask the phone to step back. */
    val relay: Boolean get() = type == RELAY

    companion object {
        const val MESSAGE_TYPE = 1
        const val RELAY = 1

        // The phone's answers: Meta's DeviceLeaseService sends Error 0 ("OK") once the lease is held.
        const val OK = 0x0000
        const val UNKNOWN_TYPE = 0xD001
        const val BAD_REQUEST = 0xD010

        fun decode(payload: ByteArray): NotifyLease {
            val f = Wire.parse(payload)
            return NotifyLease(f.bytes(1), f.varint(2).toInt())
        }
    }
}

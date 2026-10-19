package com.macedonga.visor.core.identity

import kotlinx.coroutines.CancellationException

/** Reconnect, prove the key still works, and only then call it verified. */
interface SessionProbe {

    /** Run the svc36 exchange and say whether both directions completed. */
    suspend fun runTrust(): Boolean

    /** Ask svc100, `peer_build_info`, and say whether anything came back. */
    suspend fun readCanary(): Boolean

    /** Whether the glasses sent their own svc36 credential, whatever they made of ours. */
    suspend fun credentialReceived(): Boolean = false
}

class VerificationResult(
    val diagnosis: Diagnosis,
    val trustOk: Boolean,
    val canaryAnswered: Boolean,
    val bundle: IdentityBundle,
)

/** Check [bundle] against the glasses, and return it with its recorded state brought up to date. */
suspend fun verifyBundle(
    bundle: IdentityBundle,
    openSession: suspend () -> SessionProbe,
): VerificationResult {
    val trustOk: Boolean
    val canaryAnswered: Boolean
    val credentialReceived: Boolean
    try {
        val probe = openSession()
        trustOk = probe.runTrust()
        canaryAnswered = probe.readCanary()
        credentialReceived = probe.credentialReceived()
    } catch (cancellation: CancellationException) {
        // The caller gave up, which is not a verdict about anything.
        throw cancellation
    } catch (error: Throwable) {
        // The transport never came up, or died part way. That says nothing about the key,
        // so the recorded state must not move.
        return VerificationResult(Diagnosis.LINK_DOWN, false, false, bundle)
    }

    val verdict = diagnose(trustOk = trustOk, canaryAnswered = canaryAnswered, credentialReceived = credentialReceived)
    return VerificationResult(
        diagnosis = verdict,
        trustOk = trustOk,
        canaryAnswered = canaryAnswered,
        bundle = bundle.withState(stateAfter(bundle.state, verdict)),
    )
}

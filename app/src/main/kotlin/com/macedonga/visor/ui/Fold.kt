package com.macedonga.visor.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.exp

/**
 * How folded a stage is, 0 open to 1 folded. It follows [scrolled] through [range] pixels, but not the finger: the
 * progress is shaped by a curve, read back [FOLD_DELAY_NS] late, and eased in, so the stage trails the content
 * under it and settles after it.
 */
@Composable
fun rememberTrailingFold(range: Float, scrolled: () -> Float): State<Float> {
    // From where the page already is: a page coming back scrolled must not open for a frame and fold again.
    val fold = remember { mutableFloatStateOf(if (range > 0f) FOLD_CURVE.transform((scrolled() / range).coerceIn(0f, 1f)) else 0f) }
    val read = rememberUpdatedState(scrolled)
    LaunchedEffect(range) {
        val history = ArrayDeque<Pair<Long, Float>>()
        var last = 0L
        while (true) {
            val still = animationsOff()
            withFrameNanos { now ->
                history.addLast(now to FOLD_CURVE.transform((read.value() / range).coerceIn(0f, 1f)))
                while (history.size > 1 && history[1].first <= now - FOLD_DELAY_NS) history.removeFirst()
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                // With animations off the stage follows the page as it is, with no trail.
                fold.floatValue = if (still) history.last().second
                else fold.floatValue + (history.first().second - fold.floatValue) * (1f - exp(-dt / FOLD_EASE_S))
            }
        }
    }
    return fold
}

private val FOLD_CURVE = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
private const val FOLD_DELAY_NS = 70_000_000L
private const val FOLD_EASE_S = 0.12f

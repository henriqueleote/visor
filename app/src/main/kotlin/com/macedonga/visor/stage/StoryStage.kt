package com.macedonga.visor.stage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.ui.animationsOff
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * What a guided flow asks of the pair while it runs, as the setup tells its story: where the view turns, whether it
 * drifts round while the glasses work, and sound at a part of the frame, going out (a tone from a temple) or coming in
 * (a voice to the front). Screens set it; [StoryStage] plays it every frame.
 */
class StageCue {
    /** Degrees the view turns from rest. */
    var yaw by mutableFloatStateOf(0f)

    /** A slow sway either side, for while the glasses are at work. */
    var orbit by mutableStateOf(false)

    /** The model part the sound is at: temple_l, temple_r, front; null for none. */
    var source by mutableStateOf<String?>(null)

    /** True for sound coming into the part, false for sound going out of it. */
    var inward by mutableStateOf(false)

    /** How strong the sound is, 0 to 1. */
    var level by mutableFloatStateOf(0f)
}

/**
 * The pair on the stage for a guided flow: centred at [centrePx] on the window at [scale] (both eased on a spring as a
 * step changes them, as the setup's pages do), lit by [look], turned and swayed by [cue], with its sound drawn over it.
 */
@Composable
fun StoryStage(cue: StageCue, look: LightLook, centrePx: Float, scale: Float, stagePx: Float, closed: Boolean = false) {
    val stage = LocalStage.current
    val colors = Visor.colors
    val centre by animateFloatAsState(centrePx, spring(dampingRatio = 0.9f, stiffness = 40f), label = "centre")
    val zoom by animateFloatAsState(scale, spring(dampingRatio = 0.9f, stiffness = 40f), label = "scale")
    StageSpot(closed, liveliness = 0.5f) {
        val h = stagePx * max(zoom, 1f)
        top = centre - h / 2
        height = h
        this.scale = zoom
        pivotX = 0.5f
        pivotY = 0.5f
        shift = (h - stagePx) / 2
        alpha = 1f
    }
    StageLight(look)

    val clock = remember { mutableFloatStateOf(0f) }
    val now by rememberUpdatedState(cue)
    LaunchedEffect(stage) {
        var last = 0L
        val pose = stage.pose
        while (true) {
            val still = animationsOff()
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                last = t
                if (!still) clock.floatValue += dt
                val c = now
                val k = if (still) 1f else 1f - exp(-dt / FOLLOW_SECONDS)
                val sway = if (c.orbit && !still) sin(clock.floatValue * ORBIT_SPEED) * ORBIT_DEGREES else 0f
                pose.viewYaw += (c.yaw + sway - pose.viewYaw) * k
                // The voice is in the light too: each loud moment swells it.
                if (c.inward && c.level > 0.05f) stage.light.pulse(c.level * 0.6f)
                stage.renderer.invalidate()
            }
        }
    }
    DisposableEffect(stage) {
        onDispose { stage.pose.viewYaw = 0f }
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.floatValue
        val part = cue.source
        if (part != null && cue.level > 0.01f) {
            stage.renderer.partOnScreen(part)?.let { (fx, fy) ->
                val at = stage.onWindow(fx, fy, size.width)
                if (cue.inward) voiceIn(at, t, cue.level, colors.lightWarm) else soundOut(at, t, cue.level, colors.lightNeutral)
            }
        }
    }
}

/** A tone leaving a temple: rings that open out and fade, a glow where it starts. */
private fun DrawScope.soundOut(c: Offset, t: Float, level: Float, tint: Color) {
    val reach = 70.dp.toPx() + 60.dp.toPx() * level
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.35f * level), Color.Transparent), c, reach * 0.5f), reach * 0.5f, c)
    for (k in 0 until RINGS) {
        val phase = (t / RING_SECONDS + k / RINGS.toFloat()) % 1f
        val r = 10.dp.toPx() + phase * reach
        drawCircle(tint.copy(alpha = (1f - phase) * 0.8f * level), r, c, style = Stroke((2.5f - 1.5f * phase).dp.toPx()))
    }
}

/** A voice going into the frame: rings that close in, brighter as they arrive, and a core that swells with it. */
private fun DrawScope.voiceIn(c: Offset, t: Float, level: Float, tint: Color) {
    val reach = 120.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f * level), tint.copy(alpha = 0.2f * level), Color.Transparent), c, 50.dp.toPx() * (0.6f + level)), 50.dp.toPx() * (0.6f + level), c)
    for (k in 0 until RINGS) {
        val phase = (t / RING_SECONDS + k / RINGS.toFloat()) % 1f
        val r = reach * (1f - phase) + 8.dp.toPx()
        drawCircle(tint.copy(alpha = phase * 0.7f * (0.3f + 0.7f * level)), r, c, style = Stroke((1f + 1.5f * phase).dp.toPx()))
    }
}

private const val FOLLOW_SECONDS = 0.35f
private const val ORBIT_SPEED = 0.7f
private const val ORBIT_DEGREES = 28f
private const val RINGS = 3
private const val RING_SECONDS = 1.2f

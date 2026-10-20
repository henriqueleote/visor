package com.macedonga.visor.media

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.AssistantPhase
import com.macedonga.visor.core.assistant.TurnSource
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.media.BroadcastCodec
import com.macedonga.visor.core.media.BroadcastPhase
import com.macedonga.visor.core.media.LiveOverlay
import com.macedonga.visor.core.media.LivePhase
import com.macedonga.visor.core.media.LiveProblem
import com.macedonga.visor.core.media.LiveStatus
import com.macedonga.visor.core.media.STOP_BATTERY_LOW
import com.macedonga.visor.core.media.STOP_TEMPLES_CLOSED
import com.macedonga.visor.core.media.STOP_THERMAL
import com.macedonga.visor.gallery.Pill
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.stage.AnimateLight
import com.macedonga.visor.stage.GlassesStage
import com.macedonga.visor.stage.LightPool
import com.macedonga.visor.stage.LocalStage
import com.macedonga.visor.stage.Mood
import com.macedonga.visor.stage.StageLight
import com.macedonga.visor.stage.lookOf
import com.macedonga.visor.stage.turnsThePair
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.rememberHaptics
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val TABULAR_LABEL @Composable get() = Visor.type.label.copy(fontFeatureSettings = "tnum")

// ---- The picture ----

/**
 * The video, laid out once at [cover] and moved to [placed] by a layer transform: resizing the view itself would
 * reallocate its buffer on every frame of the step back. Clipped to [visible] with rounded corners, and while it
 * blooms, to a circle round the camera's glass.
 */
@Composable
internal fun LiveFrame(video: LiveVideo, cover: Rect, placed: Rect, visible: Rect, radius: Float, bloomCenter: Offset, bloomRadius: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = true
                shape = FrameShape(visible, radius, bloomCenter, bloomRadius)
            },
    ) {
        LiveVideoView(
            video,
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed(cover.width.roundToInt(), cover.height.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(cover.left.roundToInt(), cover.top.roundToInt()) }
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = placed.width / cover.width
                    scaleY = placed.height / cover.height
                    // The layer's origin is its own corner, already at the cover's corner.
                    translationX = placed.left - cover.left
                    translationY = placed.top - cover.top
                },
        )
    }
}

private class FrameShape(val rect: Rect, val radius: Float, val center: Offset, val circle: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val frame = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }
        if (circle < 0f) return Outline.Generic(frame)
        val disc = Path().apply { addOval(Rect(center, circle.coerceAtLeast(0.5f))) }
        return Outline.Generic(Path.combine(PathOperation.Intersect, frame, disc))
    }
}

/**
 * The window's light: the picture's own colours, shrunk to a few pixels and drawn large and soft around the
 * window. Every pixel drifts towards the latest sample instead of cross-fading from one to the next, so the light
 * changes like a room's, never in steps or flashes.
 */
@Composable
internal fun LiveGlow(sample: ImageBitmap?, window: Rect, step: Float) {
    val target = remember { FloatArray(GLOW_PIXELS * 3) }
    val current = remember { FloatArray(GLOW_PIXELS * 3) }
    val bitmap = remember { Bitmap.createBitmap(GLOW_W, GLOW_H, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var primed by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sample) {
        val source = sample?.asAndroidBitmap() ?: return@LaunchedEffect
        val scaled = if (source.width == GLOW_W && source.height == GLOW_H) source else Bitmap.createScaledBitmap(source, GLOW_W, GLOW_H, true)
        val pixels = IntArray(GLOW_PIXELS)
        scaled.getPixels(pixels, 0, GLOW_W, 0, 0, GLOW_W, GLOW_H)
        for (i in pixels.indices) {
            target[i * 3] = ((pixels[i] shr 16) and 0xFF) / 255f
            target[i * 3 + 1] = ((pixels[i] shr 8) and 0xFF) / 255f
            target[i * 3 + 2] = (pixels[i] and 0xFF) / 255f
        }
        if (!primed) {
            target.copyInto(current)
            primed = true
        }
    }
    // Only while it can be seen: drift, and write the drifted colours into the bitmap that is drawn.
    LaunchedEffect(step > 0f) {
        if (step <= 0f) return@LaunchedEffect
        var last = 0L
        val pixels = IntArray(GLOW_PIXELS)
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val k = 1f - exp(-dt / GLOW_DRIFT_SECONDS)
                for (i in current.indices) current[i] += (target[i] - current[i]) * k
                for (i in pixels.indices) {
                    val r = (current[i * 3] * 255).toInt().coerceIn(0, 255)
                    val g = (current[i * 3 + 1] * 255).toInt().coerceIn(0, 255)
                    val b = (current[i * 3 + 2] * 255).toInt().coerceIn(0, 255)
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                bitmap.setPixels(pixels, 0, GLOW_W, 0, 0, GLOW_W, GLOW_H)
                frame++
            }
        }
    }
    val breath = rememberInfiniteTransition(label = "glow")
    val swell by breath.animateFloat(1f, 1.06f, infiniteRepeatable(tween(4_500), RepeatMode.Reverse), label = "swell")
    if (!primed || step <= 0f) return
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = step * 0.85f
                if (Build.VERSION.SDK_INT >= 31) renderEffect = BlurEffect(size.minDimension * 0.14f, size.minDimension * 0.14f, TileMode.Clamp)
            },
    ) {
        frame
        val w = window.width * 1.9f * swell
        val h = window.height * 1.35f * swell
        drawImage(
            image,
            dstOffset = IntOffset((window.center.x - w / 2).toInt(), (window.center.y - h / 2).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            filterQuality = FilterQuality.Low,
        )
        drawRect(
            Brush.radialGradient(
                0f to Color.Black, 0.45f to Color.Black.copy(alpha = 0.75f), 0.8f to Color.Black.copy(alpha = 0.2f), 1f to Color.Transparent,
                center = window.center, radius = maxOf(size.width, size.height) * 0.7f,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private const val GLOW_W = 6
private const val GLOW_H = 10
private const val GLOW_PIXELS = GLOW_W * GLOW_H
private const val GLOW_DRIFT_SECONDS = 0.7f

// ---- Coming up out of the glasses ----

/**
 * What stands round the glasses while the stream comes up: one line of where it has got to, and the light. The pair
 * is the one stage's; the Live screen has it grow towards the camera and fade as the first frame blooms.
 */
@Composable
internal fun Arrival(
    status: LiveStatus,
    allowed: Boolean,
    stageTop: Float,
    stageHeight: Float,
    lens: Offset,
    bloom: Float,
    leaving: Boolean,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Visor.colors
    val stage = LocalStage.current
    val haptics = rememberHaptics()
    val failed = status.phase == LivePhase.FAILED || !allowed
    StageLight(lookOf(if (leaving) Mood.CONNECTED else if (failed) Mood.FAR else Mood.GOING_LIVE, colors))
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - bloom * 1.5f).coerceIn(0f, 1f) }) {
        // Where the pair stands: a drag turns it.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, stageTop.roundToInt()) }
                .height(with(density) { stageHeight.toDp() })
                .turnsThePair { haptics.tick(0.35f) },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (stageTop + stageHeight).roundToInt()) }
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (leaving) {
                // Nothing to say on the way out.
            } else if (failed) {
                Text(stringResource(R.string.live_failed), style = Visor.type.heading, color = colors.textPrimary)
                val problem = if (!allowed) LiveProblem.WIFI_DIRECT_DENIED else status.problem
                Text(
                    stringResource(problemWords(problem)),
                    style = Visor.type.label, color = colors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                if (problem == LiveProblem.WIFI_DIRECT_DENIED) Pill(stringResource(R.string.live_allow), onAllow)
                else Pill(stringResource(R.string.live_retry), onRetry)
            } else {
                Steps(status.phase)
            }
        }
        if (!leaving) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
                RoundIcon(VisorIcons.Close, stringResource(R.string.live_close), onClose, size = 48.dp)
            }
        }
    }
}

/** "Wi-Fi Direct · Glasses · Camera": done ones quiet, the current one breathing, the rest dim. */
@Composable
private fun Steps(phase: LivePhase) {
    val colors = Visor.colors
    val at = when (phase) {
        LivePhase.IDLE, LivePhase.WIFI -> 0
        LivePhase.GLASSES -> 1
        LivePhase.CAMERA, LivePhase.RESTARTING -> 2
        else -> 3
    }
    val breath = rememberInfiniteTransition(label = "steps")
    val pulse by breath.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val names = listOf(R.string.live_step_wifi, R.string.live_step_glasses, R.string.live_step_camera)
        names.forEachIndexed { i, name ->
            if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(colors.textTertiary))
            val color by animateColorAsState(
                when {
                    i < at -> colors.textSecondary
                    i == at -> colors.textPrimary
                    else -> colors.textTertiary
                },
                tween(400), label = "step",
            )
            Text(
                stringResource(name), style = Visor.type.label, color = color,
                modifier = Modifier.graphicsLayer { alpha = if (i == at) pulse else 1f },
            )
        }
    }
}

private fun problemWords(problem: LiveProblem?): Int = when (problem) {
    LiveProblem.NOT_CONNECTED, null -> R.string.live_problem_not_connected
    LiveProblem.IN_CASE -> R.string.live_problem_in_case
    LiveProblem.WIFI_OFF -> R.string.live_problem_wifi_off
    LiveProblem.WIFI_DIRECT_DENIED -> R.string.live_problem_wifi_direct
    LiveProblem.NO_KEY -> R.string.live_problem_no_key
    LiveProblem.JOIN_FAILED -> R.string.live_problem_join
    LiveProblem.SESSION_FAILED -> R.string.live_problem_session
    LiveProblem.NO_VIDEO -> R.string.live_problem_no_video
    LiveProblem.GAVE_UP -> R.string.live_problem_gave_up
    LiveProblem.VPN -> R.string.live_problem_vpn
}

// ---- Over the picture ----

/**
 * What sits on the full-screen picture: the status in the corner, and whichever of the readouts, heat, grid and
 * horizon are on. They fade as the picture steps back, except the grid, which stays on the picture.
 */
@Composable
internal fun LiveOverlays(model: LiveViewModel, visible: Rect, step: Float, bloom: Float) {
    val settings by model.settings.settings.collectAsStateWithLifecycle()
    val status by model.live.status.collectAsStateWithLifecycle()
    val shown = ((bloom - 0.75f) / 0.25f).coerceIn(0f, 1f)
    if (LiveOverlay.GRID in settings.overlays) Grid(visible, shown)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown * (1f - step * 2.2f).coerceIn(0f, 1f) },
    ) {
        // A dark breath at the top and bottom, only as much as the words need.
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent))))
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            StatusChip(model)
            if (status.phase == LivePhase.RESTARTING) Reconnecting(Modifier.padding(top = 8.dp))
        }
        Column(
            Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (LiveOverlay.READOUTS in settings.overlays) Readouts(model, stacked = true)
        }
        if (LiveOverlay.HEAT in settings.overlays) {
            Heat(model, Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp))
        }
    }
}

/**
 * "Hey Meta" during the Live, as subtitles at the foot of the picture: what was asked, small, and the answer under
 * it while the glasses speak it. A turn stays a few seconds after its answer, then fades.
 */
@Composable
internal fun Captions(model: LiveViewModel, visible: Rect, alpha: Float) {
    val colors = Visor.colors
    val turns by model.assistant.turns.collectAsStateWithLifecycle()
    val phase by model.assistant.phase.collectAsStateWithLifecycle()
    val since by model.live.since.collectAsStateWithLifecycle()
    val turn = turns.lastOrNull { it.source != TurnSource.PHONE }?.takeIf { since > 0 && it.at >= since }
    val busy = phase == AssistantPhase.THINKING || phase == AssistantPhase.SPEAKING
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(turn?.interactionId, turn?.answer, busy) {
        showing = turn != null
        if (turn != null && !busy && turn.answer.isNotEmpty()) {
            delay(CAPTION_HOLD_MS)
            showing = false
        }
    }
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = showing && turn != null,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (visible.bottom - with(density) { 180.dp.toPx() }).roundToInt()) }
            .graphicsLayer { this.alpha = alpha },
        enter = fadeIn(tween(240)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 4 },
        exit = fadeOut(tween(400)),
    ) {
        val t = turn ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.question, style = Visor.type.label, color = colors.textPrimary.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            val pulse = rememberInfiniteTransition(label = "thinking").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "thinking")
            if (t.answer.isEmpty()) {
                Text(stringResource(R.string.live_ai_thinking), style = Visor.type.body, color = colors.textPrimary, modifier = Modifier.graphicsLayer { this.alpha = pulse.value })
            } else {
                Text(t.answer, style = Visor.type.heading, color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 4)
            }
        }
    }
}

private const val CAPTION_HOLD_MS = 6_000L

/** Thirds over the visible picture, hairline. */
@Composable
private fun Grid(visible: Rect, alpha: Float) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        val c = Color.White.copy(alpha = 0.28f)
        val stroke = 1.dp.toPx()
        for (k in 1..2) {
            val x = visible.left + visible.width * k / 3
            val y = visible.top + visible.height * k / 3
            drawLine(c, Offset(x, visible.top), Offset(x, visible.bottom), stroke)
            drawLine(c, Offset(visible.left, y), Offset(visible.right, y), stroke)
        }
    }
}

/** "● LIVE 12:04", or REC with the recording's own time, or ON AIR. The dot blinks once a second, like the LED. */
@Composable
internal fun StatusChip(model: LiveViewModel, modifier: Modifier = Modifier) {
    val colors = Visor.colors
    val since by model.live.since.collectAsStateWithLifecycle()
    val recording by model.outputs.recording.collectAsStateWithLifecycle()
    val broadcast by model.outputs.broadcast.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    val (word, from) = when {
        recording != null -> stringResource(R.string.live_rec) to recording!!.since
        broadcast.phase == BroadcastPhase.LIVE -> stringResource(R.string.live_on_air) to broadcast.since
        else -> stringResource(R.string.live_badge) to since
    }
    val blink = rememberInfiniteTransition(label = "led")
    val led by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "led")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.38f)).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).graphicsLayer { alpha = led }.clip(CircleShape).background(colors.signal))
        Spacer(Modifier.width(8.dp))
        Text(word, style = Visor.type.label, color = colors.textPrimary)
        if (from > 0) {
            Spacer(Modifier.width(8.dp))
            Text(clockWords(now - from), style = TABULAR_LABEL, color = colors.textPrimary)
        }
    }
}

@Composable
private fun Reconnecting(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.live_reconnecting),
        style = Visor.type.label, color = Visor.colors.textPrimary,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.38f)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Size, frame rate, bitrate and whether Visor picks them: what the link is doing right now. */
@Composable
internal fun Readouts(model: LiveViewModel, stacked: Boolean) {
    val colors = Visor.colors
    val stats by model.live.receiver.stats.collectAsStateWithLifecycle()
    val decoded by model.live.decodedSize.collectAsStateWithLifecycle()
    val auto by model.live.auto.collectAsStateWithLifecycle()
    val rung by model.live.rung.collectAsStateWithLifecycle()
    val height = decoded?.substringAfter('x')?.toIntOrNull() ?: rung?.quality?.height
    val size = height?.let { "${minOf(it, decoded?.substringBefore('x')?.toIntOrNull() ?: it)}p" } ?: "—"
    val mbit = "%.1f Mbit/s".format(stats.bytesPerSecond * 8 / 1_000_000f)
    val parts = listOf(size, "${stats.fps} fps", mbit, stringResource(if (auto) R.string.live_auto else R.string.live_manual))
    if (stacked) {
        Column(horizontalAlignment = Alignment.End) {
            for (p in parts) Text(p, style = TABULAR_LABEL, color = colors.textPrimary.copy(alpha = 0.85f))
        }
    } else {
        Text(parts.joinToString("  ·  "), style = TABULAR_LABEL, color = colors.textSecondary)
    }
}

/** How warm the glasses are and how much battery they have left. */
@Composable
internal fun Heat(model: LiveViewModel, modifier: Modifier = Modifier) {
    val colors = Visor.colors
    val device by model.live.device.collectAsStateWithLifecycle()
    val stream by model.live.health.collectAsStateWithLifecycle()
    val health = device?.thermal?.health ?: stream.thermal
    val percent = device?.battery?.percent ?: stream.battery?.toLong()
    val hot = health == "SEVERE" || health == "CRITICAL" || health == "SHUTDOWN"
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(VisorIcons.Heat, null, Modifier.size(16.dp), tint = if (hot) colors.signal else colors.textPrimary)
        Text(stringResource(heatWords(health)), style = Visor.type.label, color = if (hot) colors.signal else colors.textPrimary)
        if (percent != null) {
            Spacer(Modifier.width(6.dp))
            Icon(VisorIcons.Battery, null, Modifier.size(16.dp), tint = if (percent <= 10) colors.signal else colors.textPrimary)
            Text("$percent%", style = TABULAR_LABEL, color = colors.textPrimary)
        }
    }
}

private fun heatWords(health: String?): Int = when (health) {
    "LIGHT" -> R.string.live_heat_light
    "MODERATE" -> R.string.live_heat_moderate
    "SEVERE" -> R.string.live_heat_severe
    "CRITICAL", "SHUTDOWN" -> R.string.live_heat_critical
    else -> R.string.live_heat_normal
}

/** The heat, the broadcast and the glasses' own photos, said once as they happen. */
@Composable
internal fun LiveWatchers(model: LiveViewModel) {
    val device by model.live.device.collectAsStateWithLifecycle()
    val stream by model.live.health.collectAsStateWithLifecycle()
    val broadcast by model.outputs.broadcast.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val health = device?.thermal?.health ?: stream.thermal
    LaunchedEffect(model) {
        model.live.stopped.collect { reason ->
            when (reason) {
                STOP_THERMAL -> model.say(R.string.live_stopped_heat, warning = true)
                STOP_BATTERY_LOW -> model.say(R.string.live_stopped_battery, warning = true)
                STOP_TEMPLES_CLOSED -> model.say(R.string.live_stopped_folded, warning = true)
            }
        }
    }
    // Only heat that may stop the camera is worth a word, and only once the picture is up.
    val status by model.live.status.collectAsStateWithLifecycle()
    val severe = health == "SEVERE" || health == "CRITICAL"
    LaunchedEffect(severe, status.phase == LivePhase.RUNNING) {
        if (severe && status.phase == LivePhase.RUNNING) {
            model.say(R.string.live_heat_severe_warning, warning = true)
            haptics.error()
        }
    }
    LaunchedEffect(broadcast.phase) {
        when (broadcast.phase) {
            BroadcastPhase.RECONNECTING -> model.say(R.string.live_broadcast_retrying, warning = true)
            BroadcastPhase.FAILED -> {
                model.say(R.string.live_broadcast_failed, broadcast.problem, warning = true)
                haptics.error()
            }
            BroadcastPhase.LIVE -> haptics.done()
            else -> Unit
        }
    }
}

/** In picture in picture: only that something is being kept. */
@Composable
internal fun BoxScope.PipBadge(recording: Boolean) {
    Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(10.dp).clip(CircleShape).background(if (recording) Visor.colors.signal else Color.White))
}

/** One line at the top for a few seconds: what was saved, with a way to open it, or a warning. */
@Composable
internal fun BoxScope.NoticeIsland(model: LiveViewModel, onOpen: (Uri) -> Unit) {
    val colors = Visor.colors
    val notice by model.notice.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf(notice) }
    if (notice != null) last = notice
    AnimatedVisibility(
        visible = notice != null,
        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 56.dp, start = 24.dp, end = 24.dp),
        enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { -it / 2 },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 3 },
    ) {
        val n = last ?: return@AnimatedVisibility
        Row(
            Modifier.clip(RoundedCornerShape(22.dp)).background(colors.raised.copy(alpha = 0.94f)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (n.warning) {
                Icon(VisorIcons.Heat, null, Modifier.size(18.dp), tint = colors.textPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (n.arg != null) stringResource(n.text, n.arg) else stringResource(n.text),
                style = Visor.type.label, color = colors.textPrimary,
                modifier = Modifier.padding(vertical = 6.dp).weight(1f, fill = false),
            )
            n.uri?.let { uri ->
                Spacer(Modifier.width(10.dp))
                Pill(stringResource(R.string.live_open), { onOpen(uri) }, small = true)
            }
            if (n.glassesHost) {
                Spacer(Modifier.width(10.dp))
                Pill(stringResource(R.string.live_use_glasses_host), { model.useGlassesHost() }, small = true)
            }
            if (n.uri == null && !n.glassesHost) Spacer(Modifier.width(8.dp))
        }
    }
}

// ---- Stepped back ----

/**
 * Around the lit window: close and the sheet above, the readouts under it, and at the bottom the toggles and the
 * three that save: what just happened, recording, a photo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SteppedChrome(
    model: LiveViewModel,
    window: Rect,
    step: Float,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onBroadcast: () -> Unit,
    onSnapshot: () -> Unit,
    onGlassesPhoto: () -> Unit,
) {
    val colors = Visor.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val settings by model.settings.settings.collectAsStateWithLifecycle()
    val recording by model.outputs.recording.collectAsStateWithLifecycle()
    val broadcast by model.outputs.broadcast.state.collectAsStateWithLifecycle()
    val held by produceState(0L) {
        while (true) {
            value = model.outputs.replay.heldUs / 1_000_000
            delay(500)
        }
    }
    val rise = with(density) { 24.dp.toPx() } * (1f - step)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = step; translationY = rise }) {
        // Placed by the bars' room whether they show or not, as the window is, so nothing moves when they come back.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(VisorIcons.Close, stringResource(R.string.live_close), onClose, size = 48.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StatusChip(model) }
            RoundIcon(VisorIcons.Settings, stringResource(R.string.live_more), onMore, size = 48.dp)
        }
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(0, (window.bottom + with(density) { 14.dp.toPx() }).roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Readouts(model, stacked = false)
            Heat(model)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val airing = broadcast.phase != BroadcastPhase.OFF && broadcast.phase != BroadcastPhase.FAILED
                Toggle(VisorIcons.Live, stringResource(R.string.live_broadcast), airing) { haptics.toggle(); onBroadcast() }
                Toggle(if (settings.listen) VisorIcons.Speaker else VisorIcons.SpeakerOff, stringResource(R.string.live_listen), settings.listen) {
                    haptics.toggle()
                    model.update { it.copy(listen = !it.listen) }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                Captioned(spanWords(held)) {
                    RoundIcon(VisorIcons.Replay, stringResource(R.string.live_replay), { haptics.done(); model.saveReplay() }, size = 56.dp, background = colors.raised.copy(alpha = 0.85f))
                }
                RecordButton(recording != null) {
                    haptics.shutter()
                    model.toggleRecording()
                }
                Captioned(stringResource(R.string.live_photo)) {
                    // A tap keeps the picture on screen; a long press asks the glasses for a full photo.
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.raised.copy(alpha = 0.85f))
                            .combinedClickable(
                                onClickLabel = stringResource(R.string.live_photo),
                                onLongClickLabel = stringResource(R.string.live_glasses_photo),
                                onLongClick = {
                                    haptics.connected()
                                    onGlassesPhoto()
                                },
                                onClick = {
                                    haptics.shutter()
                                    onSnapshot()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VisorIcons.Camera, null, Modifier.size(22.dp), tint = colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Captioned(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Text(caption, style = TABULAR_LABEL, color = Visor.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A white ring; inside it a white disc, or while recording a small square in the signal colour. */
@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val colors = Visor.colors
    val inner by animateDpAsState(if (recording) 26.dp else 58.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "rec")
    val corner by animateDpAsState(if (recording) 7.dp else 29.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "corner")
    val fill by animateColorAsState(if (recording) colors.signal else Color.White, tween(180), label = "fill")
    val label = stringResource(if (recording) R.string.live_stop_recording else R.string.live_record)
    Box(
        Modifier.size(76.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).clip(RoundedCornerShape(corner)).background(fill))
    }
}

@Composable
private fun Toggle(icon: ImageVector, text: String, on: Boolean, onClick: () -> Unit) {
    val colors = Visor.colors
    val back by animateColorAsState(if (on) Color.White else colors.raised.copy(alpha = 0.85f), tween(180), label = "back")
    val front by animateColorAsState(if (on) Color.Black else colors.textPrimary, tween(180), label = "front")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(back).clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = front)
        Spacer(Modifier.width(8.dp))
        Text(text, style = Visor.type.label, color = front)
    }
}

// ---- Sheets ----

/** What sits on the picture, each on or off, and the way to the rest of the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveSheet(model: LiveViewModel, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val colors = Visor.colors
    val settings by model.settings.settings.collectAsStateWithLifecycle()
    val auto by model.live.auto.collectAsStateWithLifecycle()
    val rung by model.live.rung.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Text(stringResource(R.string.live_more), style = Visor.type.heading, color = colors.textPrimary, modifier = Modifier.padding(bottom = 14.dp))
            Group {
                val rows = listOf(
                    LiveOverlay.READOUTS to R.string.live_overlay_readouts,
                    LiveOverlay.HEAT to R.string.live_overlay_heat,
                    LiveOverlay.GRID to R.string.live_overlay_grid,
                )
                rows.forEachIndexed { i, (overlay, name) ->
                    if (i > 0) Hairline()
                    SwitchRow(stringResource(name), overlay in settings.overlays) { model.setOverlay(overlay, it) }
                }
                Hairline()
                SwitchRow(stringResource(R.string.live_listen_long), settings.listen) { on -> model.update { it.copy(listen = on) } }
            }
            Spacer(Modifier.height(12.dp))
            Group {
                val detail = (if (auto) stringResource(R.string.live_auto) else stringResource(R.string.live_manual)) +
                    (rung?.let { " · ${it.quality.label}" } ?: "")
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.live_more_settings), style = Visor.type.body, color = colors.textPrimary)
                        Text(detail, style = Visor.type.label, color = colors.textSecondary)
                    }
                    Icon(VisorIcons.Chevron, null, Modifier.size(18.dp), tint = colors.textSecondary)
                }
            }
        }
    }
}

/**
 * Where the Live goes out to, and the button that sends it. While it is on air, how it is going and the way to
 * end it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BroadcastSheet(model: LiveViewModel, onDismiss: () -> Unit) {
    val colors = Visor.colors
    val settings by model.settings.settings.collectAsStateWithLifecycle()
    val state by model.outputs.broadcast.state.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(settings.rtmpUrl) }
    var key by rememberSaveable { mutableStateOf(settings.rtmpKey) }
    var codec by rememberSaveable { mutableStateOf(settings.rtmpCodec) }
    val airing = state.phase != BroadcastPhase.OFF && state.phase != BroadcastPhase.FAILED
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Text(stringResource(R.string.live_broadcast), style = Visor.type.heading, color = colors.textPrimary)
            if (airing) {
                val host = runCatching { java.net.URI(settings.rtmpUrl).host }.getOrNull() ?: settings.rtmpUrl
                Text(stringResource(R.string.live_broadcast_to, host), style = Visor.type.label, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "%.1f Mbit/s".format(state.kbps / 1000f),
                    style = Visor.type.readoutSmall, color = colors.textPrimary, modifier = Modifier.padding(top = 18.dp, bottom = 22.dp),
                )
                Pill(stringResource(R.string.live_broadcast_end), {
                    model.stopBroadcast()
                    onDismiss()
                })
                return@Column
            }
            Spacer(Modifier.height(14.dp))
            Field(url, { url = it }, stringResource(R.string.live_broadcast_server), stringResource(R.string.live_broadcast_server_hint), secret = false)
            Spacer(Modifier.height(10.dp))
            Field(key, { key = it }, stringResource(R.string.live_broadcast_key), "", secret = true)
            Spacer(Modifier.height(12.dp))
            Group {
                ChoiceRow(stringResource(R.string.live_broadcast_h264), codec == BroadcastCodec.H264) { codec = BroadcastCodec.H264 }
                Hairline()
                ChoiceRow(stringResource(R.string.live_broadcast_hevc), codec == BroadcastCodec.HEVC) { codec = BroadcastCodec.HEVC }
            }
            Spacer(Modifier.height(18.dp))
            val ready = (url.startsWith("rtmp://") || url.startsWith("rtmps://")) && key.isNotBlank()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer { alpha = if (ready) 1f else 0.4f }) {
                    Pill(stringResource(R.string.live_broadcast_go), {
                        if (ready) {
                            model.update { it.copy(rtmpUrl = url.trim(), rtmpKey = key.trim(), rtmpCodec = codec) }
                            model.startBroadcast()
                            onDismiss()
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String, secret: Boolean) {
    val colors = Visor.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (hint.isNotEmpty()) Text(hint, color = colors.textTertiary) },
        singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White, unfocusedBorderColor = colors.control,
            focusedLabelColor = colors.textPrimary, unfocusedLabelColor = colors.textSecondary,
            cursorColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Visor.colors.raised)) { content() }
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(Visor.colors.hairline))
}

@Composable
internal fun SwitchRow(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Visor.colors
    val haptics = rememberHaptics()
    val change by rememberUpdatedState(onChange)
    Row(
        Modifier.fillMaxWidth().clickable { haptics.toggle(); change(!on) }.padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Visor.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = { haptics.toggle(); change(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.control, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = Visor.colors
    val fill by animateColorAsState(if (on) Color.White else Color.Transparent, tween(180), label = "choice")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, if (on) Color.White else colors.textTertiary, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(fill))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = Visor.type.body, color = colors.textPrimary)
    }
}

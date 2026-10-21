package com.macedonga.visor.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.dp
import com.macedonga.visor.core.connection.GlassesState
import com.macedonga.visor.core.session.LinkState
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the temples are closed: folded unless the glasses are with us and open. While Bluetooth is paused for an
 * import or the Live nothing is said about the hinge, so the last word holds.
 */
fun closedOf(state: GlassesState, mood: Mood, lastFolded: Boolean): Boolean = when (mood) {
    Mood.CONNECTED, Mood.RECORDING, Mood.LOW_BATTERY, Mood.IMPORTING -> state.device?.hinge?.let { it == "CLOSED" } ?: lastFolded
    else -> true
}

/** The LED and the touches that follow from the glasses' state, wherever the pair stands. */
@Composable
fun Choreography(state: GlassesState, mood: Mood, light: LightFrame, pose: GlassesPose, onConnected: () -> Unit, onShutter: () -> Unit) {
    var lastMood by remember { mutableStateOf(mood) }
    LaunchedEffect(mood) {
        if (mood == Mood.CONNECTED && lastMood == Mood.SEARCHING) onConnected()
        lastMood = mood
    }
    // Only a photo taken while the screen is up flashes, not the last one the glasses still report.
    val camera = state.device?.camera
    val cameraBefore = remember { camera }
    LaunchedEffect(camera) {
        if (camera == "CAPTURE_PHOTO_STARTED" && camera != cameraBefore) {
            light.flash()
            onShutter()
        }
    }
    // The model's LED is the front capture light; the status LED (lit while charging) is another one.
    val ledOn = state.device?.led?.privacyLedActive == true
    LaunchedEffect(light, ledOn, mood) {
        snapshotFlow { light.tick.longValue }.collect {
            pose.led = when {
                mood == Mood.RECORDING -> if (light.still) 1f else 0.55f + 0.45f * sin(2 * PI * light.time).toFloat()
                ledOn -> 1f
                else -> 0f
            }
        }
    }
}

/**
 * The temples following [closed]: closing, the right goes first and the left lands on it; opening, the left lifts off
 * first. At launch the pair arrives folded and unfolds, once [arrival] returns; elsewhere it starts as it should be.
 */
@Composable
fun Hinge(closed: Boolean, pose: GlassesPose, firstVisit: Boolean = false, arrival: suspend () -> Unit = {}) {
    val start = if (firstVisit || closed) 1f else 0f
    val left = remember { Animatable(start) }
    val right = remember { Animatable(start) }
    val framing = remember { Animatable(start) }
    var arrived by remember { mutableStateOf(!firstVisit) }
    LaunchedEffect(closed) {
        if (!arrived) {
            arrival()
            delay(ARRIVAL_MS)
            arrived = true
        }
        val target = if (closed) 1f else 0f
        val (first, second) = if (closed) right to left else left to right
        launch { first.animateTo(target, HINGE) }
        launch {
            delay(STAGGER_MS)
            second.animateTo(target, HINGE)
        }
        // The framing moves like a camera operator: eases in, follows, settles after the temples.
        launch { framing.animateTo(target, tween(FRAMING_MS, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f))) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(left.value, right.value, framing.value) }.collect { (l, r, f) ->
            pose.foldLeft = againstStops(l)
            pose.foldRight = againstStops(r)
            pose.framing = f
        }
    }
}

/**
 * Away from the home, the mood the home last gave the pair, kept true while the link moves: connected the glasses
 * speak for themselves; gone, a mood that needed them falls back to looking for them.
 */
fun moodAway(state: GlassesState, live: Boolean, last: Mood): Mood = when {
    state.target == null -> Mood.ABSENT
    state.link == LinkState.READY -> moodOf(state, live)
    last in setOf(Mood.CONNECTED, Mood.CHARGING, Mood.LOW_BATTERY, Mood.RECORDING, Mood.IMPORTING, Mood.GOING_LIVE) -> Mood.SEARCHING
    else -> last
}

/** The pair saying yes: a dip of the head and back. */
suspend fun GlassesPose.nod() {
    val a = Animatable(0f)
    a.animateTo(NOD_DEGREES, tween(140, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) { pitchDegrees = value }
    a.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f)) { pitchDegrees = value }
}

/** The pair saying no: a short shake from side to side. */
suspend fun GlassesPose.shake() {
    val a = Animatable(0f)
    for (to in listOf(SHAKE_DEGREES, -SHAKE_DEGREES, SHAKE_DEGREES * 0.6f, -SHAKE_DEGREES * 0.35f)) {
        a.animateTo(to, tween(90)) { gestureYaw = value }
    }
    a.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f)) { gestureYaw = value }
}

/** How much the pair drifts on its own: asleep out of reach, restless while searching, calm when ready. */
fun liveliness(mood: Mood): Float = when (mood) {
    Mood.ABSENT, Mood.FAR -> 0.25f
    Mood.SEARCHING -> 1.3f
    Mood.CHARGING -> 0.45f
    Mood.RECORDING -> 0.6f
    Mood.CONNECTED, Mood.LOW_BATTERY -> 1f
    Mood.IMPORTING, Mood.GOING_LIVE -> 0.8f
    Mood.ASLEEP -> 0.2f
    Mood.IN_CASE -> 0.45f
}

/** A temple springing past a stop bounces back off it rather than through the frame. */
private fun againstStops(v: Float) = when {
    v > 1f -> 2f - v
    v < 0f -> -v
    else -> v
}

private val HINGE = spring<Float>(dampingRatio = 0.7f, stiffness = 150f)
private const val STAGGER_MS = 140L
private const val FRAMING_MS = 1050
/** Coming home, the pair has faded in before it unfolds. */
private const val ARRIVAL_MS = 520L
private const val NOD_DEGREES = 7f
private const val SHAKE_DEGREES = 9f

/** The stage's width to height, the same wherever the pair stands. */
const val STAGE_ASPECT = 1.02f

/**
 * The pool's centre, in widths from the top: the floor under the pair projects to 0.565 of the width with the
 * renderer's camera (15 degrees up, 24 degree field), and the pool sits there, a touch towards the front edge.
 */
const val POOL_Y = 0.575f

/** The strip the home's stage folds down to under the status bar, and how small the pair is in it. */
val HOME_STRIP = 200.dp
const val HOME_STRIP_SCALE = 0.75f

/** The rounded top corners of a sheet that slides up under a stage. */
val SHEET_RADIUS = 28.dp

/** A sheet whose rounded top sits at [top]. */
fun sheetShape(top: Float, radius: Float) = GenericShape { size, _ ->
    addRoundRect(
        RoundRect(0f, top, size.width, size.height + radius, topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius)),
    )
}

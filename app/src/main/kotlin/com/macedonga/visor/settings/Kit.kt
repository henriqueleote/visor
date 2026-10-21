package com.macedonga.visor.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.core.settings.GlassesSettings
import com.macedonga.visor.core.settings.SettingsState
import com.macedonga.visor.core.settings.same
import com.macedonga.visor.gallery.ProgressRing
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.stage.AnimateLight
import com.macedonga.visor.stage.LightPool
import com.macedonga.visor.stage.LocalStage
import com.macedonga.visor.stage.StageHolder
import com.macedonga.visor.stage.StageLight
import com.macedonga.visor.stage.lookOf
import com.macedonga.visor.stage.moodAway
import com.macedonga.visor.stage.nod
import com.macedonga.visor.stage.shake
import com.macedonga.visor.ui.Haptics
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.asHeading
import com.macedonga.visor.ui.lineLimit
import com.macedonga.visor.ui.switchRow
import com.macedonga.visor.ui.rememberHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Back, for whichever page is on top. The nav graph provides it. */
val LocalBack = staticCompositionLocalOf<() -> Unit> { {} }

@HiltViewModel
class PageModel @Inject constructor(
    val connection: GlassesConnection,
    val settings: GlassesSettings,
    val pins: Pins,
    val toggles: VisorToggles,
) : ViewModel()

/**
 * What every row on a settings page reads: the glasses' settings as they stand, whether they can be written, the
 * notice at the foot of the page, and the details sheet a long press opens.
 */
class PageHost(
    val settings: GlassesSettings,
    val pins: Pins,
    val toggles: VisorToggles,
    val haptics: Haptics,
    val state: SettingsState,
    val ready: Boolean,
    val notice: PageNotice,
    val openDetails: (String) -> Unit,
)

val LocalHost = staticCompositionLocalOf<PageHost> { error("no settings page above this row") }

/** One line at the foot of the page, for a moment. */
class PageNotice {
    var text by mutableStateOf<String?>(null)
        private set
    var at by mutableLongStateOf(0L)
        private set

    fun show(words: String) {
        text = words
        at = System.nanoTime()
    }

    fun clear() {
        text = null
    }
}

/**
 * What every settings screen stands on: the rows' host, the glasses' light kept in the mood the home gave it, the
 * glasses answering each write, the notice at the foot and the details sheet a long press opens. [seeThrough] leaves
 * the canvas out, for a screen that shows the stage under it.
 */
@Composable
fun SettingsHost(model: PageModel = hiltViewModel(), seeThrough: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val colors = Visor.colors
    val stage = LocalStage.current
    val haptics = rememberHaptics()
    val glasses by model.connection.state.collectAsStateWithLifecycle()
    val state by model.settings.state.collectAsStateWithLifecycle()
    val ready = glasses.link == LinkState.READY
    ReadSettingsWhenReady(model.settings, ready)
    StageLight(lookOf(moodAway(glasses, live = false, last = stage.mood), colors))
    Nods(model.settings, stage, haptics)

    val notice = remember { PageNotice() }
    var details by remember { mutableStateOf<String?>(null) }
    val host = PageHost(model.settings, model.pins, model.toggles, haptics, state, ready, notice) { key ->
        haptics.tick(0.5f)
        details = key
    }
    CompositionLocalProvider(LocalHost provides host) {
        Box(if (seeThrough) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(colors.canvas)) {
            content()
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            NoticePill(notice, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 20.dp))
        }
        details?.let { key -> SettingDetails(key, onDismiss = { details = null }) }
    }
}

/**
 * A settings page under the glasses' light: back and the page's name side by side in the bar, as on Settings itself,
 * the lede under them, and the bar darkening once the rows go under it. [sheet] is the page's own sheet, which reads
 * the rows' host like a row.
 */
@Composable
fun SettingsPage(
    title: String,
    lede: String? = null,
    light: Float = 0.55f,
    list: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    sheet: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) = SettingsHost {
    val colors = Visor.colors
    val stage = LocalStage.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val fadePx = with(density) { 160.dp.toPx() }
        val scrolled by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / fadePx).coerceIn(0f, 1f) } }

        if (light > 0f) {
            LightPool(
                stage.light, Offset(0.5f, 0.1f), Size(0.6f, 0.3f), Size(0.001f, 0.001f),
                Modifier.fillMaxWidth().height(maxWidth * 0.95f).graphicsLayer { alpha = light * (1f - scrolled * 0.85f) },
            )
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top + BAR, bottom = bottom + 48.dp),
        ) {
            item(key = "page-head") { PageLede(lede) }
            content()
        }

        val barShown by animateFloatAsState(if (scrolled > 0.05f) 1f else 0f, tween(180), label = "bar")
        // Solid behind the title, so rows passing under it never show through; it fades only below the bar.
        val solid = (top + BAR) / (top + BAR + BAR_FADE)
        Box(
            Modifier
                .fillMaxWidth()
                .height(top + BAR + BAR_FADE)
                .graphicsLayer { alpha = barShown }
                .background(Brush.verticalGradient(0f to colors.canvas, solid to colors.canvas, 1f to Color.Transparent)),
        )
        TopBar(title, actions)
    }
    sheet()
}

/** Back and the page's name beside it, on one line: a long name comes down a few sizes rather than breaking. */
@Composable
fun TopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val back = LocalBack.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(BAR).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(VisorIcons.ChevronLeft, stringResource(R.string.settings_back), back)
        BasicText(
            title,
            style = Visor.type.title.copy(color = Visor.colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = Visor.type.title.fontSize, stepSize = 1.sp),
            modifier = Modifier.weight(1f).asHeading().padding(start = 4.dp, end = 8.dp),
        )
        actions()
    }
}

/** What a page is for, in a line or two under the bar. */
@Composable
private fun PageLede(lede: String?) {
    if (lede == null) Spacer(Modifier.height(12.dp))
    else Text(lede, style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(horizontal = SIDE).padding(top = 8.dp, bottom = 22.dp))
}

/**
 * The glasses answering a write: kept, their light swells and the pair nods; refused, the light falters twice and
 * the pair shakes its head.
 */
@Composable
private fun Nods(settings: GlassesSettings, stage: StageHolder, haptics: Haptics) {
    LaunchedEffect(settings) {
        var before = settings.state.value
        settings.state.collect { now ->
            val settled = before.pending.keys - now.pending.keys - before.quiet
            val refused = now.kept - before.kept - now.quiet
            if (refused.isNotEmpty()) {
                stage.light.flicker()
                haptics.error()
                launch { stage.pose.shake() }
            } else if (settled.any { key -> key !in now.kept && same(settings.catalogue[key]?.kind, now.stored[key], before.pending.getValue(key)) }) {
                stage.light.pulse(0.55f)
                haptics.tick(0.35f)
                launch { stage.pose.nod() }
            }
            before = now
        }
    }
}

@Composable
private fun NoticePill(notice: PageNotice, modifier: Modifier) {
    LaunchedEffect(notice.at) {
        if (notice.text != null) {
            delay(2_400)
            notice.clear()
        }
    }
    var last by remember { mutableStateOf("") }
    notice.text?.let { last = it }
    AnimatedVisibility(
        notice.text != null,
        modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 2 },
    ) {
        Text(
            last,
            style = Visor.type.label,
            color = Visor.colors.textPrimary,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Visor.colors.control).padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

internal val BAR = 56.dp
private val BAR_FADE = 28.dp
internal val SIDE = 24.dp

/** A run of rows on a raised card. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Visor.colors.raised),
        content = content,
    )
}

/** Between two rows on a card, from edge to edge. */
@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Visor.colors.hairline))
}

/** Rows on one card with hairlines between them. */
@Composable
fun Rows(vararg rows: (@Composable () -> Unit)?) {
    val shown = rows.filterNotNull()
    if (shown.isEmpty()) return
    Card {
        shown.forEachIndexed { i, row ->
            if (i > 0) Hairline()
            row()
        }
    }
}

/** A small grey heading over a card. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Visor.type.label, color = Visor.colors.textSecondary, modifier = modifier.asHeading().padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 8.dp))
}

/** A note under a card. */
@Composable
fun Footnote(text: String, color: Color = Visor.colors.textSecondary) {
    Text(text, style = Visor.type.label, color = color, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp))
}

/** Changed from its default. */
@Composable
fun ChangedDot() {
    Box(Modifier.padding(start = 8.dp).size(6.dp).clip(CircleShape).background(Visor.colors.textSecondary))
}

/** The mono line engineering rows carry: the key the firmware knows, and what the glasses store. */
@Composable
fun KeyLine(text: String) {
    Text(text, style = MONO, color = Visor.colors.textSecondary, maxLines = lineLimit(2), overflow = TextOverflow.Ellipsis)
}

val MONO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)

/**
 * A row's frame: [title] with an optional dot and lines under it, [value] beside it when both fit on one line and
 * under it when they do not, [trailing] at the end, a tap and a long press. While [busy] a turning ring says the
 * glasses have not answered yet. A locked row is dimmed, and its tap says why instead of acting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowFrame(
    title: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    changed: Boolean = false,
    value: String? = null,
    busy: Boolean = false,
    overline: String? = null,
    sub: String? = null,
    keyLine: String? = null,
    problem: String? = null,
    dense: Boolean = false,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    dim: Boolean = false,
    state: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val host = LocalHost.current
    val blocked = stringResource(R.string.settings_locked)
    val tap: (() -> Unit)? = when {
        locked -> ({ host.haptics.tick(0.3f); host.notice.show(blocked) })
        else -> onClick
    }
    val base = if (tap != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = tap ?: {}, onLongClick = onLongClick).then(state)
    } else Modifier
    Column(
        base
            .fillMaxWidth()
            .heightIn(min = if (dense) 48.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = if (dense) 10.dp else 13.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.alpha(if (locked || dim) 0.4f else 1f), verticalAlignment = Alignment.CenterVertically) {
            RowText(overline, title, changed, value, sub, keyLine, Modifier.weight(1f))
            if (busy) Spinner(Modifier.padding(start = 12.dp))
            trailing()
        }
        if (below != null) {
            Column(Modifier.alpha(if (locked) 0.4f else 1f).padding(top = 12.dp), content = below)
        }
        if (problem != null) Text(problem, style = Visor.type.label, color = Visor.colors.signal, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * A row's words. When the name fits beside the value whole, the value sits against the right edge, centred on
 * everything to its left, in line with the chevron or switch after it, and the lines under the name wrap short of it.
 * Otherwise the value goes under the name, so neither is squeezed into a column of its own.
 */
@Composable
private fun RowText(overline: String?, title: String, changed: Boolean, value: String?, sub: String?, keyLine: String?, modifier: Modifier) {
    val gap = 16.dp
    val line = 2.dp
    Layout(
        contents = listOf(
            {
                Column(verticalArrangement = Arrangement.spacedBy(line)) {
                    if (overline != null) Text(overline, style = Visor.type.label, color = Visor.colors.textSecondary, maxLines = lineLimit(1), overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
                        if (changed) ChangedDot()
                    }
                }
            },
            { if (value != null) Text(value, style = Visor.type.body, color = Visor.colors.textSecondary) },
            {
                if (sub != null || keyLine != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(line)) {
                        if (sub != null) Text(sub, style = Visor.type.label, color = Visor.colors.textSecondary)
                        if (keyLine != null) KeyLine(keyLine)
                    }
                }
            },
        ),
        modifier = modifier,
    ) { (heads, values, tails), constraints ->
        val width = constraints.maxWidth
        val space = line.roundToPx()
        val h = heads.first()
        val v = values.firstOrNull()
        val t = tails.firstOrNull()
        val inline = v != null && h.maxIntrinsicWidth(Constraints.Infinity) + gap.roundToPx() + v.maxIntrinsicWidth(Constraints.Infinity) <= width
        if (v != null && inline) {
            val vp = v.measure(Constraints(maxWidth = width))
            val textWidth = (width - gap.roundToPx() - vp.width).coerceAtLeast(0)
            val hp = h.measure(Constraints(maxWidth = textWidth))
            val tp = t?.measure(Constraints(maxWidth = textWidth))
            val textHeight = hp.height + (tp?.let { space + it.height } ?: 0)
            val height = maxOf(textHeight, vp.height)
            layout(width, height) {
                val top = (height - textHeight) / 2
                hp.place(0, top)
                tp?.place(0, top + hp.height + space)
                vp.place(width - vp.width, (height - vp.height) / 2)
            }
        } else {
            val hp = h.measure(Constraints(maxWidth = width))
            val vp = v?.measure(Constraints(maxWidth = width))
            val tp = t?.measure(Constraints(maxWidth = width))
            val height = hp.height + listOfNotNull(vp, tp).sumOf { space + it.height }
            layout(width, height) {
                var y = 0
                hp.place(0, y)
                y += hp.height
                vp?.let {
                    y += space
                    it.place(0, y)
                    y += it.height
                }
                tp?.place(0, y + space)
            }
        }
    }
}

/** The mark of a choice among several: a white disc with a check when chosen, an empty ring when not. */
@Composable
fun ChoiceMark(chosen: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (chosen) Color.White else Color.Transparent)
            .border(1.5.dp, if (chosen) Color.White else Visor.colors.textTertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) Icon(VisorIcons.Check, null, Modifier.size(16.dp), tint = Color.Black)
    }
}

/** Waiting on the glasses: a short arc turning. */
@Composable
fun Spinner(modifier: Modifier = Modifier) {
    ProgressRing(null, modifier.size(18.dp))
}

/** A switch that only shows: its row takes the tap, so the whole row is the target. */
@Composable
fun VisorSwitch(on: Boolean) {
    val colors = Visor.colors
    Switch(
        checked = on,
        onCheckedChange = null,
        // Its row carries the state for TalkBack (switchRow); the switch itself is only drawn.
        modifier = Modifier.padding(start = 12.dp).clearAndSetSemantics {},
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black, checkedTrackColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.control, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun ToggleRow(
    title: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    changed: Boolean = false,
    sub: String? = null,
    overline: String? = null,
    keyLine: String? = null,
    problem: String? = null,
    dense: Boolean = false,
    busy: Boolean = false,
) {
    val host = LocalHost.current
    RowFrame(
        title, onClick = { host.haptics.toggle(); onChange(!on) }, onLongClick = onLongClick, locked = locked, changed = changed,
        busy = busy, overline = overline, sub = sub, keyLine = keyLine, problem = problem, dense = dense, state = Modifier.switchRow(on),
    ) { VisorSwitch(on) }
}

/** A value against the right edge (or under the name when it is long), and a chevron when a tap opens something. */
@Composable
fun ValueRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    changed: Boolean = false,
    sub: String? = null,
    overline: String? = null,
    keyLine: String? = null,
    problem: String? = null,
    dense: Boolean = false,
    busy: Boolean = false,
    dim: Boolean = false,
) {
    RowFrame(
        title, onClick = onClick, onLongClick = onLongClick, locked = locked, changed = changed, value = value, busy = busy,
        overline = overline, sub = sub, keyLine = keyLine, problem = problem, dense = dense, dim = dim,
    ) {
        if (onClick != null) Icon(VisorIcons.Chevron, null, Modifier.padding(start = 8.dp).size(16.dp), tint = Visor.colors.textTertiary)
    }
}

/** A few short options as pills under the title, the chosen one white. */
@Composable
fun <T> PillsRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onPick: (T) -> Unit,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    changed: Boolean = false,
    sub: String? = null,
    overline: String? = null,
    keyLine: String? = null,
    problem: String? = null,
    dense: Boolean = false,
    busy: Boolean = false,
) {
    val host = LocalHost.current
    val blocked = stringResource(R.string.settings_locked)
    RowFrame(
        title, onClick = null, onLongClick = onLongClick, locked = locked, changed = changed, busy = busy,
        overline = overline, sub = sub, keyLine = keyLine, problem = problem, dense = dense,
        below = {
            Pills(options, selected, on = Visor.colors.control) { picked ->
                if (locked) {
                    host.haptics.tick(0.3f)
                    host.notice.show(blocked)
                } else if (picked != selected) {
                    host.haptics.toggle()
                    onPick(picked)
                }
            }
        },
    )
}

@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T?, on: Color = Visor.colors.raised, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val chosen = value == selected
            val back by animateColorAsState(if (chosen) Color.White else on, tween(180), label = "pill")
            Text(
                label,
                style = Visor.type.label,
                color = if (chosen) Color.Black else Visor.colors.textPrimary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(back).clickable { onPick(value) }.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** A value along a track: the number on the right, the slider under it; written when the finger lifts. */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    words: (Float) -> String,
    onDone: (Float) -> Unit,
    onLongClick: (() -> Unit)? = null,
    locked: Boolean = false,
    changed: Boolean = false,
    overline: String? = null,
    keyLine: String? = null,
    problem: String? = null,
    dense: Boolean = false,
    busy: Boolean = false,
) {
    var dragged by remember(value) { mutableStateOf(value) }
    RowFrame(
        title, onClick = null, onLongClick = onLongClick, locked = locked, changed = changed, busy = busy,
        overline = overline, keyLine = keyLine, problem = problem, dense = dense,
        below = {
            Slider(
                value = dragged.coerceIn(range.start, range.endInclusive),
                onValueChange = { dragged = it },
                onValueChangeFinished = { onDone(dragged) },
                valueRange = range,
                steps = steps,
                enabled = !locked,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Visor.colors.control,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Visor.colors.textSecondary,
                    disabledActiveTrackColor = Visor.colors.textSecondary,
                    disabledInactiveTrackColor = Visor.colors.control,
                ),
            )
        },
    ) {
        Text(words(dragged), style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * A place: an icon on a tile, a name, what it holds now, and the way in. [onCard] for a row inside [Rows]: the card's
 * own margins, and a tile that stands out from the card.
 */
@Composable
fun NavRow(icon: ImageVector?, title: String, summary: String?, onClick: () -> Unit, dim: Boolean = false, trailing: String? = null, onCard: Boolean = false) {
    val colors = Visor.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = if (onCard) 16.dp else SIDE, vertical = if (onCard) 12.dp else 11.dp).alpha(if (dim) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (onCard) colors.control else colors.raised), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = colors.textPrimary)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Visor.type.body, color = colors.textPrimary)
            if (!summary.isNullOrEmpty()) Text(summary, style = Visor.type.label, color = colors.textSecondary, maxLines = lineLimit(1), overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = Visor.type.label, color = colors.textSecondary, modifier = Modifier.padding(start = 12.dp))
        Icon(VisorIcons.Chevron, null, Modifier.padding(start = 8.dp).size(16.dp), tint = colors.textTertiary)
    }
}

/** A white pill: the one kind of button that asks for something. Quiet ones are grey. */
@Composable
fun ActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true) {
    Text(
        text,
        style = Visor.type.label,
        color = if (quiet) Visor.colors.textPrimary else Color.Black,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(if (quiet) Visor.colors.control else Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** The bottom sheet every settings page uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorSheet(onDismiss: () -> Unit, full: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    VisorSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = full), content)

/** The same sheet with its [state] in the caller's hands, to slide it away before it goes. */
@ExperimentalMaterial3Api
@Composable
fun VisorSheet(onDismiss: () -> Unit, state: SheetState, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Visor.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(50)).background(Visor.colors.control)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 20.dp), content = content)
    }
}

@Composable
fun SheetTitle(title: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = SIDE).padding(top = 6.dp, bottom = 16.dp)) {
        Text(title, style = Visor.type.heading, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
        if (sub != null) Text(sub, style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** A list of options, the chosen one checked; picking one closes the sheet. A filter field once the list is long. */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Triple<T, String, String?>>,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    sub: String? = null,
) {
    var query by remember { mutableStateOf("") }
    VisorSheet(onDismiss, full = options.size > 8) {
        SheetTitle(title, sub)
        if (options.size > 12) {
            SearchField(query, { query = it }, stringResource(R.string.settings_filter), Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
        }
        val needle = query.trim().lowercase()
        val shown = if (needle.isEmpty()) options else options.filter { needle in it.second.lowercase() || needle in (it.third ?: "").lowercase() }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(shown.size) { i ->
                val (value, label, detail) = shown[i]
                Row(
                    Modifier.fillMaxWidth().semantics { this.selected = value == selected }.clickable {
                        onPick(value)
                        onDismiss()
                    }.padding(horizontal = SIDE, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Visor.type.body, color = Visor.colors.textPrimary)
                        if (detail != null) Text(detail, style = Visor.type.label, color = Visor.colors.textSecondary)
                    }
                    if (value == selected) Icon(VisorIcons.Check, null, Modifier.padding(start = 12.dp).size(22.dp), tint = Color.White)
                }
            }
        }
    }
}

/** A sheet's text field: a rounded well with the hint inside while it is empty; [focus] takes the keyboard at once. */
@Composable
fun SheetField(
    value: String,
    onValue: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    focus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Visor.colors.raised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = Visor.type.body, color = Visor.colors.textTertiary)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = singleLine,
            textStyle = Visor.type.body.copy(color = Visor.colors.textPrimary),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 0.dp else 140.dp).focusRequester(focusRequester),
        )
    }
}

/** Text to write: one field, and Save. [extra] sits beside Save (Paste, New). */
@Composable
fun TextSheet(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    sub: String? = null,
    hint: String = "",
    singleLine: Boolean = true,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    valid: (String) -> Boolean = { true },
    filter: (String) -> String = { it },
    extra: (@Composable RowScope.(set: (String) -> Unit) -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    VisorSheet(onDismiss, full = !singleLine) {
        SheetTitle(title, sub)
        SheetField(draft, { draft = filter(it) }, hint = hint, singleLine = singleLine, secret = secret, keyboard = keyboard, focus = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = SIDE).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            extra?.invoke(this) { draft = it }
            Spacer(Modifier.weight(1f))
            ActionPill(stringResource(R.string.settings_save), {
                onSave(draft)
                onDismiss()
            }, enabled = valid(draft) && draft != initial)
        }
    }
}

/** The search field: a pill with a glass in it. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, hint: String, modifier: Modifier = Modifier, focus: FocusRequester? = null, onFocus: (Boolean) -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Visor.colors.raised).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VisorIcons.Search, null, Modifier.size(18.dp), tint = Visor.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(hint, style = Visor.type.body, color = Visor.colors.textSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Visor.type.body.copy(color = Visor.colors.textPrimary),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (focus != null) it.focusRequester(focus) else it }
                    .onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Icon(VisorIcons.Close, stringResource(R.string.settings_clear), Modifier.size(18.dp).clip(CircleShape).clickable { onQuery("") }, tint = Visor.colors.textSecondary)
        }
    }
}

/** An outlined tile with a hint, where the next pin would go. */
@Composable
fun HintTile(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Visor.colors.control, RoundedCornerShape(18.dp)).padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text, style = Visor.type.label, color = Visor.colors.textSecondary)
    }
}

/** A row for [Rows], where a lambda alone would read as a block: `if (x) row { ... } else null`. */
fun row(content: @Composable () -> Unit): @Composable () -> Unit = content

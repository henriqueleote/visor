package com.macedonga.visor.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.media.CaptureType
import com.macedonga.visor.core.media.Transfer
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.rememberHaptics
import com.macedonga.visor.ui.rememberTrailingFold
import com.macedonga.visor.ui.asHeading
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.delay

/**
 * The gallery: the newest moment, or the one picked, large on a stage and lit by its own colours; under it, on a
 * sheet that rides up over the stage, everything by day, what is still on the glasses included.
 */
@Composable
fun GalleryScreen(model: GalleryModel = hiltViewModel()) {
    val context = LocalContext.current
    val colors = Visor.colors
    val haptics = rememberHaptics()
    val gate = rememberImportGate()
    val items by model.items.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()
    val inFlight by model.inFlight.collectAsStateWithLifecycle()
    val problem by model.problem.collectAsStateWithLifecycle()
    val overBluetooth by model.overBluetooth.collectAsStateWithLifecycle()
    val transfers by model.transfers.collectAsStateWithLifecycle()

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(items, favoritesOnly) { if (favoritesOnly) items.filter { it.favorite } else items }
    val days = remember(shown) { GalleryModel.days(shown) }
    var stagedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val staged = shown.firstOrNull { it.key == stagedKey } ?: shown.firstOrNull()
    var selection by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selection.isNotEmpty()
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deleted by remember { mutableIntStateOf(0) }
    var deletedAt by remember { mutableLongStateOf(0L) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val progress = rememberProgress(inFlight, transfers, items)

    BackHandler(enabled = selecting && openKey == null) { selection = emptySet() }

    // Places: asked for once, from the sheet, until given or put off.
    val prefs = remember { context.getSharedPreferences("gallery", android.content.Context.MODE_PRIVATE) }
    var placesOk by remember { mutableStateOf(placesAllowed(context)) }
    var placesPutOff by remember { mutableStateOf(prefs.getBoolean("places_put_off", false)) }
    LifecycleResumeEffect(Unit) {
        placesOk = placesAllowed(context)
        onPauseOrDispose { }
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { placesOk = placesAllowed(context) }
    val askPlaces = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        placesOk = placesAllowed(context)
        // The background permission is a second question, and only after the first was granted.
        if (!placesOk && context.allows(android.Manifest.permission.ACCESS_FINE_LOCATION)) askBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    LaunchedEffect(deletedAt) {
        if (deletedAt == 0L) return@LaunchedEffect
        delay(PhoneLibrary.UNDO_MS)
        deleted = 0
    }

    val grid = rememberLazyGridState()
    // Each item's place in the grid: the stage, the sheet's head, then each day's header and its tiles.
    val gridIndex = remember(days) {
        val map = HashMap<String, Int>()
        var i = 1
        for ((n, day) in days.withIndex()) {
            i += if (n == 0) 2 else 1
            for (item in day.items) map[item.key] = i++
        }
        map
    }
    val flat = remember(days) { days.flatMap { it.items } }

    fun delete(chosen: Collection<GalleryItem>) {
        val onPhone = chosen.filter { it.phone != null }
        if (onPhone.isEmpty()) return
        haptics.toggle()
        model.delete(onPhone)
        deleted = onPhone.size
        deletedAt = System.currentTimeMillis()
    }

    fun tap(item: GalleryItem, stageOpen: Boolean) {
        when {
            selecting -> selection = if (item.key in selection) selection - item.key else selection + item.key
            item.onGlassesOnly -> {
                stagedKey = item.key
                if (item.key !in inFlight) {
                    haptics.toggle()
                    gate { model.import(listOf(item)) }
                }
            }
            stageOpen && staged?.key != item.key -> {
                haptics.tick(0.4f)
                stagedKey = item.key
            }
            else -> openKey = item.key
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.canvas)) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val stageHeight = maxWidth * STAGE_RATIO
        val strip = top + BAR
        val fullPx = with(density) { stageHeight.toPx() }
        val stripPx = with(density) { strip.toPx() }
        val fold by rememberTrailingFold(fullPx - stripPx) {
            if (grid.firstVisibleItemIndex == 0) grid.firstVisibleItemScrollOffset.toFloat() else fullPx
        }
        val sheetTop = remember {
            derivedStateOf {
                grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == STAGE }?.let { (it.offset.y + it.size.height).toFloat() } ?: Float.NEGATIVE_INFINITY
            }
        }
        val stageOpen = fold < 0.5f
        // Where the sheet's top edge is: on the stage's own spacer while it shows, else where the fold has the stage.
        val edge = { max(sheetTop.value, fullPx - fold * (fullPx - stripPx)).coerceIn(stripPx, fullPx) }
        val screenLong = with(density) { maxOf(maxWidth, maxHeight).roundToPx() }

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            state = grid,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = sheetShape(edge(), SHEET_RADIUS.toPx())
                }
                .drawBehind { drawRect(colors.surface) }
                .dragToSelect(
                    grid,
                    onStart = { key ->
                        haptics.tick(0.6f)
                        selection = selection + key
                    },
                    onExtend = { from, to, base ->
                        val a = flat.indexOfFirst { it.key == from }
                        val b = flat.indexOfFirst { it.key == to }
                        if (a >= 0 && b >= 0) {
                            val range = flat.subList(minOf(a, b), maxOf(a, b) + 1).map { it.key }
                            val next = base + range
                            if (next != selection) haptics.tick(0.3f)
                            selection = next
                        }
                    },
                    selection = { selection },
                ),
            contentPadding = PaddingValues(bottom = bottom + 112.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            item(key = STAGE, span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.fillMaxWidth().height(stageHeight)) }
            val island: @Composable () -> Unit = {
                Island(
                    // What the problem names stays offered, though the glasses count it delivered.
                    waiting = items.filter { it.onGlassesOnly && (it.capture?.isNew == true || it.key in problem?.handles.orEmpty()) },
                    arriving = inFlight.size,
                    problem = problem,
                    overBluetooth = overBluetooth,
                    askPlaces = !placesOk && !placesPutOff,
                    onPlaces = {
                        if (context.allows(android.Manifest.permission.ACCESS_FINE_LOCATION)) askBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else askPlaces.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    onPlacesLater = {
                        placesPutOff = true
                        prefs.edit().putBoolean("places_put_off", true).apply()
                    },
                    onImport = { haptics.toggle(); gate { model.import(it) } },
                )
            }
            if (days.isEmpty()) {
                item(key = ISLAND, span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.animateItem().padding(top = 16.dp)) { island() } }
                if (favoritesOnly) {
                    item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.gallery_empty_favorites),
                            style = Visor.type.body,
                            color = colors.textSecondary,
                            modifier = Modifier.animateItem().padding(24.dp),
                        )
                    }
                }
            }
            for ((index, day) in days.withIndex()) {
                item(key = "day:${day.date}", span = { GridItemSpan(maxLineSpan) }, contentType = "day") { DayHeader(day, first = index == 0, Modifier.animateItem()) }
                if (index == 0) item(key = ISLAND, span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.animateItem()) { island() } }
                items(day.items, key = { it.key }, contentType = { "tile" }) { item ->
                    Tile(
                        modifier = Modifier.animateItem(fadeInSpec = tween(260), placementSpec = spring(dampingRatio = 0.85f, stiffness = 380f), fadeOutSpec = tween(200)),
                        item, previews,
                        selecting = selecting,
                        selected = item.key in selection,
                        arriving = item.capture?.handle in inFlight,
                        progress = progress[item.capture?.handle],
                        staged = stageOpen && item.key == staged?.key,
                        onTap = { tap(item, stageOpen) },
                        onBounds = { bounds[item.key] = it },
                        onGone = { bounds.remove(item.key) },
                    )
                }
            }
        }

        // The stage, over the sheet: the picked moment and its light, folding away as the sheet rides up.
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = edge().toInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .scrollable(grid, Orientation.Vertical, reverseDirection = true)
                .background(colors.canvas),
        ) {
            Stage(
                staged, previews, edge(), stageHeight, strip,
                progress = progress[staged?.capture?.handle],
                arriving = staged?.capture?.handle in inFlight,
                onOpen = { staged?.let { openKey = it.key } },
                onImport = { staged?.let { tap(it, true) } },
                onBounds = { bounds[STAGE] = it },
            )
            TopBar(
                selecting = selecting,
                count = selection.size,
                favoritesOnly = favoritesOnly,
                onClear = { selection = emptySet() },
                onFavorites = {
                    haptics.toggle()
                    favoritesOnly = !favoritesOnly
                },
            )
        }

        val chosen = shown.filter { it.key in selection }
        AnimatedVisibility(
            visible = selecting && openKey == null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        ) {
            SelectionBar(
                chosen,
                onImport = {
                    val picked = chosen
                    gate { model.import(picked) }
                    selection = emptySet()
                },
                onShare = { model.shareIntent(chosen)?.let(context::startActivity) },
                onFavorite = { on -> haptics.toggle(); model.favorite(chosen, on) },
                onDelete = { delete(chosen); selection = emptySet() },
            )
        }

        openKey?.let { key ->
            Viewer(
                items = shown,
                startKey = key,
                previews = previews,
                progress = { progress[it.capture?.handle] },
                arriving = { it.capture?.handle in inFlight },
                origin = { k -> if (k == STAGE) bounds[STAGE] else if (k == staged?.key && fold < 0.2f) bounds[STAGE] else bounds[k] },
                // From the phone's own index: the glasses need not be near for the details.
                originals = { (it.kept?.handle ?: it.capture?.handle)?.let(model.media.index::originals)?.takeIf(File::isDirectory) },
                onPage = { item ->
                    stagedKey = item.key
                    // Bring the tile into view under the viewer, so closing has somewhere to land.
                    if (bounds[item.key] == null && !(item.key == staged?.key && fold < 0.2f)) gridIndex[item.key]?.let { grid.requestScrollToItem(it) }
                },
                onClose = { openKey = null },
                onFavorite = { haptics.toggle(); model.favorite(listOf(it), !it.favorite) },
                onShare = { model.shareIntent(listOf(it))?.let(context::startActivity) },
                onDelete = { delete(listOf(it)) },
                onImport = { tap(it, false) },
                onEdit = { editKey = it.key },
            )
        }

        editKey?.let { key ->
            val item = items.firstOrNull { it.key == key }
            val kept = item?.kept
            if (item == null || kept == null) {
                LaunchedEffect(Unit) { editKey = null }
                return@let
            }
            Editor(
                kept = kept,
                darkroom = model.media.darkroom,
                originals = model.media.index.originals(kept.handle).takeIf(File::isDirectory),
                shown = Images.cached(Images.pictureKey(item, screenLong)),
                onSaved = {
                    Images.forget(item)
                    model.library.reload()
                    editKey = null
                },
                onCancel = { editKey = null },
            )
        }

        AnimatedVisibility(
            visible = deleted > 0,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (selecting) 96.dp else 20.dp),
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
            exit = fadeOut(tween(160)),
        ) {
            UndoPill(deleted) {
                haptics.toggle()
                model.undoDelete()
                deleted = 0
            }
        }
    }
}

@Composable
private fun TopBar(selecting: Boolean, count: Int, favoritesOnly: Boolean, onClear: () -> Unit, onFavorites: () -> Unit) {
    val colors = Visor.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(BAR).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(selecting, Modifier.weight(1f), label = "bar") { choosing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (choosing) {
                    RoundIcon(VisorIcons.Close, stringResource(R.string.gallery_close), onClear)
                    Text(pluralStringResource(R.plurals.gallery_selected, count, count), style = Visor.type.heading, color = colors.textPrimary, modifier = Modifier.asHeading())
                } else {
                    Text(stringResource(R.string.gallery_title), style = Visor.type.title, color = colors.textPrimary, modifier = Modifier.asHeading().padding(start = 12.dp))
                }
            }
        }
        if (!selecting) {
            val pop = remember { Animatable(1f) }
            LaunchedEffect(favoritesOnly) {
                pop.snapTo(0.8f)
                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
            }
            val wash by animateColorAsState(if (favoritesOnly) colors.control else Color.Transparent, tween(200), label = "wash")
            Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                Crossfade(favoritesOnly, animationSpec = tween(160), label = "heart") { on ->
                    RoundIcon(
                        if (on) VisorIcons.HeartFilled else VisorIcons.Heart,
                        stringResource(R.string.gallery_favorites),
                        onFavorites,
                        background = wash,
                    )
                }
            }
        }
    }
}

/**
 * The picked moment, as large as the stage allows under the bar, lit from behind by its own colours, with when it
 * was under it. As the sheet rides up the picture shrinks towards the bar with the stage, and goes only at the end.
 */
@Composable
private fun Stage(
    item: GalleryItem?,
    previews: Map<String, File>,
    edgePx: Float,
    height: Dp,
    strip: Dp,
    progress: Float?,
    arriving: Boolean,
    onOpen: () -> Unit,
    onImport: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val colors = Visor.colors
    val density = LocalDensity.current
    val picture by rememberPicture(item, previews, STAGE_PX)
    val described = item?.let { spoken(it) }
    // The picture's room at rest: from under the bar to above the caption.
    val region = height - strip - PHOTO_TOP - CAPTION - STAGE_FOOT
    val fullPx = with(density) { height.toPx() }
    val stripPx = with(density) { strip.toPx() }
    val regionPx = with(density) { region.toPx() }
    val restPx = with(density) { (PHOTO_TOP + CAPTION + STAGE_FOOT).toPx() }
    val shownPx = edgePx
    val fold = ((fullPx - shownPx) / (fullPx - stripPx)).coerceIn(0f, 1f)
    // What is left for it now, the caption and the air giving way first.
    val scale = ((shownPx - stripPx - restPx * (1f - fold)) / regionPx).coerceIn(0f, 1f)
    // It fades as it shrinks, gone just as it reaches the bar.
    val photoAlpha = ((scale - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val topPx = with(density) { (strip + PHOTO_TOP).toPx() }
    // Laid out at full height whatever the folding box around it allows: the picture's own scale does the shrinking.
    Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(height)) {
        PhotoLight(
            picture,
            Modifier.fillMaxSize().graphicsLayer { alpha = photoAlpha * (0.4f + 0.6f * scale) },
            level = 0.62f,
            center = Offset(0.5f, (topPx + regionPx * scale / 2f) / fullPx),
            radius = 0.62f * (0.35f + 0.65f * scale),
        )
        if (item == null) {
            Text(
                stringResource(R.string.gallery_empty),
                style = Visor.type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp).graphicsLayer { alpha = 1f - fold },
            )
            return@Box
        }
        Box(
            Modifier
                .padding(top = strip + PHOTO_TOP)
                .fillMaxWidth()
                .height(region)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = photoAlpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .aspectRatio(item.aspect, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                    .clip(RoundedCornerShape(PHOTO_RADIUS))
                    .background(colors.raised)
                    .semantics { described?.let { contentDescription = it } }
                    .clickable(onClick = if (item.onGlassesOnly) onImport else onOpen),
            ) {
                Crossfade(item.key to picture, label = "stage") { (_, image) ->
                    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                if (item.onGlassesOnly) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    Box(Modifier.align(Alignment.Center)) {
                        if (arriving) ProgressRing(progress, Modifier.size(44.dp))
                        else Pill(stringResource(R.string.gallery_import), onImport)
                    }
                }
            }
        }
        Text(
            caption(item),
            style = Visor.type.label,
            color = colors.textSecondary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = strip + PHOTO_TOP + region + 12.dp)
                .graphicsLayer {
                    // It rides up with the picture's bottom edge, and is gone well before the picture is.
                    translationY = -regionPx * (1f - scale)
                    alpha = (1f - fold * 2.5f).coerceIn(0f, 1f)
                },
        )
    }
}

/** One rounded island under the first day's title, a row for each thing worth saying, or nothing. */
@Composable
private fun Island(
    waiting: List<GalleryItem>,
    arriving: Int,
    problem: com.macedonga.visor.core.media.ImportProblem?,
    overBluetooth: Boolean,
    askPlaces: Boolean,
    onPlaces: () -> Unit,
    onPlacesLater: () -> Unit,
    onImport: (List<GalleryItem>) -> Unit,
) {
    val colors = Visor.colors
    val rows = buildList<@Composable () -> Unit> {
        when {
            arriving > 0 -> add {
                IslandRow(
                    if (overBluetooth) stringResource(R.string.import_over_bluetooth) else pluralStringResource(R.plurals.gallery_arriving, arriving, arriving),
                    lead = { ProgressRing(null, Modifier.size(18.dp)) },
                )
            }
            problem != null && waiting.any { it.capture?.handle in problem.handles } -> add {
                IslandRow(
                    importProblemText(problem),
                    lead = { Box(Modifier.size(8.dp).clip(CircleShape).background(colors.signal)) },
                ) { Pill(stringResource(R.string.import_retry), { onImport(waiting) }, small = true) }
            }
            waiting.isNotEmpty() -> add {
                IslandRow(
                    pluralStringResource(R.plurals.gallery_waiting, waiting.size, waiting.size),
                    lead = { Icon(VisorIcons.Glasses, null, Modifier.size(20.dp), tint = colors.textPrimary) },
                ) { Pill(stringResource(R.string.gallery_import_all), { onImport(waiting) }, small = true) }
            }
        }
        if (askPlaces) add {
            IslandRow(stringResource(R.string.gallery_places), lead = { Icon(VisorIcons.Pin, null, Modifier.size(20.dp), tint = colors.textPrimary) }) {
                Text(
                    stringResource(R.string.gallery_not_now),
                    style = Visor.type.label,
                    color = colors.textSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onPlacesLater).padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Pill(stringResource(R.string.gallery_allow), onPlaces, small = true)
            }
        }
    }
    Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.9f, stiffness = 400f))) {
        if (rows.isEmpty()) return@Column
        Column(
            Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(ISLAND_RADIUS))
                .background(colors.raised),
        ) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(colors.hairline))
                row()
            }
        }
    }
}

@Composable
private fun IslandRow(text: String, lead: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { lead() }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Visor.type.label, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
    }
}

@Composable
private fun DayHeader(day: Day, first: Boolean, modifier: Modifier) {
    val colors = Visor.colors
    val context = LocalContext.current
    val photos = day.items.count { it.type != CaptureType.VIDEO }
    val videos = day.items.size - photos
    // Where most of the day was taken.
    val spot = remember(day) {
        day.items.mapNotNull { i -> i.kept?.lat?.let { lat -> i.kept.lon?.let { lon -> lat to lon } } }
            .groupBy { (lat, lon) -> (lat * 100).toLong() to (lon * 100).toLong() }.maxByOrNull { it.value.size }?.value?.first()
    }
    val place by androidx.compose.runtime.produceState(spot?.let { PlaceNames.cached(it.first, it.second) }, spot) {
        if (spot != null && value == null) value = PlaceNames.of(context, spot.first, spot.second)
    }
    // The first day opens the sheet; the others follow the tiles above them closely.
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (first) 26.dp else 14.dp, bottom = 12.dp)) {
        Text(dayTitle(day.date), style = Visor.type.heading, color = colors.textPrimary, modifier = Modifier.asHeading())
        Text(
            listOfNotNull(
                place,
                photos.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.home_photos, it, it) },
                videos.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.home_videos, it, it) },
            ).joinToString(" · "),
            style = Visor.type.label,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * One square of the grid. On the glasses only: the glasses' small preview under a veil, with their mark. A video
 * says how long it runs; a live photo and a favourite carry their marks. Choosing, a picked tile draws in.
 */
@Composable
private fun Tile(
    modifier: Modifier,
    item: GalleryItem,
    previews: Map<String, File>,
    selecting: Boolean,
    selected: Boolean,
    arriving: Boolean,
    progress: Float?,
    staged: Boolean,
    onTap: () -> Unit,
    onBounds: (Rect) -> Unit,
    onGone: () -> Unit,
) {
    val colors = Visor.colors
    val thumb by rememberThumb(item, previews)
    val inset by animateFloatAsState(if (selected) 0.86f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "inset")
    val described = spoken(item)
    DisposableEffect(item.key) { onDispose(onGone) }
    Box(
        modifier
            .aspectRatio(1f)
            .background(colors.raised)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .semantics {
                contentDescription = described
                if (selecting) this.selected = selected
            }
            .clickable(onClick = onTap),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = inset
                scaleY = inset
                clip = inset < 1f
                shape = RoundedCornerShape(10.dp)
            },
        ) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (item.onGlassesOnly) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            if (item.type == CaptureType.VIDEO || item.favorite) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
            }
            val mark = Modifier.padding(6.dp).size(15.dp)
            when {
                item.onGlassesOnly -> Icon(VisorIcons.Glasses, null, mark.align(Alignment.BottomStart), tint = Color.White)
                item.favorite -> Icon(VisorIcons.HeartFilled, null, mark.align(Alignment.BottomStart), tint = Color.White)
            }
            if (item.type == CaptureType.LIVE) Icon(VisorIcons.LivePhoto, null, mark.align(Alignment.TopEnd), tint = Color.White.copy(alpha = 0.9f))
            if (item.type == CaptureType.VIDEO) {
                item.phone?.durationMs?.takeIf { it > 0 }?.let {
                    Text(duration(it), style = Visor.type.label, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 7.dp, vertical = 5.dp))
                }
            }
            if (arriving) ProgressRing(progress, Modifier.align(Alignment.Center).size(26.dp))
        }
        if (staged && !selecting) Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.9f)))
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(VisorIcons.Check, null, Modifier.size(15.dp), tint = Color.Black)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    chosen: List<GalleryItem>,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Visor.colors
    val onPhone = chosen.filter { it.phone != null }
    val allFavorite = onPhone.isNotEmpty() && onPhone.all { it.favorite }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(colors.raised).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chosen.any { it.onGlassesOnly }) Pill(stringResource(R.string.gallery_import), onImport, small = true, modifier = Modifier.padding(horizontal = 4.dp))
        if (onPhone.isNotEmpty()) {
            RoundIcon(VisorIcons.Share, stringResource(R.string.gallery_share), onShare, size = 48.dp)
            RoundIcon(if (allFavorite) VisorIcons.HeartFilled else VisorIcons.Heart, stringResource(R.string.gallery_favorite), { onFavorite(!allFavorite) }, size = 48.dp)
            RoundIcon(VisorIcons.Trash, stringResource(R.string.gallery_delete), onDelete, size = 48.dp)
        }
    }
}

@Composable
private fun UndoPill(count: Int, onUndo: () -> Unit) {
    val colors = Visor.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(colors.raised).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pluralStringResource(R.plurals.gallery_deleted, count, count), style = Visor.type.label, color = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.gallery_undo),
            style = Visor.type.label,
            color = colors.textPrimary,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onUndo).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** A white pill: the one kind of button that asks for something. */
@Composable
fun Pill(text: String, onClick: () -> Unit, small: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Visor.type.label,
        color = Color.Black,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 8.dp else 12.dp),
    )
}

@Composable
fun RoundIcon(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    background: Color = Color.Transparent,
    tint: Color = Visor.colors.textPrimary,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(22.dp), tint = tint)
    }
}

/** How far an import has come, or a turning arc while it has not started moving. */
@Composable
fun ProgressRing(progress: Float?, modifier: Modifier = Modifier, color: Color = Color.White) {
    val spin = rememberInfiniteTransition(label = "spin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "turn")
    val shown by animateFloatAsState(progress ?: 0f, tween(300), label = "progress")
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        drawCircle(color.copy(alpha = 0.25f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        if (progress == null || progress <= 0f) {
            drawArc(color, turn, 80f, false, topLeft = Offset(stroke, stroke), size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke, size.height - 2 * stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            drawArc(color, -90f, 360f * shown, false, topLeft = Offset(stroke, stroke), size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke, size.height - 2 * stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * Each capture's import as a fraction, from its assets on the wire, never going back when a finished asset leaves
 * the list of transfers. Null for what is not being imported.
 */
@Composable
private fun rememberProgress(inFlight: Set<String>, transfers: List<Transfer>, items: List<GalleryItem>): Map<String, Float?> {
    val best = remember { HashMap<String, Float>() }
    // Assets seen on the wire: once one leaves the list of transfers, it has arrived.
    val seen = remember { HashMap<String, MutableSet<String>>() }
    val captures = remember(items) { items.mapNotNull { it.capture }.associateBy { it.handle } }
    return remember(inFlight, transfers, captures) {
        best.keys.retainAll(inFlight)
        seen.keys.retainAll(inFlight)
        inFlight.associateWith { handle ->
            val capture = captures[handle] ?: return@associateWith null
            val total = capture.wanted.sumOf { it.size }.coerceAtLeast(1)
            val ids = capture.wanted.map { it.id }.toSet()
            val moving = transfers.filter { it.uuid in ids }
            val mine = seen.getOrPut(handle) { HashSet() }.apply { addAll(moving.map { it.uuid }) }
            if (mine.isEmpty()) return@associateWith null
            val done = capture.wanted.filter { a -> a.id in mine && moving.none { it.uuid == a.id } }.sumOf { it.size }
            val now = ((done + moving.sumOf { it.received }).toFloat() / total).coerceIn(0f, 1f)
            maxOf(now, best[handle] ?: 0f).also { best[handle] = it }
        }
    }
}

/** Long-press a tile and drag: everything between it and the finger is chosen, on top of what already was. */
private fun Modifier.dragToSelect(
    grid: LazyGridState,
    onStart: (String) -> Unit,
    onExtend: (from: String, to: String, base: Set<String>) -> Unit,
    selection: () -> Set<String>,
): Modifier = pointerInput(grid) {
    var from: String? = null
    var base = emptySet<String>()
    detectDragGesturesAfterLongPress(
        onDragStart = { at ->
            grid.tileAt(at)?.let { key ->
                from = key
                base = selection()
                onStart(key)
            }
        },
        onDrag = { change, _ ->
            val start = from ?: return@detectDragGesturesAfterLongPress
            grid.tileAt(change.position)?.let { onExtend(start, it, base) }
        },
        onDragEnd = { from = null },
        onDragCancel = { from = null },
    )
}

private fun LazyGridState.tileAt(at: Offset): String? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "tile" && IntRect(it.offset, it.size).contains(at.round()) }?.key as? String

private fun sheetShape(top: Float, radius: Float) = GenericShape { size, _ ->
    addRoundRect(
        RoundRect(0f, top, size.width, size.height + radius, topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius)),
    )
}

private const val STAGE = STAGE_KEY
private const val ISLAND = "island"
private const val COLUMNS = 4
private val GAP = 2.dp
private val BAR = 56.dp
/** The stage is this many widths tall. */
private const val STAGE_RATIO = 1.3f
private const val STAGE_PX = 1600
private val SHEET_RADIUS = 28.dp
private val PHOTO_RADIUS = 14.dp
/** Air between the bar and the stage's picture, its caption, and under them. */
private val PHOTO_TOP = 28.dp
private val CAPTION = 29.dp
private val STAGE_FOOT = 24.dp
private val ISLAND_RADIUS = 18.dp

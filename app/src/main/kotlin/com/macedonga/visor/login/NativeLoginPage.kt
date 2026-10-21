package com.macedonga.visor.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.macedonga.visor.core.identity.bloks.BloksHost
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.identity.bloks.Action
import com.macedonga.visor.core.identity.bloks.BloksSession
import com.macedonga.visor.core.identity.bloks.Event
import com.macedonga.visor.core.identity.bloks.Field
import com.macedonga.visor.core.identity.bloks.Flat
import com.macedonga.visor.core.identity.bloks.Heading
import com.macedonga.visor.core.identity.bloks.Paragraph
import com.macedonga.visor.core.identity.bloks.Screen
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.asHeading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class NativeLoginViewModel @Inject constructor(val login: MetaLogin, @ApplicationContext private val context: Context) : ViewModel() {
    private var session: BloksSession? = null
    private val _state = MutableStateFlow<BloksSession.State?>(null)
    val state: StateFlow<BloksSession.State?> = _state

    /** What a screen asked of the window (no screenshots, screen kept on) and a date it asked for. */
    val secure = MutableStateFlow(false)
    val keepOn = MutableStateFlow(false)
    val dateAsk = MutableStateFlow<DateAsk?>(null)

    class DateAsk(val initialSeconds: Long?, val done: (Long?) -> Unit)

    private val host = object : BloksHost {
        override fun openUrl(url: String) {
            runCatching {
                val tab = CustomTabsIntent.Builder().build()
                tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tab.launchUrl(context, Uri.parse(url))
            }
        }

        override fun haptic() {
            runCatching {
                val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        }

        override fun shareText(text: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        override fun dial(number: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        override fun copy(text: String) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
        }

        override fun secure(on: Boolean) {
            secure.value = on
        }

        override fun keepScreenOn(on: Boolean) {
            keepOn.value = on
        }

        override fun pickDate(initialSeconds: Long?, withTime: Boolean, done: (Long?) -> Unit) {
            dateAsk.value = DateAsk(initialSeconds, done)
        }

        override val apiLevel: Int get() = Build.VERSION.SDK_INT
        override val fontScale: Float get() = context.resources.configuration.fontScale
        override val densityDpi: Int get() = context.resources.displayMetrics.densityDpi
        override val screenReader: Boolean
            get() = context.getSystemService(AccessibilityManager::class.java)?.let { it.isEnabled && it.isTouchExplorationEnabled } == true
        override val reduceMotion: Boolean
            get() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    /** Starts once per page; the session outlives recomposition and rotation with this model. */
    fun start(onDone: () -> Unit): BloksSession? {
        session?.let { return it }
        val request = login.pending ?: return null
        return login.nativeSession(darkMode = true, host = host).also { s ->
            session = s
            s.onState = { _state.value = it }
            s.onOutcome = { outcome ->
                login.finishNative(outcome)
                Handler(Looper.getMainLooper()).post(onDone)
            }
            s.start(request.url)
        }
    }

    override fun onCleared() {
        session?.close()
    }
}

/**
 * Meta's login as the Meta AI app runs it, each of Meta's screens reduced to what it asks (a title, its lines, its
 * fields, its buttons) and drawn like the rest of the setup.
 */
@Composable
fun NativeLoginPage(onDone: () -> Unit, model: NativeLoginViewModel = hiltViewModel()) {
    val session = remember { model.start(onDone) }
    if (session == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val state by produceState(model.state.value) { model.state.collect { value = it } }
    val s = state
    WindowFlags(model)
    DateAsked(model)
    val screen = remember(s?.version) { Flat.of(s?.ui) }
    val back = { screen.back?.let(session::run) ?: session.back() }
    BackHandler(onBack = back)
    Column(
        Modifier
            .fillMaxSize()
            .background(Visor.colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            RoundIcon(VisorIcons.ChevronLeft, stringResource(R.string.settings_back), back)
        }
        if (s == null || screen.loading || screen.items.isEmpty()) {
            // Nothing to draw and nothing on its way is a step Visor cannot show: say so instead of spinning forever.
            var stuck by remember(s?.version) { mutableStateOf(false) }
            LaunchedEffect(s?.version, s?.busy) {
                if (s != null && !s.busy) {
                    delay(STUCK_MS)
                    stuck = true
                    session.stuck()
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (stuck) {
                    Text(stringResource(R.string.native_login_stuck), style = Visor.type.body, color = Visor.colors.textSecondary, textAlign = TextAlign.Center)
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Visor.colors.textSecondary, strokeWidth = 2.dp)
                }
            }
        } else {
            Content(screen, s, session)
        }
    }
    s?.dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { session.dismissDialog() },
            containerColor = Visor.colors.raised,
            titleContentColor = Visor.colors.textPrimary,
            textContentColor = Visor.colors.textSecondary,
            title = d.title?.let { { Text(it, style = Visor.type.title) } },
            text = d.message?.let { { Text(it, style = Visor.type.body) } },
            confirmButton = {
                Row {
                    d.buttons.forEach { (label, handler) ->
                        TextButton(onClick = {
                            session.dismissDialog()
                            handler?.let { session.invoke(it, listOf(null, null)) }
                        }) { Text(label, style = Visor.type.body, color = Visor.colors.textPrimary) }
                    }
                }
            },
        )
    }
}

@Composable
private fun Content(screen: Screen, s: BloksSession.State, session: BloksSession) {
    val primary = screen.items.filterIsInstance<Action>().firstOrNull { it.primary }
    val others = screen.items.filterIsInstance<Action>().filter { it !== primary }
    val fields = screen.items.filterIsInstance<Field>()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            screen.items.forEach { item ->
                when (item) {
                    is Heading -> Text(item.text, style = Visor.type.title, color = Visor.colors.textPrimary, modifier = Modifier.asHeading().padding(bottom = 12.dp))
                    is Paragraph -> ParagraphText(item, session)
                    is Field -> FieldWell(item, session, index = fields.indexOf(item), depth = s.depth) {
                        primary?.takeIf { it.enabled }?.let { session.run(it.event) }
                    }
                    is Action -> {}
                }
            }
            // The screen's other ways on (another code, a password instead): quiet chips under what it asks.
            if (others.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { action -> Chip(action.label, enabled = action.enabled && !s.busy) { session.run(action.event) } }
                }
            }
        }
        Toast(s)
        if (primary != null) {
            Pill(primary.label, enabled = primary.enabled && !s.busy, busy = s.busy) { session.run(primary.event) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ParagraphText(item: Paragraph, session: BloksSession) {
    val text = buildAnnotatedString {
        item.spans.forEach { span ->
            val click = span.onClick
            if (click != null) {
                withLink(LinkAnnotation.Clickable("bloks") { session.run(click) }) {
                    withStyle(SpanStyle(color = Visor.colors.textPrimary, textDecoration = TextDecoration.Underline)) { append(span.text) }
                }
            } else {
                append(span.text)
            }
        }
    }
    Text(
        text,
        style = Visor.type.body,
        color = if (item.error) Visor.colors.signal else Visor.colors.textSecondary,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** A screen's window wishes: no screenshots while it shows a secret, the screen kept on while it waits. */
@Composable
private fun WindowFlags(model: NativeLoginViewModel) {
    val secure by model.secure.collectAsState()
    val keepOn by model.keepOn.collectAsState()
    val window = (LocalActivity.current as? ComponentActivity)?.window ?: return
    DisposableEffect(secure, keepOn) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** A date a screen asks for (a birthday): the platform's picker, answered in unix seconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAsked(model: NativeLoginViewModel) {
    val ask by model.dateAsk.collectAsState()
    val current = ask ?: return
    val picker = rememberDatePickerState(initialSelectedDateMillis = current.initialSeconds?.times(1000))
    val answer = { seconds: Long? ->
        model.dateAsk.value = null
        current.done(seconds)
    }
    DatePickerDialog(
        onDismissRequest = { answer(null) },
        confirmButton = { TextButton(onClick = { answer(picker.selectedDateMillis?.div(1000)) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = { answer(null) }) { Text(stringResource(R.string.language_cancel)) } },
    ) { DatePicker(picker) }
}

/** Meta's toast ("code sent"): a quiet line above the button for as long as Meta asks. */
@Composable
private fun Toast(s: BloksSession.State) {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.toastCount) {
        if (s.toastCount == 0 || s.toast == null) return@LaunchedEffect
        shown = s.toast
        delay(s.toastMillis.coerceIn(1500L, 8000L))
        shown = null
    }
    AnimatedVisibility(shown != null, enter = fadeIn(), exit = fadeOut()) {
        Text(
            shown.orEmpty(),
            style = Visor.type.label,
            color = Visor.colors.textPrimary,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Visor.colors.raised)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Chip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = Visor.type.label,
        color = Visor.colors.textPrimary,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(50))
            .background(Visor.colors.raised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** A field in the setup's look: a rounded well, its name inside while empty, the text in white as it is typed. */
@Composable
private fun FieldWell(item: Field, session: BloksSession, index: Int, depth: Int, onGo: () -> Unit) {
    val input = item.input
    // Meta redraws the screen on every keystroke and may hand a new node each time: the field is its place on the
    // screen, and it keeps its own text and cursor.
    var value by remember(depth, index) { mutableStateOf(TextFieldValue(input.text, TextRange(input.text.length))) }
    val text = value.text
    val requester = remember(depth, index) { FocusRequester() }
    if (index == 0) LaunchedEffect(depth) { runCatching { requester.requestFocus() } }
    val code = input.keyboard == "passcode"
    val label = item.label.ifEmpty { if (code) stringResource(R.string.login_code) else input.hint }
    val style = if (code) {
        TextStyle(color = Visor.colors.textPrimary, fontSize = 28.sp, letterSpacing = 10.sp, textAlign = TextAlign.Center)
    } else {
        Visor.type.body.copy(color = Visor.colors.textPrimary)
    }
    Box(
        Modifier
            .padding(top = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Visor.colors.raised)
            .padding(horizontal = 16.dp, vertical = if (code) 18.dp else 16.dp),
        contentAlignment = if (code) Alignment.Center else Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text(label, style = Visor.type.body, color = Visor.colors.textTertiary)
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val clipped = if (input.maxLength > 0 && next.text.length > input.maxLength) {
                    next.copy(text = next.text.take(input.maxLength), selection = TextRange(input.maxLength))
                } else {
                    next
                }
                val changed = clipped.text != value.text
                value = clipped
                if (changed) {
                    session.setText(input.node, clipped.text)
                    input.onChange?.let(session::run)
                }
            },
            enabled = input.enabled,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Color.White),
            // Meta masks a code field; here the digits show, as they do in the Meta app's own boxes.
            visualTransformation = if (input.secret && !code) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (input.keyboard) {
                    "email" -> KeyboardType.Email
                    "password" -> KeyboardType.Password
                    "passcode", "number", "amount" -> KeyboardType.Number
                    "phone" -> KeyboardType.Phone
                    else -> KeyboardType.Text
                },
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onAny = { input.onSubmit?.let(session::run) ?: onGo() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged { f -> (if (f.isFocused) input.onFocus else input.onBlur)?.let(session::run) },
        )
    }
}

/** The setup's main button: a wide white pill. */
@Composable
private fun Pill(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled || busy) 1f else 0.35f)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Text(text, style = Visor.type.body, color = Color.Black, textAlign = TextAlign.Center)
        }
    }
}

private fun BloksSession.run(event: Event) = invoke(event.handler, event.args)

// How long a screen may show nothing, with nothing on its way, before the page says it cannot show it.
private const val STUCK_MS = 20_000L

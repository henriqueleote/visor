package com.macedonga.visor.features

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.macedonga.visor.HearingTestKey
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.CorrectionTone
import com.macedonga.visor.core.assistant.Ear
import com.macedonga.visor.core.assistant.Hearing
import com.macedonga.visor.core.assistant.HearingMode
import com.macedonga.visor.core.assistant.HearingRefusal
import com.macedonga.visor.core.assistant.HearingRun
import com.macedonga.visor.core.assistant.HearingState
import com.macedonga.visor.core.assistant.Thresholds
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.gallery.RoundIcon
import com.macedonga.visor.settings.ActionPill
import com.macedonga.visor.settings.BAR
import com.macedonga.visor.settings.Card
import com.macedonga.visor.settings.Footnote
import com.macedonga.visor.settings.LocalBack
import com.macedonga.visor.settings.LocalHost
import com.macedonga.visor.settings.PillsRow
import com.macedonga.visor.settings.Rows
import com.macedonga.visor.settings.SectionLabel
import com.macedonga.visor.settings.SettingsHost
import com.macedonga.visor.settings.SettingsPage
import com.macedonga.visor.settings.SliderRow
import com.macedonga.visor.settings.ToggleRow
import com.macedonga.visor.settings.TopBar
import com.macedonga.visor.settings.ValueRow
import com.macedonga.visor.settings.row
import com.macedonga.visor.setup.BigPill
import com.macedonga.visor.setup.QuietButton
import com.macedonga.visor.stage.LightLook
import com.macedonga.visor.stage.LocalStage
import com.macedonga.visor.stage.Mood
import com.macedonga.visor.stage.STAGE_ASPECT
import com.macedonga.visor.stage.StageCue
import com.macedonga.visor.stage.StoryStage
import com.macedonga.visor.stage.lookOf
import com.macedonga.visor.stage.nod
import com.macedonga.visor.stage.shake
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.asHeading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hearing correction: the glasses as a hearing aid, fitted by a hearing test on the glasses. On top, where it stands
 * and the one button that changes that; then its sound once fitted; the test; and the switch that lets these glasses
 * run it at all.
 */
@Composable
fun HearingCorrectionScreen(open: (NavKey) -> Unit, model: FeaturesModel = hiltViewModel()) {
    val state by model.features.state.collectAsStateWithLifecycle()
    val hearing by model.hearing.state.collectAsStateWithLifecycle()
    val tested by model.features.tested.collectAsStateWithLifecycle()
    val feature = Feature.HEARING_CORRECTION
    val standing = state.standings[feature] ?: Standing.UNKNOWN
    val allowed = standing == Standing.ON
    // The glasses' word when they give it, else what the last test here left.
    val fitted = hearing.fitted ?: tested

    SettingsPage(stringResource(R.string.feature_hc), stringResource(R.string.hc_lede)) {
        item(key = "now") { CorrectionNow(hearing, allowed, fitted, feature in state.restart, model.hearing, model.features::restarted) }
        if (fitted) {
            item(key = "sound") {
                val host = LocalHost.current
                val tone = hearing.correction ?: CorrectionTone()
                val locked = !host.ready || !allowed
                val range = Hearing.MIN_TONE.toFloat()..Hearing.MAX_TONE.toFloat()
                val steps = Hearing.MAX_TONE - Hearing.MIN_TONE - 1
                val signed: (Float) -> String = { v -> v.roundToInt().let { if (it > 0) "+$it" else "$it" } }
                val left = stringResource(R.string.hc_balance_left)
                val right = stringResource(R.string.hc_balance_right)
                val centre = stringResource(R.string.hc_balance_centre)
                Column {
                    SectionLabel(stringResource(R.string.cf_sound))
                    Rows(
                        row {
                            SliderRow(stringResource(R.string.hc_amplification), tone.volume.toFloat(), range, steps, signed, { model.hearing.tuneCorrection(tone.copy(volume = it.roundToInt())) }, locked = locked)
                        },
                        row {
                            SliderRow(stringResource(R.string.hc_sharpness), tone.sharpness.toFloat(), range, steps, signed, { model.hearing.tuneCorrection(tone.copy(sharpness = it.roundToInt())) }, locked = locked)
                        },
                        row {
                            SliderRow(
                                stringResource(R.string.hc_balance), tone.balance.toFloat(), range, steps,
                                { v -> v.roundToInt().let { if (it < 0) "$left ${-it}" else if (it > 0) "$right $it" else centre } },
                                { model.hearing.tuneCorrection(tone.copy(balance = it.roundToInt())) }, locked = locked,
                            )
                        },
                        row {
                            PillsRow(
                                stringResource(R.string.cf_style),
                                listOf(false to stringResource(R.string.hc_style_moderate), true to stringResource(R.string.hc_style_extra)),
                                selected = tone.noisy,
                                onPick = { model.hearing.tuneCorrection(tone.copy(noisy = it)) },
                                locked = locked,
                            )
                        },
                    )
                    Footnote(stringResource(R.string.hc_sound_note))
                }
            }
        }
        item(key = "test") {
            val host = LocalHost.current
            Column {
                SectionLabel(stringResource(R.string.hct_title))
                Rows(
                    row {
                        ValueRow(
                            stringResource(if (fitted) R.string.hc_test_again else R.string.hc_test_take), null,
                            onClick = { open(HearingTestKey) },
                            locked = !host.ready || !allowed,
                        )
                    },
                )
            }
        }
        item(key = "allow") {
            val host = LocalHost.current
            Column {
                SectionLabel(stringResource(R.string.cf_allow_section))
                Rows(
                    row {
                        ToggleRow(
                            stringResource(R.string.hc_allow), allowed,
                            onChange = { model.features.set(feature, it) },
                            sub = stringResource(if (allowed) R.string.cf_allow_on else R.string.hc_allow_off),
                            locked = !host.ready, busy = standing == Standing.SENDING,
                            problem = if (standing == Standing.REFUSED) stringResource(R.string.features_refused) else null,
                        )
                    },
                )
            }
        }
    }
}

/** Where hearing correction stands, in a word and a line, with the one button that changes it once there is a profile. */
@Composable
private fun CorrectionNow(hearing: HearingState, allowed: Boolean, fitted: Boolean, restart: Boolean, control: Hearing, restarted: () -> Unit) {
    val host = LocalHost.current
    val connectFirst = stringResource(R.string.settings_locked)
    val correcting = hearing.mode == HearingMode.CORRECTION || hearing.mode == HearingMode.CORRECTION_NARROW
    val run = if (correcting) hearing.run else HearingRun.OFF
    val paused = run == HearingRun.PAUSED || run == HearingRun.PAUSED_BY_USER
    val (title, sub) = when {
        !allowed -> R.string.cf_now_locked to R.string.cf_now_locked_sub
        restart -> R.string.features_restart_title to R.string.features_restart_line
        !fitted -> R.string.hc_now_untested to R.string.hc_now_untested_sub
        run == HearingRun.ON -> R.string.cf_now_on to R.string.hc_now_on_sub
        paused -> R.string.cf_now_paused to R.string.cf_paused
        // USECASE_ENABLE_FAILURE: the glasses' audio chip took the left ear's profile and refused the right's.
        hearing.refusal == HearingRefusal.ENABLE_FAILED -> R.string.cf_now_off to R.string.hc_refused_chip
        hearing.refusal != null -> R.string.cf_now_off to R.string.cf_refused
        else -> R.string.cf_now_off to R.string.hc_now_off_sub
    }
    val warn = hearing.refusal != null && run != HearingRun.ON && allowed && fitted && !restart
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FeatureTile(VisorIcons.HearingAid, lit = run == HearingRun.ON, size = 52)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = Visor.type.heading, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
                Text(stringResource(sub), style = Visor.type.label, color = if (warn) Visor.colors.signal else Visor.colors.textSecondary)
            }
            if (allowed && restart) {
                Spacer(Modifier.width(12.dp))
                ActionPill(stringResource(R.string.features_restart_done), restarted)
            } else if (fitted) {
                Spacer(Modifier.width(12.dp))
                val (label, act) = when {
                    run == HearingRun.ON -> R.string.cf_stop to control::stop
                    paused -> R.string.cf_resume to control::resume
                    else -> R.string.cf_start_now to { control.startCorrection() }
                }
                ActionPill(
                    stringResource(label),
                    {
                        if (!host.ready) host.notice.show(connectFirst)
                        else {
                            host.haptics.toggle()
                            act()
                        }
                    },
                    quiet = run == HearingRun.ON,
                    enabled = allowed,
                )
            }
        }
    }
}

/** Where the hearing test is. */
sealed interface TestStep {
    data object Who : TestStep

    /** The answer means hearing correction is not for this person. */
    data class NotFor(val why: Int) : TestStep
    data object Ready : TestStep
    data object Starting : TestStep
    data class Tone(val index: Int, val level: Int) : TestStep
    data object Fitting : TestStep
    data object Done : TestStep

    /** The profile is made, but the glasses did not start hearing correction with it. */
    data class Saved(val why: Int) : TestStep
    data class Failed(val why: Int) : TestStep
}

/**
 * The Meta app's self-fitting, step by step: the hearing app in its setup mode, four tones an ear (1, 2 and 4 kHz,
 * then 1 kHz again, which is the one that counts), each from 40 dB HL in 5 dB steps until barely heard, then the
 * thresholds to the glasses, which make the profile and switch to hearing correction.
 */
@HiltViewModel
class HearingTestModel @Inject constructor(private val hearing: Hearing, private val features: Features) : ViewModel() {
    private val _step = MutableStateFlow<TestStep>(TestStep.Who)
    val step: StateFlow<TestStep> = _step.asStateFlow()

    private val levels = IntArray(TONES.size) { START_DBHL }
    private var testing = false

    // One tone request at a time, 600 ms after the last answer, as the Meta app sends them; a newer ask replaces a
    // waiting one. Between tones the old one is stopped first, or the glasses refuse the next.
    private val tones = Mutex()
    private var asked = 0
    private var visits = 0
    private var moving: Job? = null

    fun answer(rating: Int) {
        _step.value = when (rating) {
            RATING_MILD -> TestStep.Ready
            RATING_NONE -> TestStep.NotFor(R.string.hct_not_none)
            RATING_SEVERE -> TestStep.NotFor(R.string.hct_not_severe)
            else -> TestStep.NotFor(R.string.hct_not_implant)
        }
    }

    fun begin() {
        _step.value = TestStep.Starting
        val visit = visits
        viewModelScope.launch {
            hearing.startCorrection(test = true)
            val running = hearing.awaitRunning(HearingMode.CORRECTION_SETUP, START_MS)
            // Gone while the glasses got ready: the setup they started is stopped again.
            if (visit != visits) {
                if (running) hearing.stop()
                return@launch
            }
            if (!running) {
                _step.value = TestStep.Failed(R.string.hct_failed_start)
                return@launch
            }
            testing = true
            levels.fill(START_DBHL)
            show(0)
        }
    }

    /** Louder or quieter by one step, and play it again. */
    fun nudge(up: Boolean) {
        val s = _step.value as? TestStep.Tone ?: return
        if (moving?.isActive == true) return
        val level = (s.level + if (up) STEP_DBHL else -STEP_DBHL).coerceIn(Hearing.MIN_DBHL, Hearing.MAX_DBHL)
        if (level != s.level) show(s.index, level)
    }

    /** Can't hear it: the loudest, played again. */
    fun loudest() {
        val s = _step.value as? TestStep.Tone ?: return
        if (moving?.isActive == true || s.level == Hearing.MAX_DBHL) return
        show(s.index, Hearing.MAX_DBHL)
    }

    fun next() {
        val s = _step.value as? TestStep.Tone ?: return
        if (moving?.isActive == true) return
        levels[s.index] = s.level
        moving = viewModelScope.launch {
            silence(s.index)
            if (s.index + 1 < TONES.size) show(s.index + 1) else fit()
        }
    }

    fun retry() {
        _step.value = TestStep.Ready
    }

    private fun show(index: Int, level: Int = START_DBHL) {
        if (!testing) return
        _step.value = TestStep.Tone(index, level)
        val ask = ++asked
        viewModelScope.launch {
            tones.withLock {
                if (ask != asked) return@withLock
                val (ear, hz) = TONES[index]
                if (hearing.playTone(ear, hz, level)) delay(BETWEEN_MS) else _step.value = TestStep.Failed(R.string.hct_failed_tone)
            }
        }
    }

    /** Stop [index]'s tone, waited for; a refusal to stop is no failure. */
    private suspend fun silence(index: Int) {
        asked++
        tones.withLock {
            val (ear, hz) = TONES[index]
            if (hearing.playTone(ear, hz, levels[index], on = false)) delay(BETWEEN_MS)
        }
    }

    private fun fit() {
        _step.value = TestStep.Fitting
        viewModelScope.launch {
            // Tone 1 is practice; tone 4, 1 kHz again, is the one sent.
            fun ear(from: Int) = Thresholds(at1k = levels[from + 3], at2k = levels[from + 1], at4k = levels[from + 2])
            if (!hearing.fit(ear(0), ear(4))) {
                _step.value = TestStep.Failed(R.string.hct_failed_fit)
                return@launch
            }
            testing = false
            features.hearingTested()
            val now = hearing.state.value
            val on = (now.mode == HearingMode.CORRECTION && now.run == HearingRun.ON) || run {
                hearing.stop()
                hearing.awaitStopped()
                hearing.startCorrection()
                hearing.awaitRunning(HearingMode.CORRECTION, START_MS)
            }
            _step.value = if (on) TestStep.Done
            else TestStep.Saved(if (hearing.state.value.refusal == HearingRefusal.ENABLE_FAILED) R.string.hc_refused_chip else R.string.cf_refused)
        }
    }

    /** Each visit starts from the first question: the model outlives the screen. */
    fun enter() {
        visits++
        asked++
        levels.fill(START_DBHL)
        _step.value = TestStep.Who
    }

    /** Leaving mid-test stops the hearing app, as the Meta app does. */
    fun leave() {
        visits++
        asked++
        if (testing) {
            testing = false
            hearing.stop()
        }
    }

    override fun onCleared() = leave()

    companion object {
        const val RATING_NONE = 0
        const val RATING_MILD = 1
        const val RATING_SEVERE = 2
        const val RATING_IMPLANT = 3
        const val TONES_PER_EAR = 4
        private const val START_DBHL = 40
        private const val STEP_DBHL = 5
        private const val BETWEEN_MS = 600L
        private const val START_MS = 10_000L
        private val TONES = listOf(Ear.LEFT, Ear.RIGHT).flatMap { ear -> listOf(1000, 2000, 4000, 1000).map { ear to it } }
    }
}


/**
 * The hearing test told as the setup tells its story: the pair turned to the ear under test with the tone leaving that
 * temple, swaying while the glasses get ready or make the profile, a flash and a nod when it is made, a shake when not.
 */
@Composable
fun HearingTestScreen(model: HearingTestModel = hiltViewModel()) = SettingsHost(seeThrough = true) {
    val step by model.step.collectAsStateWithLifecycle()
    val stage = LocalStage.current
    val host = LocalHost.current
    val colors = Visor.colors
    DisposableEffect(model) {
        model.enter()
        onDispose { model.leave() }
    }

    val cue = remember { StageCue() }
    val tone = step as? TestStep.Tone
    val ear = tone?.let { if (it.index < HearingTestModel.TONES_PER_EAR) Ear.LEFT else Ear.RIGHT }
    cue.yaw = when (ear) {
        Ear.LEFT -> LEFT_EAR_YAW
        Ear.RIGHT -> RIGHT_EAR_YAW
        null -> 0f
    }
    cue.orbit = step == TestStep.Starting || step == TestStep.Fitting
    cue.source = when (ear) {
        Ear.LEFT -> "temple_l"
        Ear.RIGHT -> "temple_r"
        null -> null
    }
    cue.inward = false
    cue.level = tone?.let { 0.3f + 0.7f * (it.level - Hearing.MIN_DBHL) / (Hearing.MAX_DBHL - Hearing.MIN_DBHL).toFloat() } ?: 0f
    val look = when (step) {
        is TestStep.Tone -> LightLook(colors.lightNeutral, colors.lightWarm, level = 0.9f, spread = 1f, breathSeconds = 1.6f, breathDepth = 0.3f)
        TestStep.Starting, TestStep.Fitting -> lookOf(Mood.SEARCHING, colors).copy(breathSeconds = 2.2f)
        is TestStep.Failed, is TestStep.Saved, is TestStep.NotFor -> lookOf(Mood.FAR, colors)
        else -> lookOf(Mood.CONNECTED, colors)
    }
    val moment = step::class
    LaunchedEffect(moment) {
        when (step) {
            TestStep.Done -> {
                host.haptics.connected()
                stage.light.flash()
                delay(400)
                stage.pose.nod()
            }
            is TestStep.Failed, is TestStep.Saved -> {
                host.haptics.error()
                stage.light.flicker()
                stage.pose.shake()
            }
            else -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val stagePx = with(density) { (maxWidth / STAGE_ASPECT).toPx() }
        // During the tones the pair comes closer, so the temple and its sound fill the stage.
        StoryStage(cue, look, with(density) { (top + BAR + STORY_ZONE / 2).toPx() }, if (tone != null) 1f else 0.8f, stagePx)
        AnimatedContent(
            targetState = step,
            contentKey = { it::class to (it as? TestStep.Tone)?.index },
            // A step dissolves into the next: nothing slides, and the size stays put.
            transitionSpec = { (fadeIn(tween(380, delayMillis = 90)) togetherWith fadeOut(tween(260))) using null },
            label = "step",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = top + BAR + STORY_ZONE, bottom = bottom + 20.dp).padding(horizontal = 24.dp),
            ) {
                TestStepContent(shown, model)
            }
        }
        TopBar(stringResource(R.string.hct_title))
    }
}

@Composable
private fun TestStepContent(step: TestStep, model: HearingTestModel) {
    val back = LocalBack.current
    when (step) {
        TestStep.Who -> {
            StoryWords(stringResource(R.string.hct_who_title), stringResource(R.string.hct_who_body))
            Text(stringResource(R.string.hct_question), style = Visor.type.label, color = Visor.colors.textSecondary, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((rating, words) in listOf(
                    HearingTestModel.RATING_MILD to R.string.hct_rating_mild,
                    HearingTestModel.RATING_NONE to R.string.hct_rating_none,
                    HearingTestModel.RATING_SEVERE to R.string.hct_rating_severe,
                    HearingTestModel.RATING_IMPLANT to R.string.hct_rating_implant,
                )) ChoicePill(stringResource(words)) { model.answer(rating) }
            }
        }
        is TestStep.NotFor -> StoryWords(stringResource(R.string.hct_not_title), stringResource(step.why)) {
            BigPill(stringResource(R.string.features_restart_done), back)
        }
        TestStep.Ready -> StoryWords(stringResource(R.string.hct_ready_title), stringResource(R.string.hct_ready_body)) {
            BigPill(stringResource(R.string.hct_begin), model::begin)
        }
        TestStep.Starting -> StoryWords(stringResource(R.string.hct_starting), null)
        is TestStep.Tone -> ToneControls(step, model)
        TestStep.Fitting -> StoryWords(stringResource(R.string.hct_fitting), null)
        is TestStep.Saved -> StoryWords(stringResource(R.string.hct_saved_title), stringResource(step.why)) {
            BigPill(stringResource(R.string.features_restart_done), back)
        }
        TestStep.Done -> StoryWords(stringResource(R.string.hct_done_title), stringResource(R.string.hct_done_body)) {
            BigPill(stringResource(R.string.features_restart_done), back)
        }
        is TestStep.Failed -> StoryWords(stringResource(R.string.hct_failed_title), stringResource(step.why)) {
            BigPill(stringResource(R.string.hct_retry), model::retry)
        }
    }
}

@Composable
private fun ToneControls(s: TestStep.Tone, model: HearingTestModel) {
    val perEar = HearingTestModel.TONES_PER_EAR
    val last = s.index == perEar * 2 - 1
    Text(stringResource(if (s.index < perEar) R.string.hct_left else R.string.hct_right), style = Visor.type.label, color = Visor.colors.textSecondary)
    Text(stringResource(R.string.hct_tone, s.index % perEar + 1, perEar), style = Visor.type.title, color = Visor.colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
    Text(stringResource(R.string.hct_tone_body), style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(VisorIcons.Minus, stringResource(R.string.hct_quieter), { model.nudge(up = false) }, size = 68.dp, background = Visor.colors.control)
        LevelDots(s.level, Modifier.padding(horizontal = 24.dp))
        RoundIcon(VisorIcons.Plus, stringResource(R.string.hct_louder), { model.nudge(up = true) }, size = 68.dp, background = Visor.colors.control)
    }
    if (s.level == Hearing.MAX_DBHL) Text(stringResource(R.string.hct_loudest_note), style = Visor.type.label, color = Visor.colors.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QuietButton(stringResource(R.string.hct_cant_hear), model::loudest)
        Spacer(Modifier.width(8.dp))
        BigPill(stringResource(if (last) R.string.hct_finish else R.string.hct_next), model::next, Modifier.weight(1f))
    }
}

/** The tone's level as a column of steps, the lit ones up to where it is. */
@Composable
private fun LevelDots(level: Int, modifier: Modifier) {
    val steps = (Hearing.MAX_DBHL - Hearing.MIN_DBHL) / 5 + 1
    val lit = (level - Hearing.MIN_DBHL) / 5 + 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (k in steps downTo 1) {
            val on = k <= lit
            Box(Modifier.size(width = (10 + k * 2).dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Color.White else Visor.colors.control))
        }
    }
}

/** A step's words, as the setup's pages have them, and what moves it on. */
@Composable
internal fun StoryWords(title: String, body: String?, action: (@Composable () -> Unit)? = null) {
    Text(title, style = Visor.type.title, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
    if (body != null) Text(body, style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
    if (action != null) Box(Modifier.padding(top = 28.dp)) { action() }
}

/** One answer of several, a raised pill. */
@Composable
internal fun ChoicePill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(Visor.colors.raised)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Visor.type.body, color = Visor.colors.textPrimary)
    }
}

/** How tall the stage zone is above a step's words. */
private val STORY_ZONE = 290.dp

// The pair rests at -24 degrees; these show the left temple, then the right, to the viewer.
private const val LEFT_EAR_YAW = -46f
private const val RIGHT_EAR_YAW = 70f

package com.macedonga.visor.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.assistant.FocusStyle
import com.macedonga.visor.core.assistant.Hearing
import com.macedonga.visor.core.assistant.HearingCause
import com.macedonga.visor.core.assistant.HearingMode
import com.macedonga.visor.core.assistant.HearingRefusal
import com.macedonga.visor.core.assistant.HearingRun
import com.macedonga.visor.core.assistant.HearingState
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.settings.display
import com.macedonga.visor.settings.ActionPill
import com.macedonga.visor.settings.Card
import com.macedonga.visor.settings.Footnote
import com.macedonga.visor.settings.LocalHost
import com.macedonga.visor.settings.PillsRow
import com.macedonga.visor.settings.Rows
import com.macedonga.visor.settings.SectionLabel
import com.macedonga.visor.settings.SettingsPage
import com.macedonga.visor.settings.SliderRow
import com.macedonga.visor.settings.ToggleRow
import com.macedonga.visor.settings.row
import com.macedonga.visor.settings.specOf
import com.macedonga.visor.ui.VisorIcons
import com.macedonga.visor.ui.asHeading
import kotlin.math.roundToInt

/**
 * Conversation focus: the glasses' microphones pick out the person in front of the wearer and the speakers bring
 * them closer. On top, where it stands and the one button that changes that; then its sound; then the switch that
 * lets these glasses run it at all (Meta's lock), and the touchpad shortcut.
 */
@Composable
fun ConversationFocusScreen(model: FeaturesModel = hiltViewModel()) {
    val state by model.features.state.collectAsStateWithLifecycle()
    val hearing by model.hearing.state.collectAsStateWithLifecycle()
    val tuning by model.features.focus.collectAsStateWithLifecycle()
    val feature = Feature.CONVERSATION_FOCUS
    val standing = state.standings[feature] ?: Standing.UNKNOWN
    val allowed = standing == Standing.ON
    val (style, volume) = tuning

    SettingsPage(stringResource(R.string.feature_cf), stringResource(R.string.cf_lede)) {
        item(key = "now") {
            val host = LocalHost.current
            // The minimum build focus needs is one of Meta's switches: read them once the glasses are there.
            LaunchedEffect(host.ready) {
                if (host.ready && !state.configRead && !state.busy) model.features.check()
            }
            FocusNow(hearing, allowed, model)
        }
        item(key = "sound") {
            val host = LocalHost.current
            val locked = !host.ready || !allowed
            Column {
                SectionLabel(stringResource(R.string.cf_sound))
                Rows(
                    row {
                        PillsRow(
                            stringResource(R.string.cf_style),
                            listOf(FocusStyle.STANDARD to stringResource(R.string.cf_style_standard), FocusStyle.BOOSTED to stringResource(R.string.cf_style_boosted)),
                            selected = hearing.style ?: style,
                            onPick = { model.features.tuneFocus(it, hearing.volume ?: volume) },
                            locked = locked,
                        )
                    },
                    row {
                        SliderRow(
                            stringResource(R.string.cf_volume),
                            value = (hearing.volume ?: volume).toFloat(),
                            range = Hearing.MIN_VOLUME.toFloat()..Hearing.MAX_VOLUME.toFloat(),
                            steps = Hearing.MAX_VOLUME - Hearing.MIN_VOLUME - 1,
                            words = { v -> v.roundToInt().let { if (it > 0) "+$it" else "$it" } },
                            onDone = { model.features.tuneFocus(hearing.style ?: style, it.roundToInt()) },
                            locked = locked,
                        )
                    },
                )
                Footnote(stringResource(R.string.cf_sound_note))
            }
        }
        item(key = "allow") {
            val host = LocalHost.current
            Column {
                SectionLabel(stringResource(R.string.cf_allow_section))
                Rows(
                    row {
                        ToggleRow(
                            stringResource(R.string.cf_allow), allowed,
                            onChange = { model.features.set(feature, it) },
                            sub = stringResource(if (allowed) R.string.cf_allow_on else R.string.cf_allow_off),
                            locked = !host.ready || !state.configRead, busy = standing == Standing.SENDING || state.busy,
                            problem = if (standing == Standing.REFUSED) stringResource(R.string.features_refused) else null,
                        )
                    },
                )
                Footnote(stringResource(R.string.cf_allow_note))
            }
        }
        item(key = "start") {
            val host = LocalHost.current
            val holds = model.features.holdStartsFocus(host.state)
            val before = host.settings.value(HOLD)?.let { specOf(host.settings, HOLD).display(it) }
            Column {
                SectionLabel(stringResource(R.string.cf_start))
                Rows(
                    row {
                        ToggleRow(
                            stringResource(R.string.cf_hold), holds,
                            onChange = { model.features.setHoldStartsFocus(it) },
                            sub = if (holds) stringResource(R.string.cf_hold_on) else before?.let { stringResource(R.string.cf_hold_now, it) },
                            locked = !host.ready || !allowed, busy = HOLD in host.state.pending,
                        )
                    },
                )
                Footnote(stringResource(R.string.cf_start_note))
            }
        }
    }
}

/** Where conversation focus stands now, in a word and a line, with the one button that changes it. */
@Composable
private fun FocusNow(hearing: HearingState, allowed: Boolean, model: FeaturesModel) {
    val host = LocalHost.current
    val connectFirst = stringResource(R.string.settings_locked)
    // The hearing app runs one mode at a time; hearing correction running is focus off.
    val run = if (hearing.mode == HearingMode.FOCUS || hearing.mode == HearingMode.UNSET) hearing.run else HearingRun.OFF
    val paused = run == HearingRun.PAUSED || run == HearingRun.PAUSED_BY_USER
    val title = when {
        !allowed -> R.string.cf_now_locked
        run == HearingRun.ON -> R.string.cf_now_on
        paused -> R.string.cf_now_paused
        else -> R.string.cf_now_off
    }
    val refusal = hearing.refusal?.takeIf { run != HearingRun.ON }
    val sub = when {
        !allowed -> stringResource(R.string.cf_now_locked_sub)
        refusal != null -> stringResource(refusalWords(refusal))
        run == HearingRun.ON -> listOfNotNull(
            hearing.style?.let { stringResource(if (it == FocusStyle.BOOSTED) R.string.cf_style_boosted else R.string.cf_style_standard) },
            hearing.volume?.let { stringResource(R.string.cf_now_volume, if (it > 0) "+$it" else "$it") },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.cf_now_on_sub) }
        paused -> stringResource(causeWords(hearing.cause))
        model.features.holdStartsFocus(host.state) -> stringResource(R.string.cf_now_off_sub)
        else -> stringResource(R.string.cf_now_off_here)
    }
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FeatureTile(VisorIcons.Ear, lit = run == HearingRun.ON, size = 52)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = Visor.type.heading, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
                Text(sub, style = Visor.type.label, color = if (refusal != null && allowed) Visor.colors.signal else Visor.colors.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            val (label, act) = when {
                run == HearingRun.ON -> R.string.cf_stop to model.features::stopFocus
                paused -> R.string.cf_resume to model.features::resumeFocus
                else -> R.string.cf_start_now to model.features::startFocus
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

private fun causeWords(cause: HearingCause): Int = when (cause) {
    HearingCause.PHONE_CALL -> R.string.cf_paused_call
    HearingCause.MUSIC, HearingCause.STREAMING_AUDIO -> R.string.cf_paused_audio
    HearingCause.LIVE_STREAMING -> R.string.cf_paused_live
    HearingCause.CAPTURING_VIDEO -> R.string.cf_paused_video
    HearingCause.THERMAL -> R.string.cf_paused_warm
    HearingCause.TIMEOUT -> R.string.cf_paused_timeout
    else -> R.string.cf_paused
}

private fun refusalWords(refusal: HearingRefusal): Int = when (refusal) {
    HearingRefusal.NUX_REQUIRED -> R.string.cf_refused_setup
    HearingRefusal.NO_ACCESS -> R.string.cf_refused_access
    HearingRefusal.GEOGATED -> R.string.cf_refused_region
    HearingRefusal.INVALID_DEVICE -> R.string.cf_refused_device
    HearingRefusal.BT_REQUIRED -> R.string.cf_refused_bluetooth
    else -> R.string.cf_refused
}

private const val HOLD = "tap_and_hold_mapping"

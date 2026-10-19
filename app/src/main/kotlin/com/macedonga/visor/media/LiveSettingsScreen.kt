package com.macedonga.visor.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.media.LiveOverlay
import com.macedonga.visor.core.media.StreamQuality
import com.macedonga.visor.settings.Footnote
import com.macedonga.visor.settings.MONO
import com.macedonga.visor.settings.PillsRow
import com.macedonga.visor.settings.Rows
import com.macedonga.visor.settings.row
import com.macedonga.visor.settings.SIDE
import com.macedonga.visor.settings.SectionLabel
import com.macedonga.visor.settings.SettingsPage
import com.macedonga.visor.settings.SheetTitle
import com.macedonga.visor.settings.ToggleRow
import com.macedonga.visor.settings.ValueRow
import com.macedonga.visor.settings.VisorSheet

/** The Live view's settings: quality (by itself, or by hand), the Wi-Fi band, the replay, what sits on the picture, the log. */
@Composable
fun LiveSettingsScreen(model: LiveViewModel = hiltViewModel()) {
    val settings by model.settings.settings.collectAsStateWithLifecycle()
    val log by model.live.log.collectAsStateWithLifecycle()
    var showLog by remember { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.live_settings_title)) {
        item(key = "quality") {
            Column {
                Rows(
                    { ToggleRow(stringResource(R.string.live_auto), !settings.manual, { auto -> model.update { it.copy(manual = !auto) } }, sub = stringResource(R.string.live_settings_auto_sub)) },
                    if (settings.manual) row {
                        PillsRow(stringResource(R.string.live_settings_resolution), StreamQuality.entries.map { it to it.label }, settings.quality, { q -> model.update { it.copy(quality = q) } })
                    } else null,
                    if (settings.manual) row {
                        PillsRow(
                            stringResource(R.string.live_settings_bitrate),
                            listOf(600_000, 1_000_000, 1_500_000, 2_500_000, 4_000_000, 6_000_000, 8_000_000).map { it to "%.1f Mbit/s".format(it / 1_000_000f) },
                            settings.bitrate, { b -> model.update { it.copy(bitrate = b) } },
                        )
                    } else null,
                    if (settings.manual) row {
                        PillsRow(stringResource(R.string.live_settings_frame_rate), listOf(24, 30).map { it to "$it fps" }, settings.frameRate, { f -> model.update { it.copy(frameRate = f) } })
                    } else null,
                )
            }
        }
        item(key = "replay") {
            Column(Modifier.padding(top = 28.dp)) {
                Rows({
                    PillsRow(
                        stringResource(R.string.live_settings_replay), listOf(30, 60, 120, 180).map { it to spanWords(it.toLong()) }, settings.replaySeconds,
                        { r ->
                            model.update { it.copy(replaySeconds = r) }
                            model.outputs.replay.windowUs = r * 1_000_000L
                        },
                        sub = stringResource(R.string.live_settings_replay_sub),
                    )
                })
            }
        }
        item(key = "picture") {
            Column {
                SectionLabel(stringResource(R.string.live_settings_on_picture))
                Rows(
                    { ToggleRow(stringResource(R.string.live_overlay_readouts), LiveOverlay.READOUTS in settings.overlays, { model.setOverlay(LiveOverlay.READOUTS, it) }) },
                    { ToggleRow(stringResource(R.string.live_overlay_heat), LiveOverlay.HEAT in settings.overlays, { model.setOverlay(LiveOverlay.HEAT, it) }) },
                    { ToggleRow(stringResource(R.string.live_overlay_grid), LiveOverlay.GRID in settings.overlays, { model.setOverlay(LiveOverlay.GRID, it) }) },
                    { ToggleRow(stringResource(R.string.live_listen_long), settings.listen, { on -> model.update { it.copy(listen = on) } }) },
                )
            }
        }
        item(key = "log") {
            Column(Modifier.padding(top = 20.dp)) {
                Rows({ ValueRow(stringResource(R.string.live_settings_log), if (log.isEmpty()) stringResource(R.string.live_settings_nothing) else log.size.toString(), onClick = if (log.isEmpty()) null else ({ showLog = true })) })
            }
        }
        item(key = "foot") { Footnote(stringResource(R.string.live_settings_foot)) }
    }

    if (showLog) {
        VisorSheet({ showLog = false }, full = true) {
            SheetTitle(stringResource(R.string.live_settings_log))
            LazyColumn(Modifier.heightIn(max = 600.dp).padding(horizontal = SIDE)) {
                items(log.takeLast(200).size) { i -> Text(log.takeLast(200)[i], style = MONO, color = Visor.colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

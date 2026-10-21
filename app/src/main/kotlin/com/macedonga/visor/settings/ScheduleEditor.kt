package com.macedonga.visor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.settings.Control
import com.macedonga.visor.core.settings.GlassesSettings
import com.macedonga.visor.core.settings.Kind
import com.macedonga.visor.core.settings.SettingSpec
import com.macedonga.visor.ui.Line
import com.macedonga.visor.ui.Section
import com.macedonga.visor.ui.asHeading
import org.json.JSONArray
import org.json.JSONObject

/** A number packing several values: a switch per flag, choices for the wider fields. */
@Composable
internal fun BitsControl(spec: SettingSpec, c: Control.Bits, value: String?, enabled: Boolean, labels: @Composable (Modifier, String?) -> Unit, write: (String) -> Unit) {
    val now = value?.trim()?.toLongOrNull() ?: 0L
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels(Modifier.weight(1f), null)
        OutlinedButton(onClick = { open = true }) { Text(if (enabled) "Edit" else "View") }
    }
    if (!open) return
    var bits by remember(now) { mutableStateOf(now) }
    FullDialog(spec.label, onDismiss = { open = false }, canSave = enabled && bits != now, onSave = {
        open = false
        write(bits.toString())
    }) {
        for (f in c.fields) {
            val mask = ((1L shl f.width) - 1) shl f.shift
            val field = (bits and mask) ushr f.shift
            if (f.width == 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = field != 0L, enabled = enabled, onCheckedChange = { bits = if (it) bits or mask else bits and mask.inv() })
                }
            } else {
                Text(f.label, style = Visor.type.body, color = Visor.colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (o in f.options) {
                        val v = o.value.toLong()
                        FilterChip(selected = field == v, enabled = enabled, onClick = { bits = (bits and mask.inv()) or (v shl f.shift) }, label = { Text(o.label) })
                    }
                }
            }
        }
        Line("Stored as $bits.", secondary = true)
    }
}

private val DAYS = listOf("mon" to "Mon", "tue" to "Tue", "wed" to "Wed", "thu" to "Thu", "fri" to "Fri", "sat" to "Sat", "sun" to "Sun")

private data class ProfileSetting(val name: String, val type: String, val arg: Long)
private data class Profile(val name: String, val settings: List<ProfileSetting>)
private data class Window(val day: String, val start: String, val end: String, val profile: String)
private data class Calendar(val version: Long, val default: String, val profiles: List<Profile>, val schedule: List<Window>)

private fun parseCalendar(text: String?): Calendar? {
    if (text.isNullOrBlank()) return Calendar(1, "", emptyList(), emptyList())
    return try {
        val o = JSONObject(text)
        val profiles = o.optJSONObject("profiles") ?: JSONObject()
        val schedule = o.optJSONArray("schedule") ?: JSONArray()
        Calendar(
            version = o.optLong("version", 1),
            default = o.optString("default"),
            profiles = profiles.keys().asSequence().map { name ->
                val list = profiles.getJSONObject(name).optJSONArray("settings") ?: JSONArray()
                Profile(name, (0 until list.length()).map { list.getJSONObject(it) }.map {
                    val type = it.optString("type", "bool")
                    ProfileSetting(it.getString("name"), type, if (type == "bool") (if (it.optBoolean("arg")) 1 else 0) else it.optLong("arg"))
                })
            }.toList(),
            schedule = (0 until schedule.length()).map { schedule.getJSONObject(it) }.map {
                Window(it.optString("day", "mon"), it.optString("start", "09:00"), it.optString("end", "17:00"), it.optString("profile"))
            },
        )
    } catch (_: Exception) {
        null
    }
}

private fun Calendar.toJson(): String = JSONObject().apply {
    put("version", version)
    put("default", default)
    put("profiles", JSONObject().apply {
        for (p in profiles) {
            put(p.name, JSONObject().put("settings", JSONArray().apply {
                for (s in p.settings) put(JSONObject().put("name", s.name).put("type", s.type).put("arg", if (s.type == "bool") s.arg != 0L else s.arg))
            }))
        }
    })
    put("schedule", JSONArray().apply {
        for (w in schedule) put(JSONObject().put("day", w.day).put("start", w.start).put("end", w.end).put("profile", w.profile))
    })
}.toString()

/** The continuous AI camera calendar: named profiles that set switches and numbers, and weekly windows. */
@Composable
internal fun ScheduleControl(spec: SettingSpec, settings: GlassesSettings, value: String?, enabled: Boolean, labels: @Composable (Modifier, String?) -> Unit, write: (String) -> Unit) {
    val start = remember(value) { parseCalendar(value) }
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels(Modifier.weight(1f), if (start == null) "The glasses hold a calendar Visor cannot read." else null)
        OutlinedButton(enabled = start != null, onClick = { open = true }) { Text(if (enabled) "Edit" else "View") }
    }
    if (!open || start == null) return
    var cal by remember(start) { mutableStateOf(start) }
    val names = cal.profiles.map { it.name }
    val valid = cal.profiles.isEmpty() || (cal.default in names && names.all { it.isNotBlank() } && names.distinct().size == names.size && cal.schedule.all { it.profile in names })
    FullDialog(spec.label, onDismiss = { open = false }, canSave = enabled && valid && cal != start, onSave = {
        open = false
        write(if (cal.profiles.isEmpty()) "" else cal.toJson())
    }) {
        Line("Profiles set switches and numbers on the glasses. Outside every window the default profile applies.", secondary = true)
        if (!valid) Line("Every profile needs a different name, and the default and every window must use one of them.")
        Section("Profiles")
        cal.profiles.forEachIndexed { i, p ->
            Box {
                OutlinedTextField(p.name, { n ->
                    val old = p.name
                    cal = cal.copy(
                        profiles = cal.profiles.mapIndexed { j, q -> if (j == i) q.copy(name = n.trim()) else q },
                        default = if (cal.default == old) n.trim() else cal.default,
                        schedule = cal.schedule.map { if (it.profile == old) it.copy(profile = n.trim()) else it },
                    )
                }, label = { Text("Name") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                p.settings.forEachIndexed { k, s ->
                    ProfileSettingRow(settings, s, enabled, onChange = { new ->
                        cal = cal.copy(profiles = cal.profiles.mapIndexed { j, q -> if (j == i) q.copy(settings = q.settings.mapIndexed { m, x -> if (m == k) new else x }) else q })
                    }, onRemove = {
                        cal = cal.copy(profiles = cal.profiles.mapIndexed { j, q -> if (j == i) q.copy(settings = q.settings.filterIndexed { m, _ -> m != k }) else q })
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingPicker(settings, enabled) { key, type ->
                        cal = cal.copy(profiles = cal.profiles.mapIndexed { j, q -> if (j == i) q.copy(settings = q.settings + ProfileSetting(key, type, 0)) else q })
                    }
                    TextButton(enabled = enabled, onClick = { cal = cal.copy(profiles = cal.profiles.filterIndexed { j, _ -> j != i }) }) { Text("Remove profile") }
                }
            }
        }
        if (enabled) OutlinedButton(onClick = {
            val name = "Profile ${cal.profiles.size + 1}"
            cal = cal.copy(profiles = cal.profiles + Profile(name, emptyList()), default = cal.default.ifEmpty { name })
        }) { Text("Add a profile") }

        if (names.isNotEmpty()) {
            Section("Default profile")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in names) FilterChip(selected = cal.default == n, enabled = enabled, onClick = { cal = cal.copy(default = n) }, label = { Text(n) })
            }
        }

        Section("Weekly windows")
        cal.schedule.forEachIndexed { i, w ->
            Box {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((d, label) in DAYS) FilterChip(selected = w.day == d, enabled = enabled, onClick = { cal = cal.withWindow(i, w.copy(day = d)) }, label = { Text(label) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TimeButton("From", w.start, enabled) { cal = cal.withWindow(i, w.copy(start = it)) }
                    TimeButton("To", w.end, enabled) { cal = cal.withWindow(i, w.copy(end = it)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in names) FilterChip(selected = w.profile == n, enabled = enabled, onClick = { cal = cal.withWindow(i, w.copy(profile = n)) }, label = { Text(n) })
                }
                TextButton(enabled = enabled, onClick = { cal = cal.copy(schedule = cal.schedule.filterIndexed { j, _ -> j != i }) }) { Text("Remove window") }
            }
        }
        if (enabled && names.isNotEmpty()) OutlinedButton(onClick = {
            cal = cal.copy(schedule = cal.schedule + Window("mon", "09:00", "17:00", cal.default.ifEmpty { names.first() }))
        }) { Text("Add a window") }

        Section("Format")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar version", style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
            OutlinedTextField(
                cal.version.toString(),
                { t -> t.filter { it.isDigit() }.toLongOrNull()?.let { cal = cal.copy(version = it) } },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

private fun Calendar.withWindow(i: Int, w: Window) = copy(schedule = schedule.mapIndexed { j, x -> if (j == i) w else x })

@Composable
private fun ProfileSettingRow(settings: GlassesSettings, s: ProfileSetting, enabled: Boolean, onChange: (ProfileSetting) -> Unit, onRemove: () -> Unit) {
    val label = settings.specs[s.name]?.label ?: s.name
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Visor.type.body, color = Visor.colors.textPrimary)
            Text(s.name, style = Visor.type.label, color = Visor.colors.textSecondary)
        }
        if (s.type == "bool") {
            Switch(checked = s.arg != 0L, enabled = enabled, onCheckedChange = { onChange(s.copy(arg = if (it) 1 else 0)) })
        } else {
            OutlinedTextField(
                s.arg.toString(),
                { t -> t.filter { it.isDigit() || it == '-' }.toLongOrNull()?.let { onChange(s.copy(arg = it)) } },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
            )
        }
        TextButton(enabled = enabled, onClick = onRemove) { Text("✕") }
    }
}

/** Choose a switch or a number the glasses keep, for a profile to set. */
@Composable
private fun SettingPicker(settings: GlassesSettings, enabled: Boolean, onPick: (String, String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(enabled = enabled, onClick = { picking = true }) { Text("Add a setting") }
    if (!picking) return
    var query by remember { mutableStateOf("") }
    val choices = remember {
        settings.catalogue.values.filter { it.kind == Kind.BOOL || it.kind == Kind.INT }
            .map { Triple(it.key, settings.specs[it.key]?.label ?: it.key, if (it.kind == Kind.BOOL) "bool" else "int") }
            .sortedBy { it.second }
    }
    AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text("Setting") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val needle = query.trim().lowercase()
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(choices.filter { needle.isEmpty() || needle in it.second.lowercase() || needle.replace(' ', '_') in it.first }, key = { it.first }) { (key, label, type) ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                picking = false
                                onPick(key, type)
                            }.padding(vertical = 6.dp),
                        ) {
                            Text(label, style = Visor.type.body)
                            Text(key, style = Visor.type.label, color = Visor.colors.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, time: String, enabled: Boolean, onSet: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(enabled = enabled, onClick = { picking = true }) { Text("$label $time") }
    if (!picking) return
    val (h, m) = time.split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val state = rememberTimePickerState(initialHour = h.coerceIn(0, 23), initialMinute = m.coerceIn(0, 59), is24Hour = true)
    AlertDialog(
        onDismissRequest = { picking = false },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                picking = false
                onSet("%02d:%02d".format(state.hour, state.minute))
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Box(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Visor.colors.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** A full-screen editor with Cancel and Save. */
@Composable
internal fun FullDialog(title: String, onDismiss: () -> Unit, canSave: Boolean, onSave: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Visor.colors.canvas)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = Visor.type.title, color = Visor.colors.textPrimary, modifier = Modifier.asHeading())
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(enabled = canSave, onClick = onSave) { Text("Save to the glasses") }
            }
        }
    }
}

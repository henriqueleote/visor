package com.macedonga.visor.settings

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.macedonga.visor.core.settings.ProtoField
import com.macedonga.visor.core.settings.ProtoFieldSpec
import com.macedonga.visor.core.settings.ProtoSchema
import com.macedonga.visor.core.settings.SettingSpec
import com.macedonga.visor.core.settings.encodeProto
import com.macedonga.visor.core.settings.parseProto
import com.macedonga.visor.core.settings.parseSchema
import com.macedonga.visor.ui.Line
import com.macedonga.visor.ui.Section
import com.macedonga.visor.ui.asHeading

/** An encoded setting: a one-line summary, and an editor built from its schema behind "Open". */
@Composable
internal fun StructuredControl(
    spec: SettingSpec,
    c: Control.Structured,
    value: String?,
    enabled: Boolean,
    labels: @Composable (Modifier, String?) -> Unit,
    write: (String) -> Unit,
) {
    val schema = remember(c) { parseSchema(c.schema) }
    val decoded = remember(value) { value?.let { decode(it) } }
    var open by remember { mutableStateOf(false) }
    val problem = when {
        schema == null -> "Its layout is not mapped."
        value != null && decoded == null -> "The glasses hold a value Visor cannot read."
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels(Modifier.weight(1f), problem)
        OutlinedButton(enabled = schema != null && decoded != null || (schema != null && value == null), onClick = { open = true }) {
            Text(if (schema?.readOnly == true || !enabled) "View" else "Edit")
        }
    }
    if (open && schema != null) {
        StructuredDialog(spec.label, schema, decoded ?: emptyList(), editable = enabled && !schema.readOnly, onDismiss = { open = false }) {
            open = false
            write(encode(it))
        }
    }
}

/** Base64 of a protobuf message, as these settings keep it; empty means an empty message. */
private fun decode(text: String): List<ProtoField>? {
    if (text.isBlank()) return emptyList()
    val bytes = try {
        Base64.decode(text.trim(), Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return parseProto(bytes)
}

private fun encode(fields: List<ProtoField>): String = Base64.encodeToString(encodeProto(fields), Base64.NO_WRAP)

@Composable
private fun StructuredDialog(
    title: String,
    schema: ProtoSchema,
    start: List<ProtoField>,
    editable: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<ProtoField>) -> Unit,
) {
    var fields by remember { mutableStateOf(start) }
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
            MessageEditor(schema, schema.root, fields, editable) { fields = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text(if (editable) "Cancel" else "Close") }
                if (editable) Button(enabled = fields !== start, onClick = { onSave(fields) }) { Text("Save to the glasses") }
            }
        }
    }
}

/** Every field the schema names for [type], with what the data holds; unknown fields ride along unchanged. */
@Composable
private fun MessageEditor(schema: ProtoSchema, type: String, fields: List<ProtoField>, editable: Boolean, onChange: (List<ProtoField>) -> Unit) {
    val specs = schema.messages[type].orEmpty()
    for (f in specs) {
        val present = fields.filter { it.number == f.number }
        when {
            f.type == "message" && f.repeated -> {
                Section(f.label)
                present.forEachIndexed { i, item ->
                    EntryCard {
                        val child = parseProto(item.bytes)
                        if (child == null) {
                            Line("This entry cannot be read; it is kept as it is.", secondary = true)
                        } else {
                            MessageEditor(schema, f.ref.orEmpty(), child, editable) { updated ->
                                onChange(replaceNth(fields, f.number, i, ProtoField(f.number, 2, bytes = encodeProto(updated))))
                            }
                        }
                        if (editable) TextButton(onClick = { onChange(removeNth(fields, f.number, i)) }) { Text("Remove") }
                    }
                }
                if (present.isEmpty()) Line("None.", secondary = true)
                if (editable) OutlinedButton(onClick = { onChange(fields + ProtoField(f.number, 2)) }) { Text("Add") }
            }

            f.type == "message" -> {
                Section(f.label)
                EntryCard {
                    val child = present.firstOrNull()?.let { parseProto(it.bytes) } ?: emptyList()
                    MessageEditor(schema, f.ref.orEmpty(), child, editable) { updated ->
                        onChange(setSingle(fields, f.number, if (updated.isEmpty()) null else ProtoField(f.number, 2, bytes = encodeProto(updated))))
                    }
                }
            }

            f.type == "bool" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.label, style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = present.firstOrNull()?.varint?.let { it != 0L } ?: false,
                    enabled = editable,
                    onCheckedChange = { onChange(setSingle(fields, f.number, if (it) ProtoField(f.number, 0, varint = 1) else null)) },
                )
            }

            f.type == "enum" -> {
                val options = schema.enums[f.ref].orEmpty()
                val now = (present.firstOrNull()?.varint ?: 0L).toString()
                Text(f.label, style = Visor.type.body, color = Visor.colors.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (o in options) {
                        FilterChip(
                            selected = now == o.value,
                            enabled = editable,
                            onClick = {
                                val v = o.value.toLong()
                                onChange(setSingle(fields, f.number, if (v == 0L) null else ProtoField(f.number, 0, varint = v)))
                            },
                            label = { Text(o.label) },
                        )
                    }
                    if (options.none { it.value == now }) FilterChip(selected = true, enabled = false, onClick = {}, label = { Text("Value $now") })
                }
            }

            f.type == "int" -> {
                val now = present.firstOrNull()?.varint ?: 0L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.label, style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
                    NumberField(now, editable) { onChange(setSingle(fields, f.number, if (it == 0L) null else ProtoField(f.number, 0, varint = it))) }
                }
            }

            else -> {
                val now = present.firstOrNull()?.bytes?.decodeToString().orEmpty()
                var draft by remember(now) { mutableStateOf(now) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onChange(setSingle(fields, f.number, if (it.isEmpty()) null else ProtoField(f.number, 2, bytes = it.encodeToByteArray())))
                    },
                    label = { Text(f.label) },
                    enabled = editable,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    val unknown = fields.count { field -> specs.none { it.number == field.number } }
    if (unknown > 0) Line("$unknown more field${if (unknown == 1) "" else "s"} this mapping does not name, kept as they are.", secondary = true)
}

@Composable
private fun EntryCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Visor.colors.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun NumberField(value: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = enabled, onClick = { onChange(value - 1) }) { Text("−") }
        OutlinedTextField(
            value = draft,
            onValueChange = { text ->
                draft = text.filter { it.isDigit() || it == '-' }
                draft.toLongOrNull()?.let(onChange)
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        TextButton(enabled = enabled, onClick = { onChange(value + 1) }) { Text("+") }
    }
}

/** Replace the field [number] with [field], or drop it when [field] is null (proto3 leaves defaults out). */
private fun setSingle(fields: List<ProtoField>, number: Int, field: ProtoField?): List<ProtoField> {
    val at = fields.indexOfFirst { it.number == number }
    if (at < 0) return if (field == null) fields else fields + field
    // Only the first copy counts; a repeat of a singular field would be merged by the reader anyway.
    val kept = fields.filterIndexed { i, f -> f.number != number || i == at }
    val idx = kept.indexOfFirst { it.number == number }
    return if (field == null) kept.filterIndexed { i, _ -> i != idx } else kept.mapIndexed { i, f -> if (i == idx) field else f }
}

private fun replaceNth(fields: List<ProtoField>, number: Int, n: Int, field: ProtoField): List<ProtoField> {
    var seen = -1
    return fields.map { if (it.number == number && ++seen == n) field else it }
}

private fun removeNth(fields: List<ProtoField>, number: Int, n: Int): List<ProtoField> {
    var seen = -1
    return fields.filterNot { it.number == number && ++seen == n }
}

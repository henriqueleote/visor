package com.macedonga.visor.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.core.settings.Control
import com.macedonga.visor.core.settings.localeOf
import com.macedonga.visor.core.settings.localeValue
import com.macedonga.visor.ui.lineLimit
import java.util.Locale

/** The phone's own values, for the settings the Meta app fills in from the phone it runs on. */

@Composable
internal fun LocaleControl(value: String?, enabled: Boolean, labels: @Composable (Modifier, String?) -> Unit, write: (String) -> Unit) {
    val options = remember {
        Locale.getAvailableLocales()
            .filter { it.language.isNotEmpty() && it.country.isNotEmpty() && it.variant.isEmpty() && it.script.isEmpty() }
            .distinctBy { localeValue(it) }
            .sortedBy { it.displayName }
            .map { Control.Option(localeValue(it), it.displayName) }
    }
    val phone = localeValue(Locale.getDefault())
    PickerRow(
        title = "Language",
        current = value?.trim(),
        shown = value?.trim()?.takeIf { it.isNotEmpty() }?.let { localeOf(it).displayName } ?: "Not set",
        options = listOf(Control.Option(phone, "This phone: ${Locale.getDefault().displayName}")) + options,
        searchable = true,
        enabled = enabled,
        labels = labels,
        write = write,
    )
}

@Composable
internal fun AppControl(c: Control.App, value: String?, enabled: Boolean, labels: @Composable (Modifier, String?) -> Unit, write: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember(c.role) { installedApps(context, c.role) }
    val current = value?.trim()
    val label = c.extra.firstOrNull { it.value == current }?.label ?: apps.firstOrNull { it.value == current }?.label ?: current?.ifEmpty { null } ?: "Not set"
    PickerRow(
        title = if (c.role == "sms") "Messages app" else "Media app",
        current = current,
        shown = label,
        options = c.extra + apps,
        searchable = apps.size > 12,
        enabled = enabled,
        labels = labels,
        write = write,
    )
}

/** Apps on this phone that can fill [role], the default SMS app first. */
private fun installedApps(context: Context, role: String): List<Control.Option> {
    val pm = context.packageManager
    val infos = when (role) {
        "sms" -> pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), 0).map { it.activityInfo.applicationInfo }
        else -> (
            pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0).map { it.serviceInfo.applicationInfo } +
                pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0).map { it.activityInfo.applicationInfo }
            )
    }
    val default = if (role == "sms") Telephony.Sms.getDefaultSmsPackage(context) else null
    return infos.distinctBy { it.packageName }
        .map { Control.Option(it.packageName, pm.getApplicationLabel(it).toString() + if (it.packageName == default) " (default)" else "") }
        .sortedWith(compareBy({ it.value != default }, { it.label.lowercase() }))
}

@Composable
internal fun PhoneListControl(c: Control.PhoneList, value: String?, enabled: Boolean, labels: @Composable (Modifier, String?) -> Unit, write: (String) -> Unit) {
    val saved = remember(value) { value.orEmpty().split(c.separator).map { it.trim() }.filter { it.isNotEmpty() } }
    var numbers by remember(value) { mutableStateOf(saved) }
    labels(Modifier, null)
    numbers.forEachIndexed { i, n ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = n,
                onValueChange = { t -> numbers = numbers.toMutableList().also { it[i] = t.filter { ch -> ch.isDigit() || ch in "+ " } } },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = enabled, onClick = { numbers = numbers.filterIndexed { j, _ -> j != i } }) { Text("Remove") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, onClick = { numbers = numbers + "" }) { Text("Add a number") }
        val clean = numbers.map { it.trim() }.filter { it.isNotEmpty() }
        Button(enabled = enabled && clean != saved, onClick = { write(clean.joinToString(c.separator)) }) { Text("Save") }
    }
}

/** A row with the value's name on a button, and a list to pick from behind it. */
@Composable
private fun PickerRow(
    title: String,
    current: String?,
    shown: String,
    options: List<Control.Option>,
    searchable: Boolean,
    enabled: Boolean,
    labels: @Composable (Modifier, String?) -> Unit,
    write: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels(Modifier.weight(1f), null)
        OutlinedButton(enabled = enabled, onClick = { picking = true }) { Text(shown, maxLines = lineLimit(1)) }
    }
    if (!picking) return
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (searchable) OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val needle = query.trim().lowercase()
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options.filter { needle.isEmpty() || needle in it.label.lowercase() || needle in it.value.lowercase() }, key = { it.value + it.label }) { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                picking = false
                                if (current != o.value) write(o.value)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == o.value, onClick = null)
                            Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                                Text(o.label, style = Visor.type.body)
                                if (o.value != o.label) Text(o.value, style = Visor.type.label, color = Visor.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
    )
}

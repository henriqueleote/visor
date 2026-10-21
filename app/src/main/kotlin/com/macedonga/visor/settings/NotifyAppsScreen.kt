package com.macedonga.visor.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macedonga.visor.R
import com.macedonga.visor.core.design.Visor
import com.macedonga.visor.ui.switchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app that can post notifications, with its name and icon. */
private class PhoneApp(val pkg: String, val name: String, val icon: ImageBitmap?)

/**
 * Which phone apps the glasses hear from: the ones that sent something lately first, every other app behind
 * "Show every app". All are on until muted, so a new app is heard from the start.
 */
@Composable
fun NotifyAppsScreen(model: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val muted by model.apps.muted.collectAsStateWithLifecycle()
    val seen by model.apps.seen.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<PhoneApp>?>(null) }
    var every by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchable(context) } }

    SettingsPage(stringResource(R.string.notify_apps_title), stringResource(R.string.notify_apps_lede), light = 0.35f) {
        val all = apps
        if (all == null) {
            item(key = "loading") {
                Row(Modifier.padding(horizontal = SIDE, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner()
                    Text(stringResource(R.string.notify_apps_loading), style = Visor.type.body, color = Visor.colors.textSecondary, modifier = Modifier.padding(start = 12.dp))
                }
            }
            return@SettingsPage
        }
        val byPkg = all.associateBy { it.pkg }
        val recent = seen.entries.sortedByDescending { it.value }.mapNotNull { byPkg[it.key] }
        val rest = all.filter { it.pkg !in seen }
        item(key = "actions") {
            Row(Modifier.padding(horizontal = SIDE).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(stringResource(R.string.notify_apps_all_on), { model.apps.setAll(all.map { it.pkg }, true) }, quiet = true, enabled = muted.isNotEmpty())
                ActionPill(stringResource(R.string.notify_apps_all_off), { model.apps.setAll(all.map { it.pkg }, false) }, quiet = true)
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                SectionLabel(stringResource(R.string.notify_apps_recent))
                Rows(*recent.map { app -> @Composable { AppRow(app, app.pkg !in muted) { on -> model.apps.set(app.pkg, on) } } }.toTypedArray())
            }
        }
        if (!every && recent.isNotEmpty()) {
            item(key = "every") {
                Box(Modifier.padding(top = 12.dp)) {
                    NavRow(null, stringResource(R.string.notify_apps_every), stringResource(R.string.notify_apps_every_sub, rest.size), onClick = { every = true })
                }
            }
        } else {
            item(key = "rest") {
                SectionLabel(stringResource(if (recent.isEmpty()) R.string.notify_apps_installed else R.string.notify_apps_others))
                Rows(*rest.map { app -> @Composable { AppRow(app, app.pkg !in muted) { on -> model.apps.set(app.pkg, on) } } }.toTypedArray())
            }
        }
        item(key = "foot") { Footnote(stringResource(R.string.notify_apps_foot)) }
    }
}

@Composable
private fun AppRow(app: PhoneApp, on: Boolean, onChange: (Boolean) -> Unit) {
    val host = LocalHost.current
    Row(
        Modifier.fillMaxWidth().switchRow(on).clickable { host.haptics.toggle(); onChange(!on) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) Image(app.icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
        else Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Visor.colors.control))
        Spacer(Modifier.width(14.dp))
        Text(app.name, style = Visor.type.body, color = Visor.colors.textPrimary, modifier = Modifier.weight(1f))
        VisorSwitch(on)
    }
}

/** Every app with a launcher entry, by name, but Visor itself. */
private fun launchable(context: Context): List<PhoneApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { info ->
            val icon = runCatching {
                val d = pm.getApplicationIcon(info)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96)
                d.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull()
            PhoneApp(info.packageName, pm.getApplicationLabel(info).toString(), icon)
        }
        .sortedBy { it.name.lowercase() }
}

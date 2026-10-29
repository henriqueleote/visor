package com.macedonga.visor.notify

import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The phone's notifications as they are now, newest first, one line each for the model to read out: "WhatsApp, 5 min
 * ago: Ana: are you coming?". [app] narrows them to one app by its name. Running and silent ones stay out, and so do
 * Visor's own and Termux's (the bridge).
 */
internal fun NotificationListenerService.recentNotices(app: String, max: Int = 8): String {
    // A model asked for every app sometimes names it ("all").
    val app = app.trim().takeUnless { it.lowercase() in EVERY_APP }.orEmpty()
    val all = runCatching { activeNotifications }.getOrNull() ?: return "The notifications could not be read just now."
    val ranking = runCatching { currentRanking }.getOrNull()
    val now = System.currentTimeMillis()
    val lines = all.filter { readable(it, ranking) }
        .map { it to label(it.packageName) }
        .filter { (_, name) -> app.isBlank() || name.contains(app.trim(), ignoreCase = true) }
        .sortedByDescending { it.first.postTime }
        .take(max)
        .map { (sbn, name) -> "$name, ${ago(now - sbn.postTime)}: ${content(sbn.notification)}" }
    return when {
        lines.isNotEmpty() -> lines.joinToString("\n")
        app.isBlank() -> "There are no notifications."
        else -> "There are no notifications from $app."
    }
}

private fun NotificationListenerService.readable(sbn: StatusBarNotification, ranking: NotificationListenerService.RankingMap?): Boolean {
    val pkg = sbn.packageName
    if (pkg == packageName || pkg.startsWith("com.termux") || pkg == "android" || pkg == "com.android.systemui") return false
    val n = sbn.notification
    val skip = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE
    if (n.flags and skip != 0) return false
    val r = NotificationListenerService.Ranking()
    if (ranking != null && ranking.getRanking(sbn.key, r) && r.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
    return content(n).isNotBlank()
}

/** A chat's last few messages with their senders, or the title and text. */
private fun content(n: Notification): String {
    val extras = n.extras
    @Suppress("DEPRECATION")
    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }.orEmpty()
    if (messages.isNotEmpty()) {
        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val said = messages.takeLast(3).joinToString(" / ") { m ->
            listOfNotNull(m.getCharSequence("sender")?.toString(), m.getCharSequence("text")?.toString()).joinToString(": ")
        }
        return listOfNotNull(conversation?.let { "in $it" }, said).joinToString(", ").take(MAX_CHARS)
    }
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
    return listOf(title, text).filter { it.isNotBlank() }.joinToString(": ").take(MAX_CHARS)
}

private fun NotificationListenerService.label(pkg: String): String =
    runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

private fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} days ago"
    }
}

private const val MAX_CHARS = 300
private val EVERY_APP = setOf("all", "any", "every", "everything", "all apps", "notifications", "phone")

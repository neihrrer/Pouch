package dev.trove.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Compact relative time: "just now", "5m", "3h", "2d", then a short date. */
fun timeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < TimeUnit.MINUTES.toMillis(1)) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (minutes < 60) return "${minutes}m ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "${hours}h ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days < 7) return "${days}d ago"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

/** Full date, e.g. "March 4, 2026". */
fun fullDate(timestamp: Long): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

/** Section label for grouping saved articles by day. */
fun daySection(timestamp: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(cal, today) -> "Today"
        sameDay(cal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d", Locale.getDefault()).format(cal.time)
    }
}

private fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
    a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

/** Domain of a URL for favicon fallbacks. */
fun domainOf(url: String): String =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: ""

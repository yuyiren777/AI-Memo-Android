package cn.aimemo.mobile.reminder

fun formatRemainingTime(targetMillis: Long, nowMillis: Long): String {
    val difference = targetMillis - nowMillis
    if (difference < 0L) return "已开始"

    val totalMinutes = (difference + 59_999L) / 60_000L
    val days = totalMinutes / 1_440L
    val hours = totalMinutes % 1_440L / 60L
    val minutes = totalMinutes % 60L
    return buildString {
        append("还有")
        if (days > 0L) append(days).append("天")
        if (hours > 0L) append(hours).append("小时")
        append(minutes).append("分钟")
    }
}

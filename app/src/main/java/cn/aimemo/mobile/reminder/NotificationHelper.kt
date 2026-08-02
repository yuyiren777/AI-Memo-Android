package cn.aimemo.mobile.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.aimemo.mobile.MainActivity
import cn.aimemo.mobile.R
import cn.aimemo.mobile.data.Schedule
import java.time.format.DateTimeFormatter

object NotificationHelper {
    private const val CHANNEL_ID = "schedule_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "日程提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "按设定时间提醒待办日程"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showSchedule(context: Context, schedule: Schedule) {
        val details = buildList {
            schedule.date?.let { date ->
                val time = schedule.startTime ?: ReminderTimeCalculator.dateOnlyDefaultTime
                add("${date.format(DateTimeFormatter.ofPattern("M月d日"))} ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
            schedule.location.takeIf(String::isNotBlank)?.let { add(it) }
            schedule.notes.takeIf(String::isNotBlank)?.let { add(it) }
        }.joinToString(" · ")
        show(
            context = context,
            notificationId = schedule.id.hashCode(),
            title = schedule.title,
            text = details.ifBlank { "该日程未设置日期，已在本次打开应用时提醒" },
        )
    }

    fun showUndatedSummary(context: Context, schedules: List<Schedule>) {
        if (schedules.isEmpty()) return
        val title = if (schedules.size == 1) schedules.first().title else "${schedules.size} 项未设置日期的待办"
        val text = schedules.take(3).joinToString("、") { it.title }
        show(context, UNDATED_NOTIFICATION_ID, title, text)
    }

    private fun show(context: Context, notificationId: Int, title: String, text: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private const val UNDATED_NOTIFICATION_ID = 912_001
}


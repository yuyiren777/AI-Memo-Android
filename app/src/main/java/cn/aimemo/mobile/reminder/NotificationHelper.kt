package cn.aimemo.mobile.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.aimemo.mobile.MainActivity
import cn.aimemo.mobile.R
import cn.aimemo.mobile.data.Schedule
import java.time.format.DateTimeFormatter
import java.time.ZoneId

object NotificationHelper {
    private const val CHANNEL_ID = "schedule_reminders_heads_up_v4_private"
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    fun createChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "日程提醒（声音和顶部横幅）",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "日程到期时播放声音、振动并在屏幕顶部显示横幅"
            setSound(sound, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 260, 140, 260)
            enableLights(true)
            lightColor = Color.rgb(36, 123, 118)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showSchedule(
        context: Context,
        schedule: Schedule,
        stageLabel: String = "日程提醒",
        stageKey: String = "final",
    ) {
        val remaining = schedule.date?.let { date ->
            val target = date.atTime(schedule.startTime ?: ReminderTimeCalculator.dateOnlyDefaultTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            formatRemainingTime(target, System.currentTimeMillis())
        }
        val details = buildList {
            remaining?.let(::add)
            schedule.date?.let { date ->
                val time = schedule.startTime ?: ReminderTimeCalculator.dateOnlyDefaultTime
                add("${date.format(DATE_FORMATTER)} ${time.format(TIME_FORMATTER)}")
            }
            schedule.location.takeIf(String::isNotBlank)?.let { add(it) }
            schedule.notes.takeIf(String::isNotBlank)?.let { add(it) }
        }.joinToString(" · ")
        show(
            context = context,
            notificationId = notificationId(schedule.id, stageKey),
            title = "$stageLabel：${schedule.title}",
            text = details.ifBlank { "该日程未设置日期，已在本次打开应用时提醒" },
        )
    }

    fun showUndatedSummary(context: Context, schedules: List<Schedule>) {
        if (schedules.isEmpty()) return
        val title = if (schedules.size == 1) schedules.first().title else "${schedules.size} 项未设置日期的待办"
        val text = schedules.take(3).joinToString("、") { it.title }
        show(context, UNDATED_NOTIFICATION_ID, title, text)
    }

    fun showTest(context: Context) {
        createChannel(context)
        show(
            context,
            TEST_NOTIFICATION_ID,
            "顶部提醒测试",
            "如果你听到了提示音并看到这条横幅，提醒设置已经正常。",
        )
    }

    fun openChannelSettings(context: Context) {
        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(channelIntent) }.getOrElse {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            openAppDetails(context)
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.getOrElse { openAppDetails(context) }
    }

    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.getOrElse { openAppDetails(context) }
    }

    fun readinessMessage(context: Context): String {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "系统通知权限尚未开启，请进入通知权限设置。"
        }
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        if (channel == null || channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            return "提醒渠道不是高优先级，请开启声音、振动和横幅/悬浮通知。"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                return "通知可以显示，但精确提醒权限未开启，触发时间可能延迟。"
            }
        }
        return "通知权限正常；若测试仍不悬浮，请在系统通知设置中开启横幅/悬浮通知。"
    }

    private fun openAppDetails(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(longArrayOf(0, 260, 140, 260))
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private const val UNDATED_NOTIFICATION_ID = 912_001
    private const val TEST_NOTIFICATION_ID = 912_002

    private fun notificationId(scheduleId: Long, stageKey: String): Int {
        val slot = when (stageKey) {
            "first" -> 1
            "second" -> 2
            else -> 3
        }
        return (scheduleId * 10 + slot).hashCode()
    }
}

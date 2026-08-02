package cn.aimemo.mobile.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.aimemo.mobile.AppPreferences
import cn.aimemo.mobile.data.Schedule

class ReminderScheduler(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(schedule: Schedule) {
        val trigger = ReminderTimeCalculator.reminderInstant(
            schedule = schedule,
            leadMinutes = preferences.reminderLeadMinutes.toLong(),
        ) ?: return
        val operation = pendingIntent(schedule.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = trigger.toEpochMilli()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(scheduleId: Long) {
        pendingIntent(scheduleId, PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(scheduleId: Long, mode: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            mode or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "cn.aimemo.mobile.action.REMIND"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}

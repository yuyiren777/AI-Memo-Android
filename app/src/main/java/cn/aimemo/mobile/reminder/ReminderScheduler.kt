package cn.aimemo.mobile.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.aimemo.mobile.AppPreferences
import cn.aimemo.mobile.MainActivity
import cn.aimemo.mobile.data.Schedule
import java.time.Clock

data class ReminderStage(val key: String, val label: String, val leadMinutes: Int, val slot: Int)

class ReminderScheduler(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun stages(): List<ReminderStage> {
        val final = ReminderStage("final", "最后一次提醒", preferences.finalReminderMinutes, 0)
        val optional = listOfNotNull(
            preferences.firstReminderMinutes?.let { ReminderStage("first", "第一次提醒", it, 1) },
            preferences.secondReminderMinutes?.let { ReminderStage("second", "第二次提醒", it, 2) },
        )
        return (optional + final)
            .filter { it.leadMinutes >= final.leadMinutes }
            .distinctBy { it.leadMinutes }
            .sortedByDescending(ReminderStage::leadMinutes)
    }

    fun schedule(schedule: Schedule, clock: Clock = Clock.systemDefaultZone()) {
        cancel(schedule.id)
        ReminderTimeCalculator.plan(schedule, stages(), clock).forEach { planned ->
            val stage = planned.stage
            val operation = pendingIntent(schedule.id, stage, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEach
            val triggerAt = planned.trigger.toEpochMilli()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showAlarmIntent()),
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }
    }

    fun cancel(scheduleId: Long) {
        listOf(
            ReminderStage("final", "最后一次提醒", 0, 0),
            ReminderStage("first", "第一次提醒", 0, 1),
            ReminderStage("second", "第二次提醒", 0, 2),
        ).forEach { stage ->
            pendingIntent(scheduleId, stage, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun pendingIntent(scheduleId: Long, stage: ReminderStage, mode: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "$ACTION_REMIND.${stage.key}"
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_STAGE_KEY, stage.key)
            putExtra(EXTRA_STAGE_LABEL, stage.label)
        }
        val requestCode = (scheduleId * 3 + stage.slot).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            mode or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showAlarmIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SHOW_ALARM_REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMIND = "cn.aimemo.mobile.action.REMIND"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_STAGE_KEY = "stage_key"
        const val EXTRA_STAGE_LABEL = "stage_label"
        private const val SHOW_ALARM_REQUEST_CODE = 914_001
    }
}

package cn.aimemo.mobile.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.aimemo.mobile.AiMemoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderGuardService.start(context)
        val scheduleId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0) return
        val application = context.applicationContext as AiMemoApplication
        val stageKey = intent.getStringExtra(ReminderScheduler.EXTRA_STAGE_KEY) ?: "final"
        val stageLabel = intent.getStringExtra(ReminderScheduler.EXTRA_STAGE_LABEL) ?: "日程提醒"
        val schedule = application.database.getById(scheduleId)
            ?.takeUnless { it.completed }
            ?: return
        NotificationHelper.showSchedule(context, schedule, stageLabel, stageKey)
        application.database.addReminderLog(schedule, stageKey, stageLabel)
        application.notifyReminderLogged()
        if (stageKey == "final" && schedule.repeatRule != "none") {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    application.repository.advanceRepeated(schedule)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}

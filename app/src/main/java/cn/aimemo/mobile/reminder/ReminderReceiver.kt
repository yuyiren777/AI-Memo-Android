package cn.aimemo.mobile.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.aimemo.mobile.AiMemoApplication

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0) return
        val application = context.applicationContext as AiMemoApplication
        application.database.getById(scheduleId)
            ?.takeUnless { it.completed }
            ?.let { NotificationHelper.showSchedule(context, it) }
    }
}


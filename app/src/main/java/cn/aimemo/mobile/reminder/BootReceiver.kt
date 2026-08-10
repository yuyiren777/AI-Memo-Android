package cn.aimemo.mobile.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.aimemo.mobile.AiMemoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        ReminderGuardService.start(context)
        val pendingResult = goAsync()
        val application = context.applicationContext as AiMemoApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.repository.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}

package cn.aimemo.mobile

import android.app.Application
import cn.aimemo.mobile.data.AccountingRepository
import cn.aimemo.mobile.data.ScheduleDatabase
import cn.aimemo.mobile.data.ScheduleRepository
import cn.aimemo.mobile.reminder.NotificationHelper
import cn.aimemo.mobile.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AiMemoApplication : Application() {
    private val _reminderEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reminderEvents = _reminderEvents.asSharedFlow()

    lateinit var preferences: AppPreferences
        private set
    lateinit var database: ScheduleDatabase
        private set
    lateinit var repository: ScheduleRepository
        private set
    lateinit var accountingRepository: AccountingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        database = ScheduleDatabase(this)
        database.setWriteAheadLoggingEnabled(true)
        repository = ScheduleRepository(
            database = database,
            reminderScheduler = ReminderScheduler(this, preferences),
        )
        accountingRepository = AccountingRepository(database)
        NotificationHelper.createChannel(this)
    }

    fun notifyReminderLogged() {
        _reminderEvents.tryEmit(Unit)
    }
}

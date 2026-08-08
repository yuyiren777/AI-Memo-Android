package cn.aimemo.mobile

import android.app.Application
import cn.aimemo.mobile.data.AccountingRepository
import cn.aimemo.mobile.data.ScheduleDatabase
import cn.aimemo.mobile.data.ScheduleRepository
import cn.aimemo.mobile.reminder.NotificationHelper
import cn.aimemo.mobile.reminder.ReminderScheduler

class AiMemoApplication : Application() {
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
}

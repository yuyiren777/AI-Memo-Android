package cn.aimemo.mobile.data

import cn.aimemo.mobile.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ScheduleRepository(
    private val database: ScheduleDatabase,
    private val reminderScheduler: ReminderScheduler,
) {
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _schedules.value = database.listAll()
    }

    suspend fun save(schedule: Schedule): Schedule = withContext(Dispatchers.IO) {
        require(schedule.title.isNotBlank()) { "日程标题不能为空" }
        val saved = database.save(schedule)
        reminderScheduler.cancel(saved.id)
        if (!saved.completed) reminderScheduler.schedule(saved)
        _schedules.value = database.listAll()
        saved
    }

    suspend fun setCompleted(schedule: Schedule, completed: Boolean) {
        save(schedule.copy(completed = completed))
    }

    suspend fun delete(schedule: Schedule) = withContext(Dispatchers.IO) {
        reminderScheduler.cancel(schedule.id)
        database.delete(schedule.id)
        _schedules.value = database.listAll()
    }

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        database.listAll().filterNot { it.completed }.forEach(reminderScheduler::schedule)
    }

    fun undatedPending(): List<Schedule> = _schedules.value.filter { !it.completed && it.date == null }
}


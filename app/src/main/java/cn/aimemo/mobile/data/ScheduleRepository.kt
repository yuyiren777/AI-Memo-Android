package cn.aimemo.mobile.data

import cn.aimemo.mobile.reminder.ReminderScheduler
import cn.aimemo.mobile.reminder.ReminderTimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleRepository(
    private val database: ScheduleDatabase,
    private val reminderScheduler: ReminderScheduler,
) {
    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val existing = database.listAll()
        val normalized = existing.map(::normalizeSchedule)
        existing.zip(normalized).forEach { (original, current) ->
            if (original != current) {
                database.save(current)
                if (!current.completed) reminderScheduler.schedule(current)
            }
        }
        _schedules.value = normalized
    }

    suspend fun save(schedule: Schedule): Schedule = withContext(Dispatchers.IO) {
        require(schedule.title.isNotBlank()) { "日程标题不能为空" }
        val saved = database.save(normalizeSchedule(schedule))
        reminderScheduler.cancel(saved.id)
        if (!saved.completed) reminderScheduler.schedule(saved)
        _schedules.value = database.listAll()
        saved
    }

    suspend fun setCompleted(schedule: Schedule, completed: Boolean) {
        save(schedule.copy(completed = completed))
    }

    suspend fun setCompleted(schedules: List<Schedule>, completed: Boolean) = withContext(Dispatchers.IO) {
        schedules.distinctBy(Schedule::id).forEach { schedule ->
            val saved = database.save(normalizeSchedule(schedule.copy(completed = completed)))
            reminderScheduler.cancel(saved.id)
            if (!saved.completed) reminderScheduler.schedule(saved)
        }
        _schedules.value = database.listAll()
    }

    suspend fun delete(schedule: Schedule) = withContext(Dispatchers.IO) {
        reminderScheduler.cancel(schedule.id)
        database.delete(schedule.id)
        _schedules.value = database.listAll()
    }

    suspend fun delete(schedules: List<Schedule>) = withContext(Dispatchers.IO) {
        schedules.distinctBy(Schedule::id).forEach { schedule ->
            reminderScheduler.cancel(schedule.id)
            database.delete(schedule.id)
        }
        _schedules.value = database.listAll()
    }

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val existing = database.listAll()
        val normalized = existing.map(::normalizeSchedule)
        existing.zip(normalized).forEach { (original, current) ->
            if (original != current) database.save(current)
        }
        normalized.filterNot { it.completed }.forEach(reminderScheduler::schedule)
        _schedules.value = normalized
    }

    suspend fun reminderLogs(): List<ReminderLog> = withContext(Dispatchers.IO) {
        database.listReminderLogs()
    }

    suspend fun deleteReminderLog(id: Long) = withContext(Dispatchers.IO) {
        database.deleteReminderLog(id)
    }

    suspend fun markReminderLogsSeen() = withContext(Dispatchers.IO) {
        database.markAllReminderLogsSeen()
    }

    suspend fun advanceRepeated(schedule: Schedule) {
        val date = schedule.date ?: LocalDate.now()
        val nextDate = ReminderTimeCalculator.nextRepeatedDate(date, schedule.repeatRule) ?: return
        save(schedule.copy(date = nextDate, completed = false))
    }

    private fun normalizeSchedule(schedule: Schedule): Schedule {
        if (schedule.completed || schedule.repeatRule == "none") return schedule

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        var nextDate = schedule.date ?: LocalDate.now(zone)
        val time = schedule.startTime ?: LocalTime.NOON
        while (!nextDate.atTime(time).atZone(zone).toInstant().isAfter(now)) {
            nextDate = ReminderTimeCalculator.nextRepeatedDate(nextDate, schedule.repeatRule)
                ?: break
        }
        return if (schedule.date == nextDate) schedule else schedule.copy(date = nextDate)
    }

    fun undatedPending(): List<Schedule> = _schedules.value.filter { !it.completed && it.date == null }

    fun clearMemory() {
        _schedules.value = emptyList()
    }
}

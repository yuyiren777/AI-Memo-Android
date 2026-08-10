package cn.aimemo.mobile.reminder

import cn.aimemo.mobile.data.Schedule
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.LocalDate

data class PlannedReminder(val stage: ReminderStage, val trigger: Instant)

object ReminderTimeCalculator {
    val dateOnlyDefaultTime: LocalTime = LocalTime.NOON

    fun eventInstant(schedule: Schedule, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        val date = schedule.date ?: return null
        val time = schedule.startTime ?: dateOnlyDefaultTime
        return date.atTime(time).atZone(zoneId).toInstant()
    }

    fun reminderInstant(
        schedule: Schedule,
        leadMinutes: Long,
        clock: Clock = Clock.systemDefaultZone(),
    ): Instant? {
        val event = eventInstant(schedule, clock.zone) ?: return null
        if (!event.isAfter(clock.instant())) return null
        val preferred = event.minusSeconds(leadMinutes.coerceAtLeast(0) * 60)
        return if (preferred.isAfter(clock.instant())) preferred else clock.instant().plusSeconds(3)
    }

    fun plan(
        schedule: Schedule,
        stages: List<ReminderStage>,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<PlannedReminder> {
        val event = eventInstant(schedule, clock.zone) ?: return emptyList()
        val now = clock.instant()
        val finalStage = stages.minByOrNull(ReminderStage::leadMinutes) ?: return emptyList()
        if (!event.isAfter(now)) {
            val today = LocalDate.now(clock)
            return if (schedule.startTime == null && schedule.date == today) {
                listOf(PlannedReminder(finalStage, now.plusSeconds(3)))
            } else emptyList()
        }

        val preferred = stages.associateWith { event.minusSeconds(it.leadMinutes.toLong() * 60) }
        val future = preferred.filterValues { it.isAfter(now) }
            .map { PlannedReminder(it.key, it.value) }
        val due = preferred.filterValues { !it.isAfter(now) }.keys
            .minByOrNull(ReminderStage::leadMinutes)
            ?.let { PlannedReminder(it, now.plusSeconds(3)) }
        return (future + listOfNotNull(due)).sortedBy(PlannedReminder::trigger)
    }
}

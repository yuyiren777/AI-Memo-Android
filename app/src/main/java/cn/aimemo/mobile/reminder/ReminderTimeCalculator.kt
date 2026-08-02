package cn.aimemo.mobile.reminder

import cn.aimemo.mobile.data.Schedule
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

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
}


package cn.aimemo.mobile.reminder

import cn.aimemo.mobile.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dateOnlyScheduleOccursAtNoon() {
        val schedule = Schedule(title = "考试", date = LocalDate.of(2026, 8, 3))
        assertEquals(
            Instant.parse("2026-08-03T04:00:00Z"),
            ReminderTimeCalculator.eventInstant(schedule, zone),
        )
    }

    @Test
    fun explicitTimeIsPreserved() {
        val schedule = Schedule(
            title = "开会",
            date = LocalDate.of(2026, 8, 3),
            startTime = LocalTime.of(9, 15),
        )
        assertEquals(
            Instant.parse("2026-08-03T01:15:00Z"),
            ReminderTimeCalculator.eventInstant(schedule, zone),
        )
    }

    @Test
    fun undatedScheduleHasNoClockAlarm() {
        assertNull(ReminderTimeCalculator.eventInstant(Schedule(title = "买资料"), zone))
    }

    @Test
    fun imminentEventSchedulesAThreeSecondFallback() {
        val now = Instant.parse("2026-08-03T01:14:30Z")
        val clock = Clock.fixed(now, zone)
        val schedule = Schedule(
            title = "开会",
            date = LocalDate.of(2026, 8, 3),
            startTime = LocalTime.of(9, 15),
        )
        assertEquals(now.plusSeconds(3), ReminderTimeCalculator.reminderInstant(schedule, 30, clock))
    }
}


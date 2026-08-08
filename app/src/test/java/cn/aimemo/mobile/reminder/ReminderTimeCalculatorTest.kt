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

    @Test
    fun missedEarlyStagesDoNotAllFireTogether() {
        val now = Instant.parse("2026-08-03T01:10:00Z")
        val clock = Clock.fixed(now, zone)
        val schedule = Schedule(
            title = "考试",
            date = LocalDate.of(2026, 8, 3),
            startTime = LocalTime.of(10, 0),
        )
        val stages = listOf(
            ReminderStage("first", "第一次提醒", 1440, 1),
            ReminderStage("second", "第二次提醒", 60, 2),
            ReminderStage("final", "最后一次提醒", 30, 0),
        )
        val plan = ReminderTimeCalculator.plan(schedule, stages, clock)
        assertEquals(listOf("second", "final"), plan.map { it.stage.key })
        assertEquals(now.plusSeconds(3), plan.first().trigger)
    }

    @Test
    fun dateOnlyCreatedAfterNoonGetsOneImmediateReminder() {
        val now = Instant.parse("2026-08-03T06:00:00Z")
        val clock = Clock.fixed(now, zone)
        val schedule = Schedule(title = "交材料", date = LocalDate.of(2026, 8, 3))
        val stages = listOf(ReminderStage("final", "最后一次提醒", 30, 0))
        val plan = ReminderTimeCalculator.plan(schedule, stages, clock)
        assertEquals(1, plan.size)
        assertEquals(now.plusSeconds(3), plan.single().trigger)
    }
}

package cn.aimemo.mobile.ui

import cn.aimemo.mobile.reminder.formatRemainingTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimeFormatterTest {
    @Test
    fun keepsMinutePrecisionAcrossDaysAndHours() {
        val now = 1_000_000L
        val difference = ((2 * 24 + 3) * 60L + 17L) * 60_000L
        assertEquals("还有2天3小时17分钟", formatRemainingTime(now + difference, now))
    }

    @Test
    fun roundsAnUpcomingPartialMinuteUp() {
        assertEquals("还有1分钟", formatRemainingTime(60_001L, 1L))
        assertEquals("还有1分钟", formatRemainingTime(59_999L, 1L))
    }

    @Test
    fun startedScheduleIsClearlyMarked() {
        assertEquals("已开始", formatRemainingTime(999L, 1_000L))
    }
}

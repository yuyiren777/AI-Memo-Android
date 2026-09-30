package cn.aimemo.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleExtractorTest {
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun afternoonTimeOnlyDefaultsToToday() {
        val result = ScheduleExtractor.parse(
            "[{\"title\":\"开会\",\"date\":null,\"start_time\":\"15:00\",\"repeat\":\"none\"}]",
            fallbackText = "下午3点开会",
            today = today,
        )
        assertEquals(today, result.single().date)
    }

    @Test
    fun explicitRelativeDateIsNotOverridden() {
        val result = ScheduleExtractor.parse(
            "[{\"title\":\"开会\",\"date\":null,\"start_time\":\"15:00\",\"repeat\":\"none\"}]",
            fallbackText = "明天下午3点开会",
            today = today,
        )
        assertNull(result.single().date)
    }

    @Test
    fun repeatedAfternoonScheduleKeepsDateUnset() {
        val result = ScheduleExtractor.parse(
            "[{\"title\":\"站会\",\"date\":null,\"start_time\":\"15:00\",\"repeat\":\"daily\"}]",
            fallbackText = "每天中午开站会",
            today = today,
        )
        assertNull(result.single().date)
    }
}

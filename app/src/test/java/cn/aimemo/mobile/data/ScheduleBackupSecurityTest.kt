package cn.aimemo.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.crypto.spec.SecretKeySpec

class ScheduleBackupSecurityTest {
    @Test
    fun encryptedBackupHidesAllSensitiveDataAndRoundTrips() {
        val contents = SecureBackupContents(
            schedules = listOf(
                Schedule(
                    title = "机密会议",
                    notes = "预算与账户信息",
                    location = "内部会议室",
                    date = LocalDate.of(2026, 8, 20),
                    startTime = LocalTime.of(9, 30),
                    urgency = Urgency.URGENT,
                    createdAt = 123456789L,
                ),
            ),
            accountEntries = listOf(
                AccountEntry(
                    type = AccountEntryType.EXPENSE,
                    amountCents = 12_345L,
                    category = "私密分类",
                    note = "敏感账目备注",
                    occurredAt = 234567890L,
                    createdAt = 234567891L,
                ),
            ),
            budgetSettings = BudgetSettings(
                monthlyBudgetsCents = mapOf(
                    YearMonth.of(2026, 8) to 300_000L,
                    YearMonth.of(2026, 9) to 320_000L,
                ),
                yearlyBudgetsCents = mapOf(2026 to 3_600_000L, 2027 to 4_000_000L),
            ),
        )
        val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")

        val encoded = SecureScheduleBackup.encodeWithKey(contents, key)

        assertTrue(SecureScheduleBackup.isEncrypted(encoded))
        assertFalse(encoded.contains("机密会议"))
        assertFalse(encoded.contains("敏感账目备注"))
        assertFalse(encoded.contains("300000"))
        assertEquals(contents, SecureScheduleBackup.decodeWithKey(encoded, key))
    }

    @Test
    fun budgetsAreIndependentForEveryMonthAndYear() {
        val august = YearMonth.of(2026, 8)
        val september = YearMonth.of(2026, 9)
        val settings = BudgetSettings()
            .withMonthlyBudget(august, 300_000L)
            .withMonthlyBudget(september, 250_000L)
            .withYearlyBudget(2026, 3_600_000L)
            .withYearlyBudget(2027, 4_200_000L)

        assertEquals(300_000L, settings.budgetForMonth(august))
        assertEquals(250_000L, settings.budgetForMonth(september))
        assertEquals(3_600_000L, settings.budgetForYear(2026))
        assertEquals(4_200_000L, settings.budgetForYear(2027))
        assertEquals(0L, settings.withMonthlyBudget(august, 0L).budgetForMonth(august))
    }

    @Test
    fun legacyGlobalBudgetsMigrateToCurrentPeriod() {
        val legacyPayload = """
            {
              "format":"ai-memo-schedule-backup-payload",
              "version":2,
              "schedules":[],
              "account_entries":[],
              "budgets":{"monthly_cents":300000,"yearly_cents":3600000}
            }
        """.trimIndent()

        val budgets = ScheduleBackup.decodePayload(legacyPayload).budgetSettings
        val currentMonth = YearMonth.now()

        assertEquals(300_000L, budgets.budgetForMonth(currentMonth))
        assertEquals(3_600_000L, budgets.budgetForYear(currentMonth.year))
    }

    @Test
    fun encryptedBackupRejectsAnotherDeviceKey() {
        val contents = SecureBackupContents(
            schedules = listOf(Schedule(title = "私密日程")),
            accountEntries = emptyList(),
            budgetSettings = BudgetSettings(),
        )
        val originalKey = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        val otherDeviceKey = SecretKeySpec(ByteArray(32) { 2 }, "AES")
        val encoded = SecureScheduleBackup.encodeWithKey(contents, originalKey)

        assertThrows(IllegalArgumentException::class.java) {
            SecureScheduleBackup.decodeWithKey(encoded, otherDeviceKey)
        }
    }
}

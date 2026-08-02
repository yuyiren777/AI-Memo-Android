package cn.aimemo.mobile.data

import java.time.LocalDate
import java.time.LocalTime

enum class Urgency(val value: Int, val label: String) {
    NORMAL(0, "普通"),
    IMPORTANT(1, "重要"),
    URGENT(2, "紧急");

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

data class Schedule(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val location: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val urgency: Urgency = Urgency.NORMAL,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)


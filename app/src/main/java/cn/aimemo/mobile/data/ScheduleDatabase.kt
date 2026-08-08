package cn.aimemo.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalTime

class ScheduleDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL DEFAULT '',
                schedule_date TEXT,
                start_time TEXT,
                end_time TEXT,
                repeat_rule TEXT NOT NULL DEFAULT 'none',
                urgency INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX idx_schedules_date ON schedules(completed, schedule_date, start_time)"
        )
        createReminderLogTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE schedules ADD COLUMN end_time TEXT")
            database.execSQL("ALTER TABLE schedules ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT 'none'")
            createReminderLogTable(database)
        }
    }

    fun listAll(): List<Schedule> {
        val schedules = mutableListOf<Schedule>()
        readableDatabase.query(
            "schedules",
            null,
            null,
            null,
            null,
            null,
            "completed ASC, schedule_date IS NULL ASC, schedule_date ASC, start_time ASC, created_at DESC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val notesIndex = cursor.getColumnIndexOrThrow("notes")
            val locationIndex = cursor.getColumnIndexOrThrow("location")
            val dateIndex = cursor.getColumnIndexOrThrow("schedule_date")
            val timeIndex = cursor.getColumnIndexOrThrow("start_time")
            val endTimeIndex = cursor.getColumnIndexOrThrow("end_time")
            val repeatIndex = cursor.getColumnIndexOrThrow("repeat_rule")
            val urgencyIndex = cursor.getColumnIndexOrThrow("urgency")
            val completedIndex = cursor.getColumnIndexOrThrow("completed")
            val createdIndex = cursor.getColumnIndexOrThrow("created_at")
            while (cursor.moveToNext()) {
                schedules += Schedule(
                    id = cursor.getLong(idIndex),
                    title = cursor.getString(titleIndex),
                    notes = cursor.getString(notesIndex),
                    location = cursor.getString(locationIndex),
                    date = cursor.getStringOrNull(dateIndex)?.let(LocalDate::parse),
                    startTime = cursor.getStringOrNull(timeIndex)?.let(LocalTime::parse),
                    endTime = cursor.getStringOrNull(endTimeIndex)?.let(LocalTime::parse),
                    repeatRule = cursor.getString(repeatIndex),
                    urgency = Urgency.fromValue(cursor.getInt(urgencyIndex)),
                    completed = cursor.getInt(completedIndex) == 1,
                    createdAt = cursor.getLong(createdIndex),
                )
            }
        }
        return schedules
    }

    fun getById(id: Long): Schedule? = readableDatabase.query(
        "schedules",
        null,
        "id = ?",
        arrayOf(id.toString()),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        Schedule(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
            date = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("schedule_date"))?.let(LocalDate::parse),
            startTime = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("start_time"))?.let(LocalTime::parse),
            endTime = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("end_time"))?.let(LocalTime::parse),
            repeatRule = cursor.getString(cursor.getColumnIndexOrThrow("repeat_rule")),
            urgency = Urgency.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow("urgency"))),
            completed = cursor.getInt(cursor.getColumnIndexOrThrow("completed")) == 1,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
        )
    }

    fun save(schedule: Schedule): Schedule {
        val values = ContentValues().apply {
            put("title", schedule.title.trim())
            put("notes", schedule.notes.trim())
            put("location", schedule.location.trim())
            putNullable("schedule_date", schedule.date?.toString())
            putNullable("start_time", schedule.startTime?.withSecond(0)?.withNano(0)?.toString())
            putNullable("end_time", schedule.endTime?.withSecond(0)?.withNano(0)?.toString())
            put("repeat_rule", schedule.repeatRule)
            put("urgency", schedule.urgency.value)
            put("completed", if (schedule.completed) 1 else 0)
            put("created_at", schedule.createdAt)
        }
        val id = if (schedule.id == 0L) {
            writableDatabase.insertOrThrow("schedules", null, values)
        } else {
            writableDatabase.update("schedules", values, "id = ?", arrayOf(schedule.id.toString()))
            schedule.id
        }
        return schedule.copy(id = id)
    }

    fun delete(id: Long) {
        writableDatabase.delete("reminder_logs", "schedule_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("schedules", "id = ?", arrayOf(id.toString()))
    }

    fun addReminderLog(schedule: Schedule, stage: String, message: String) {
        writableDatabase.insertOrThrow("reminder_logs", null, ContentValues().apply {
            put("schedule_id", schedule.id)
            put("schedule_title", schedule.title)
            put("stage", stage)
            put("message", message)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun listReminderLogs(): List<ReminderLog> {
        val result = mutableListOf<ReminderLog>()
        readableDatabase.query("reminder_logs", null, null, null, null, null, "created_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                result += ReminderLog(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    scheduleId = cursor.getLong(cursor.getColumnIndexOrThrow("schedule_id")),
                    scheduleTitle = cursor.getString(cursor.getColumnIndexOrThrow("schedule_title")),
                    stage = cursor.getString(cursor.getColumnIndexOrThrow("stage")),
                    message = cursor.getString(cursor.getColumnIndexOrThrow("message")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                )
            }
        }
        return result
    }

    fun deleteReminderLog(id: Long) {
        writableDatabase.delete("reminder_logs", "id = ?", arrayOf(id.toString()))
    }

    private fun createReminderLogTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminder_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                schedule_id INTEGER NOT NULL,
                schedule_title TEXT NOT NULL,
                stage TEXT NOT NULL,
                message TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    companion object {
        private const val DATABASE_NAME = "ai_memo_mobile.db"
        private const val DATABASE_VERSION = 2
    }
}

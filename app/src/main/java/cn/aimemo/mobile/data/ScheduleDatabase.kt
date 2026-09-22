package cn.aimemo.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class ScheduleDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    private val payloadCipher = SecurePayloadCipher()
    private val securityPreferences = context.getSharedPreferences(SECURITY_PREFERENCES, Context.MODE_PRIVATE)

    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        // Some vendor SQLite builds reject PRAGMA statements passed to execSQL as queries.
        runCatching { executePragmaQuery(database, "PRAGMA secure_delete=ON") }
    }

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
                created_at INTEGER NOT NULL,
                secure_payload TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX idx_schedules_date ON schedules(completed, schedule_date, start_time)"
        )
        createReminderLogTable(database)
        createAccountEntriesTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE schedules ADD COLUMN end_time TEXT")
            database.execSQL("ALTER TABLE schedules ADD COLUMN repeat_rule TEXT NOT NULL DEFAULT 'none'")
            createReminderLogTable(database)
        }
        if (oldVersion < 3) {
            createAccountEntriesTable(database)
        }
        if (oldVersion in 2..3) {
            database.execSQL("ALTER TABLE reminder_logs ADD COLUMN seen INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 5) {
            migrateSensitiveRows(database)
            securityPreferences.edit().putBoolean(KEY_DATABASE_VACUUM_REQUIRED, true).apply()
        }
    }

    override fun onOpen(database: SQLiteDatabase) {
        super.onOpen(database)
        if (!securityPreferences.getBoolean(KEY_DATABASE_VACUUM_REQUIRED, false)) return
        runCatching {
            executePragmaQuery(database, "PRAGMA wal_checkpoint(TRUNCATE)")
        }.onSuccess {
            securityPreferences.edit().remove(KEY_DATABASE_VACUUM_REQUIRED).apply()
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
            "id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                schedules += cursor.readSchedule()
            }
        }
        return schedules.sortedWith(
            compareBy<Schedule> { it.completed }
                .thenBy { it.date == null }
                .thenBy { it.date }
                .thenBy { it.startTime }
                .thenByDescending { it.createdAt },
        )
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
        cursor.readSchedule()
    }

    fun save(schedule: Schedule): Schedule {
        val values = secureScheduleValues(schedule)
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
        val createdAt = System.currentTimeMillis()
        writableDatabase.insertOrThrow(
            "reminder_logs",
            null,
            secureReminderLogValues(
                ReminderLog(
                    scheduleId = schedule.id,
                    scheduleTitle = schedule.title,
                    stage = stage,
                    message = message,
                    createdAt = createdAt,
                ),
            ),
        )
    }

    fun listReminderLogs(): List<ReminderLog> {
        val result = mutableListOf<ReminderLog>()
        readableDatabase.query("reminder_logs", null, null, null, null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.readReminderLog()
            }
        }
        return result.sortedByDescending(ReminderLog::createdAt)
    }

    fun deleteReminderLog(id: Long) {
        writableDatabase.delete("reminder_logs", "id = ?", arrayOf(id.toString()))
    }

    fun markAllReminderLogsSeen() {
        val database = writableDatabase
        val logs = listReminderLogs().filterNot(ReminderLog::seen)
        database.beginTransaction()
        try {
            logs.forEach { log ->
                database.update(
                    "reminder_logs",
                    ContentValues().apply {
                        put("secure_payload", encodeReminderLog(log.copy(seen = true)))
                        put("schedule_title", "")
                        put("stage", "")
                        put("message", "")
                        put("created_at", 0L)
                        put("seen", 0)
                    },
                    "id = ?",
                    arrayOf(log.id.toString()),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun listAccountEntries(): List<AccountEntry> {
        val result = mutableListOf<AccountEntry>()
        readableDatabase.query(
            "account_entries",
            null,
            null,
            null,
            null,
            null,
            "id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.readAccountEntry()
            }
        }
        return result.sortedWith(compareByDescending<AccountEntry> { it.occurredAt }.thenByDescending { it.id })
    }

    fun saveAccountEntry(entry: AccountEntry): AccountEntry {
        val values = secureAccountEntryValues(entry)
        val id = if (entry.id == 0L) {
            writableDatabase.insertOrThrow("account_entries", null, values)
        } else {
            writableDatabase.update("account_entries", values, "id = ?", arrayOf(entry.id.toString()))
            entry.id
        }
        return entry.copy(id = id)
    }

    fun saveAccountEntries(entries: List<AccountEntry>): List<AccountEntry> {
        val database = writableDatabase
        database.beginTransaction()
        return try {
            entries.map(::saveAccountEntry).also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
        }
    }

    fun deleteAccountEntry(id: Long) {
        writableDatabase.delete("account_entries", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAccountEntries(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val database = writableDatabase
        database.beginTransaction()
        try {
            ids.forEach { id ->
                database.delete("account_entries", "id = ?", arrayOf(id.toString()))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun migrateSensitiveRows(database: SQLiteDatabase) {
        ensureSecurePayloadColumn(database, "schedules")
        ensureSecurePayloadColumn(database, "reminder_logs")
        ensureSecurePayloadColumn(database, "account_entries")

        val schedules = database.query(
            "schedules",
            null,
            "secure_payload IS NULL OR secure_payload = ''",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.readSchedule()) }
        }
        schedules.forEach { schedule ->
            database.update(
                "schedules",
                secureScheduleValues(schedule),
                "id = ?",
                arrayOf(schedule.id.toString()),
            )
        }
        val reminderLogs = database.query(
            "reminder_logs",
            null,
            "secure_payload IS NULL OR secure_payload = ''",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.readReminderLog()) }
        }
        reminderLogs.forEach { log ->
            database.update(
                "reminder_logs",
                secureReminderLogValues(log),
                "id = ?",
                arrayOf(log.id.toString()),
            )
        }
        val accountEntries = database.query(
            "account_entries",
            null,
            "secure_payload IS NULL OR secure_payload = ''",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.readAccountEntry()) }
        }
        accountEntries.forEach { entry ->
            database.update(
                "account_entries",
                secureAccountEntryValues(entry),
                "id = ?",
                arrayOf(entry.id.toString()),
            )
        }
    }

    private fun ensureSecurePayloadColumn(database: SQLiteDatabase, table: String) {
        val hasColumn = database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == "secure_payload" }
        }
        if (!hasColumn) database.execSQL("ALTER TABLE $table ADD COLUMN secure_payload TEXT")
    }

    private fun executePragmaQuery(database: SQLiteDatabase, sql: String) {
        database.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) Unit
        }
    }

    private fun secureScheduleValues(schedule: Schedule) = ContentValues().apply {
        put("title", "")
        put("notes", "")
        put("location", "")
        putNull("schedule_date")
        putNull("start_time")
        putNull("end_time")
        put("repeat_rule", "")
        put("urgency", 0)
        put("completed", 0)
        put("created_at", 0L)
        put("secure_payload", encodeSchedule(schedule))
    }

    private fun secureReminderLogValues(log: ReminderLog) = ContentValues().apply {
        put("schedule_id", log.scheduleId)
        put("schedule_title", "")
        put("stage", "")
        put("message", "")
        put("created_at", 0L)
        put("seen", 0)
        put("secure_payload", encodeReminderLog(log))
    }

    private fun secureAccountEntryValues(entry: AccountEntry) = ContentValues().apply {
        put("entry_type", 0)
        put("amount_cents", 0L)
        put("category", "")
        put("note", "")
        put("occurred_at", 0L)
        put("created_at", 0L)
        put("secure_payload", encodeAccountEntry(entry))
    }

    private fun android.database.Cursor.readSchedule(): Schedule {
        val id = getLong(getColumnIndexOrThrow("id"))
        val payload = getStringOrNull(getColumnIndexOrThrow("secure_payload"))
        if (!payload.isNullOrBlank()) return decodeSchedule(id, payload)
        return Schedule(
            id = id,
            title = getString(getColumnIndexOrThrow("title")),
            notes = getString(getColumnIndexOrThrow("notes")),
            location = getString(getColumnIndexOrThrow("location")),
            date = getStringOrNull(getColumnIndexOrThrow("schedule_date"))?.let(LocalDate::parse),
            startTime = getStringOrNull(getColumnIndexOrThrow("start_time"))?.let(LocalTime::parse),
            endTime = getStringOrNull(getColumnIndexOrThrow("end_time"))?.let(LocalTime::parse),
            repeatRule = getString(getColumnIndexOrThrow("repeat_rule")),
            urgency = Urgency.fromValue(getInt(getColumnIndexOrThrow("urgency"))),
            completed = getInt(getColumnIndexOrThrow("completed")) == 1,
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
        )
    }

    private fun android.database.Cursor.readReminderLog(): ReminderLog {
        val id = getLong(getColumnIndexOrThrow("id"))
        val payload = getStringOrNull(getColumnIndexOrThrow("secure_payload"))
        if (!payload.isNullOrBlank()) return decodeReminderLog(id, payload)
        return ReminderLog(
            id = id,
            scheduleId = getLong(getColumnIndexOrThrow("schedule_id")),
            scheduleTitle = getString(getColumnIndexOrThrow("schedule_title")),
            stage = getString(getColumnIndexOrThrow("stage")),
            message = getString(getColumnIndexOrThrow("message")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            seen = getInt(getColumnIndexOrThrow("seen")) == 1,
        )
    }

    private fun android.database.Cursor.readAccountEntry(): AccountEntry {
        val id = getLong(getColumnIndexOrThrow("id"))
        val payload = getStringOrNull(getColumnIndexOrThrow("secure_payload"))
        if (!payload.isNullOrBlank()) return decodeAccountEntry(id, payload)
        return AccountEntry(
            id = id,
            type = AccountEntryType.fromValue(getInt(getColumnIndexOrThrow("entry_type"))),
            amountCents = getLong(getColumnIndexOrThrow("amount_cents")),
            category = getString(getColumnIndexOrThrow("category")),
            note = getString(getColumnIndexOrThrow("note")),
            occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
        )
    }

    private fun encodeSchedule(schedule: Schedule): String = payloadCipher.encrypt(
        SCOPE_SCHEDULE,
        JSONObject().apply {
            put("title", schedule.title.trim())
            put("notes", schedule.notes.trim())
            put("location", schedule.location.trim())
            putNullable("date", schedule.date?.toString())
            putNullable("start_time", schedule.startTime?.withSecond(0)?.withNano(0)?.toString())
            putNullable("end_time", schedule.endTime?.withSecond(0)?.withNano(0)?.toString())
            put("repeat_rule", schedule.repeatRule)
            put("urgency", schedule.urgency.value)
            put("completed", schedule.completed)
            put("created_at", schedule.createdAt)
        }.toString(),
    )

    private fun decodeSchedule(id: Long, payload: String): Schedule {
        val json = JSONObject(payloadCipher.decrypt(SCOPE_SCHEDULE, payload))
        return Schedule(
            id = id,
            title = json.getString("title"),
            notes = json.optString("notes"),
            location = json.optString("location"),
            date = json.optNullableString("date")?.let(LocalDate::parse),
            startTime = json.optNullableString("start_time")?.let(LocalTime::parse),
            endTime = json.optNullableString("end_time")?.let(LocalTime::parse),
            repeatRule = json.optString("repeat_rule", "none"),
            urgency = Urgency.fromValue(json.optInt("urgency", 0)),
            completed = json.optBoolean("completed", false),
            createdAt = json.getLong("created_at"),
        )
    }

    private fun encodeReminderLog(log: ReminderLog): String = payloadCipher.encrypt(
        SCOPE_REMINDER_LOG,
        JSONObject().apply {
            put("schedule_id", log.scheduleId)
            put("schedule_title", log.scheduleTitle)
            put("stage", log.stage)
            put("message", log.message)
            put("created_at", log.createdAt)
            put("seen", log.seen)
        }.toString(),
    )

    private fun decodeReminderLog(id: Long, payload: String): ReminderLog {
        val json = JSONObject(payloadCipher.decrypt(SCOPE_REMINDER_LOG, payload))
        return ReminderLog(
            id = id,
            scheduleId = json.getLong("schedule_id"),
            scheduleTitle = json.getString("schedule_title"),
            stage = json.getString("stage"),
            message = json.optString("message"),
            createdAt = json.getLong("created_at"),
            seen = json.optBoolean("seen", false),
        )
    }

    private fun encodeAccountEntry(entry: AccountEntry): String = payloadCipher.encrypt(
        SCOPE_ACCOUNT_ENTRY,
        JSONObject().apply {
            put("entry_type", entry.type.value)
            put("amount_cents", entry.amountCents)
            put("category", entry.category.trim())
            put("note", entry.note.trim())
            put("occurred_at", entry.occurredAt)
            put("created_at", entry.createdAt)
        }.toString(),
    )

    private fun decodeAccountEntry(id: Long, payload: String): AccountEntry {
        val json = JSONObject(payloadCipher.decrypt(SCOPE_ACCOUNT_ENTRY, payload))
        return AccountEntry(
            id = id,
            type = AccountEntryType.fromValue(json.getInt("entry_type")),
            amountCents = json.getLong("amount_cents"),
            category = json.getString("category"),
            note = json.optString("note"),
            occurredAt = json.getLong("occurred_at"),
            createdAt = json.getLong("created_at"),
        )
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
                created_at INTEGER NOT NULL,
                seen INTEGER NOT NULL DEFAULT 0,
                secure_payload TEXT
            )
            """.trimIndent()
        )
    }

    private fun createAccountEntriesTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS account_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_type INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                category TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                occurred_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                secure_payload TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_account_entries_occurred_at ON account_entries(occurred_at DESC)"
        )
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf(String::isNotBlank)
    }

    companion object {
        private const val DATABASE_NAME = "ai_memo_mobile.db"
        private const val DATABASE_VERSION = 5
        private const val SECURITY_PREFERENCES = "database_security"
        private const val KEY_DATABASE_VACUUM_REQUIRED = "vacuum_after_encryption"
        private const val SCOPE_SCHEDULE = "schedule"
        private const val SCOPE_REMINDER_LOG = "reminder_log"
        private const val SCOPE_ACCOUNT_ENTRY = "account_entry"
    }
}

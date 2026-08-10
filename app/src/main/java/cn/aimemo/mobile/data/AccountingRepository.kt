package cn.aimemo.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AccountingRepository(private val database: ScheduleDatabase) {
    private val _entries = MutableStateFlow<List<AccountEntry>>(emptyList())
    val entries: StateFlow<List<AccountEntry>> = _entries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _entries.value = database.listAccountEntries()
    }

    suspend fun save(entry: AccountEntry): AccountEntry = withContext(Dispatchers.IO) {
        require(entry.amountCents > 0) { "金额必须大于 0" }
        require(entry.category.isNotBlank()) { "请选择收支分类" }
        val saved = database.saveAccountEntry(entry)
        _entries.value = database.listAccountEntries()
        saved
    }

    suspend fun saveAll(entries: List<AccountEntry>): List<AccountEntry> = withContext(Dispatchers.IO) {
        require(entries.isNotEmpty()) { "没有可保存的账目" }
        entries.forEach { entry ->
            require(entry.amountCents > 0) { "金额必须大于 0" }
            require(entry.category.isNotBlank()) { "请选择收支分类" }
        }
        val saved = database.saveAccountEntries(entries)
        _entries.value = database.listAccountEntries()
        saved
    }

    suspend fun delete(entry: AccountEntry) = withContext(Dispatchers.IO) {
        database.deleteAccountEntry(entry.id)
        _entries.value = database.listAccountEntries()
    }

    fun clearMemory() {
        _entries.value = emptyList()
    }
}

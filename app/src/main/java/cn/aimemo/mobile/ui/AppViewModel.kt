package cn.aimemo.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.aimemo.mobile.AiMemoApplication
import cn.aimemo.mobile.data.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val schedules: List<Schedule> = emptyList(),
    val loading: Boolean = true,
    val darkMode: Boolean = false,
    val reminderLeadMinutes: Int = 30,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AiMemoApplication
    private val repository = app.repository
    private val _uiState = MutableStateFlow(
        AppUiState(
            darkMode = app.preferences.darkMode,
            reminderLeadMinutes = app.preferences.reminderLeadMinutes,
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.schedules.collect { schedules ->
                _uiState.value = _uiState.value.copy(schedules = schedules, loading = false)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refresh() }
                .onFailure { error -> _uiState.value = _uiState.value.copy(loading = false, error = error.message) }
        }
    }

    fun save(schedule: Schedule, onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.save(schedule) }
                .onSuccess { onSaved() }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    fun delete(schedule: Schedule) {
        viewModelScope.launch {
            runCatching { repository.delete(schedule) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    fun setCompleted(schedule: Schedule, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(schedule, completed) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        app.preferences.darkMode = enabled
        _uiState.value = _uiState.value.copy(darkMode = enabled)
    }

    fun setReminderLeadMinutes(minutes: Int) {
        app.preferences.reminderLeadMinutes = minutes
        _uiState.value = _uiState.value.copy(reminderLeadMinutes = minutes)
        viewModelScope.launch { repository.rescheduleAll() }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}


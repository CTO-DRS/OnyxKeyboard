package com.onyx.keyboard.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.data.CsvShortcuts
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.data.db.ShortcutEntity
import com.onyx.keyboard.data.db.WordEntity
import com.onyx.keyboard.AppGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel : ViewModel() {

    private val repo = AppGraph.repo

    /** F17 — result of the last CSV shortcut import (string res id). */
    private val _csvNote = MutableStateFlow<Int?>(null)
    val csvNote: StateFlow<Int?> = _csvNote.asStateFlow()

    fun consumeCsvNote() { _csvNote.value = null }

    val profiles: StateFlow<List<ProfileEntity>> = repo.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val activeProfile: StateFlow<ProfileEntity?> = repo.observeCurrentProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val customWords: StateFlow<List<WordEntity>> = repo.observeCurrentProfile()
        .flatMapLatest { p -> repo.observeCustomWords(p?.id ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val shortcuts: StateFlow<List<ShortcutEntity>> = repo.observeCurrentProfile()
        .flatMapLatest { p -> repo.observeShortcuts(p?.id ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    fun switchTo(id: Long) {
        viewModelScope.launch { runCatching { repo.setCurrentProfile(id) } }
    }

    fun addProfile(name: String, avatar: String) {
        if (name.isBlank()) return
        viewModelScope.launch { runCatching { repo.addProfile(name, avatar) } }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { repo.deleteProfile(id) } }
    }

    fun addWord(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch {
            val pid = activeProfile.value?.id ?: return@launch
            runCatching { repo.addCustomWord(pid, word, lang = "ar") }
        }
    }

    fun deleteWord(word: WordEntity) {
        viewModelScope.launch { runCatching { repo.deleteWord(word) } }
    }

    fun addShortcut(trigger: String, expansion: String) {
        if (trigger.isBlank() || expansion.isBlank()) return
        viewModelScope.launch {
            val pid = activeProfile.value?.id ?: return@launch
            runCatching { repo.addShortcut(pid, trigger, expansion) }
        }
    }

    fun deleteShortcut(id: Long) {
        viewModelScope.launch { runCatching { repo.deleteShortcut(id) } }
    }

    /**
     * F17 — imports shortcut rows from a personal-dictionary CSV into the
     * ACTIVE profile. The count lands as a note the screen surfaces.
     */
    fun importShortcutsCsv(csvText: String) {
        viewModelScope.launch {
            val pid = activeProfile.value?.id ?: return@launch
            val rows = runCatching { CsvShortcuts.parse(csvText) }.getOrDefault(emptyList())
            if (rows.isEmpty()) {
                _csvNote.value = com.onyx.keyboard.R.string.csv_import_empty
                return@launch
            }
            val count = runCatching { repo.importShortcutsCsv(pid, rows) }.getOrDefault(0)
            _csvNote.value = if (count > 0) com.onyx.keyboard.R.string.csv_import_done
            else com.onyx.keyboard.R.string.csv_import_empty
        }
    }
}

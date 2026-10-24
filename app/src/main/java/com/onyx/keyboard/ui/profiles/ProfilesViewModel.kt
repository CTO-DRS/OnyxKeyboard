package com.onyx.keyboard.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.data.db.ShortcutEntity
import com.onyx.keyboard.data.db.WordEntity
import com.onyx.keyboard.AppGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel : ViewModel() {

    private val repo = AppGraph.repo

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
}

package com.onyx.keyboard.ui.themes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.data.db.ThemeEntity
import com.onyx.keyboard.AppGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemesViewModel : ViewModel() {

    private val repo = AppGraph.repo

    val themes: StateFlow<List<ThemeEntity>> = repo.observeThemes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val activeThemeId: StateFlow<Long> = repo.observeCurrentProfile()
        .map { it?.selectedThemeId ?: 1L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), 1L)

    fun apply(themeId: Long) {
        viewModelScope.launch { runCatching { repo.applyTheme(themeId) } }
    }
}

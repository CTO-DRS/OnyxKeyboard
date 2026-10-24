package com.onyx.keyboard.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrivacyViewModel : ViewModel() {

    private val repo = AppGraph.repo

    private val _micGranted = MutableStateFlow(false)
    val micGranted: StateFlow<Boolean> = _micGranted.asStateFlow()

    fun wipeLearned() {
        viewModelScope.launch { runCatching { repo.wipeLearnedWords() } }
    }

    fun clearClips() {
        viewModelScope.launch { runCatching { repo.clearClips() } }
    }
}

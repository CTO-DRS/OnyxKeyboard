package com.onyx.keyboard.ui.dashboard

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.data.db.DailyStatsEntity
import com.onyx.keyboard.data.db.ProfileEntity
import com.onyx.keyboard.data.db.Totals
import com.onyx.keyboard.data.db.WordEntity
import com.onyx.keyboard.AppGraph
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ImeStatus { DISABLED, ENABLED_NOT_SELECTED, ACTIVE }

/**
 * Detects IME activation state by observing the system Secure settings,
 * and streams usage statistics from the on-device Room database.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: OnyxRepository = AppGraph.repo

    private val refreshTick = MutableStateFlow(0)
    private val _status = MutableStateFlow(ImeStatus.DISABLED)
    val status: StateFlow<ImeStatus> = _status.asStateFlow()

    init {
        val resolver = app.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshTick.value += 1
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), true, observer
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), true, observer
        )
        recompute()

        viewModelScope.launch {
            refreshTick.collect { recompute() }
        }
    }

    private fun recompute() {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val def = runCatching {
            Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        }.getOrDefault("")
        val enabled = runCatching {
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        }.getOrDefault("")
        _status.value = when {
            def.contains(app.packageName) -> ImeStatus.ACTIVE
            enabled.contains(app.packageName) -> ImeStatus.ENABLED_NOT_SELECTED
            else -> ImeStatus.DISABLED
        }
    }

    val todayStats: StateFlow<DailyStatsEntity?> = repo.observeTodayStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    val totals: StateFlow<Totals> = refreshTick
        .map { runCatching { repo.totals() }.getOrDefault(Totals()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), Totals())

    @OptIn(ExperimentalCoroutinesApi::class)
    val topWords: StateFlow<List<WordEntity>> = repo.observeCurrentProfile()
        .flatMapLatest { p -> repo.observeTopWords(p?.id ?: 0L, 8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val recentStats: StateFlow<List<DailyStatsEntity>> = repo.observeRecentStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    val profileCount: StateFlow<Int> = repo.observeProfiles()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), 0)

    val activeProfile: StateFlow<ProfileEntity?> = repo.observeCurrentProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)
}

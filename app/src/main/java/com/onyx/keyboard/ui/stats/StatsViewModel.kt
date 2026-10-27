package com.onyx.keyboard.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onyx.keyboard.AppGraph
import com.onyx.keyboard.data.repository.OnyxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Streams the weekly usage picture from Room. The displayed metric is a
 * UI-only switch — changing it re-folds the SAME rows (no re-query), and
 * the live `observeRecentStats` Flow keeps the chart in sync while the
 * keyboard writes new numbers in the background.
 */
class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: OnyxRepository = AppGraph.repo

    private val metricFlow = MutableStateFlow(StatMetric.KEYSTROKES)

    /** The metric the chart + cards currently show. */
    val metric: StateFlow<StatMetric> = metricFlow.asStateFlow()

    /** Switches the displayed counter — pure re-projection, no DB work. */
    fun pickMetric(m: StatMetric) {
        metricFlow.value = m
    }

    /** The 7-day window folded from live Room rows + the chosen metric. */
    val weekly: StateFlow<WeeklyAgg> = combine(
        repo.observeRecentStats(),
        metricFlow,
    ) { rows, m ->
        WeeklyStats.compute(rows, LocalDate.now(), m)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3000),
        WeeklyStats.compute(emptyList(), LocalDate.now(), StatMetric.KEYSTROKES),
    )
}

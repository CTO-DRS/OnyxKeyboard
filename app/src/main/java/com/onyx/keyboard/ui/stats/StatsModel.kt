package com.onyx.keyboard.ui.stats

import com.onyx.keyboard.data.db.DailyStatsEntity
import java.time.LocalDate

/**
 * WEEKLY USAGE STATS — pure computation layer behind the weekly stats
 * screen. Takes whatever days Room has (sparse: only days with activity
 * get a row), folds them into a fixed 7-day window ending today, and
 * derives the aggregates the cards render.
 *
 * Everything here is plain Kotlin + java.time so it is unit-testable
 * without Robolectric; the ViewModel only wires a Flow into it.
 */

/** Which counter the chart and cards currently show. */
enum class StatMetric {
    KEYSTROKES, WORDS, EMOJIS, VOICE, GIFS;

    /** Reads this metric from a (possibly missing) day row. */
    fun of(e: DailyStatsEntity?): Long = when (this) {
        KEYSTROKES -> e?.keystrokes ?: 0
        WORDS -> e?.words ?: 0
        EMOJIS -> e?.emojis ?: 0
        VOICE -> e?.voice ?: 0
        GIFS -> e?.gifs ?: 0
    }
}

/** One slot of the 7-day window — [stats] is null when the day had no row. */
data class DayStat(val date: LocalDate, val stats: DailyStatsEntity?) {
    fun value(metric: StatMetric): Long = metric.of(stats)
    val isToday: Boolean get() = date == LocalDate.now()
}

/** The full weekly picture the screen renders. */
data class WeeklyAgg(
    /** Exactly 7 slots, oldest first, ending today. */
    val days: List<DayStat>,
    /** Sum of the CURRENT metric over the window. */
    val total: Long,
    /** Integer daily average of the CURRENT metric (total / 7). */
    val average: Long,
    /** The best day of the CURRENT metric (null when the week is empty). */
    val best: DayStat?,
    /**
     * Consecutive ACTIVE days (keystrokes > 0) ending today — or yesterday
     * when today hasn't started yet. Streaks are measured on keystrokes
     * (the base usage signal) regardless of the displayed metric.
     */
    val streak: Int,
)

object WeeklyStats {

    /** The fixed window length. */
    const val DAYS = 7

    /**
     * Folds sparse daily rows into the 7-day window ending [today]. Missing
     * days become empty slots (all zeros) so the chart always has 7 bars.
     */
    fun compute(
        recent: List<DailyStatsEntity>,
        today: LocalDate,
        metric: StatMetric,
    ): WeeklyAgg {
        val byDay = recent.associateBy { it.day }
        val days = (DAYS - 1 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            DayStat(d, byDay[d.toString()])
        }

        val total = days.sumOf { it.value(metric) }
        val average = total / DAYS
        val best = days
            .filter { it.value(metric) > 0 }
            .maxByOrNull { it.value(metric) }

        var streak = 0
        var idx = days.lastIndex
        // A zero TODAY doesn't break a streak — the day isn't over; the
        // chain counts back from the last ACTIVE day.
        if (idx >= 0 && (days[idx].stats?.keystrokes ?: 0L) == 0L) idx--
        while (idx >= 0) {
            if ((days[idx].stats?.keystrokes ?: 0L) > 0) {
                streak++
                idx--
            } else break
        }

        return WeeklyAgg(days, total, average, best, streak)
    }
}

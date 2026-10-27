package com.onyx.keyboard

import androidx.test.core.app.ApplicationProvider
import android.os.Looper
import com.onyx.keyboard.data.db.DailyStatsEntity
import com.onyx.keyboard.ui.stats.StatMetric
import com.onyx.keyboard.ui.stats.StatsViewModel
import com.onyx.keyboard.ui.stats.WeeklyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * WEEKLY STATS pack: the pure 7-day folding (sparse rows -> full window,
 * totals/average/best/streak incl. the "today hasn't ended yet" rule), the
 * metric switch re-projection, and the live wiring Room -> ViewModel. The
 * Robolectric sandbox shares one JVM + Room DB — daily stats are wiped in
 * setUp/tearDown and every write goes through AppGraph.repo (the SAME DAO
 * the ViewModel reads), so runs are order-independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = OnyxApp::class)
class OnyxWeeklyStatsTest {

    private val today: LocalDate = LocalDate.now()
    private val dao get() = AppGraph.repo.statsDao

    @Before
    fun setUp() = runBlocking {
        AppGraph.init(ApplicationProvider.getApplicationContext<OnyxApp>())
        dao.wipe()
    }

    @After
    fun tearDown() = runBlocking {
        dao.wipe()
    }

    private suspend fun seed(day: LocalDate, ks: Long = 0, words: Long = 0, emo: Long = 0) {
        dao.ensureRow(DailyStatsEntity(day.toString()))
        if (ks > 0) dao.addKeystrokes(day.toString(), ks)
        if (words > 0) dao.addWords(day.toString(), words)
        if (emo > 0) dao.addEmojis(day.toString(), emo)
    }

    private fun dayAt(offset: Long): LocalDate = today.minusDays(offset)

    /** Pumps the paused Robolectric main looper until [cond] or timeout. */
    private fun drain(timeoutMs: Long = 5000, cond: () -> Boolean): Boolean {
        val looper = shadowOf(Looper.getMainLooper())
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            looper.idle()
            if (cond()) return true
            Thread.sleep(10)
        }
        looper.idle()
        return cond()
    }

    // ------------------------------------------------------------------ fold

    @Test
    fun foldFillsMissingDaysIntoAFixed7Window() {
        // Only 3 of the last 7 days have rows: -0, -2 and -6. The window
        // must still hold EXACTLY 7 slots oldest-first.
        val recent = listOf(
            DailyStatsEntity(dayAt(0).toString(), keystrokes = 100, words = 12),
            DailyStatsEntity(dayAt(2).toString(), keystrokes = 50),
            DailyStatsEntity(dayAt(6).toString(), keystrokes = 10, words = 2),
        )
        val agg = WeeklyStats.compute(recent, today, StatMetric.KEYSTROKES)

        assertEquals(7, agg.days.size)
        assertEquals(dayAt(6), agg.days.first().date)
        assertEquals(dayAt(0), agg.days.last().date)
        // Missing days surface as null-row slots with zero values.
        assertNull(agg.days[1].stats)
        assertEquals(0L, agg.days[1].value(StatMetric.KEYSTROKES))
        // Totals over the window: 100 + 50 + 10.
        assertEquals(160L, agg.total)
        assertEquals(22L, agg.average)      // 160 / 7, integer division
        // Best = today (100 taps).
        assertEquals(100L, agg.best?.value(StatMetric.KEYSTROKES))
        assertEquals(dayAt(0), agg.best?.date)
    }

    @Test
    fun metricSwitchReprojectsTheSameRows() {
        val recent = listOf(
            DailyStatsEntity(dayAt(0).toString(), keystrokes = 100, words = 12, emojis = 3),
            DailyStatsEntity(dayAt(1).toString(), keystrokes = 40, words = 8, emojis = 5),
        )
        val ks = WeeklyStats.compute(recent, today, StatMetric.KEYSTROKES)
        val words = WeeklyStats.compute(recent, today, StatMetric.WORDS)
        val emoji = WeeklyStats.compute(recent, today, StatMetric.EMOJIS)
        val voice = WeeklyStats.compute(recent, today, StatMetric.VOICE)

        assertEquals(140L, ks.total)
        assertEquals(20L, words.total)
        assertEquals(8L, emoji.total)
        assertEquals(0L, voice.total)      // never recorded -> all zero
        assertNull(voice.best)
    }

    @Test
    fun streakCountsBackThroughActiveDays() {
        // Today active, yesterday active, day -2 EMPTY, day -3 active.
        val recent = listOf(
            DailyStatsEntity(dayAt(0).toString(), keystrokes = 10),
            DailyStatsEntity(dayAt(1).toString(), keystrokes = 10),
            DailyStatsEntity(dayAt(3).toString(), keystrokes = 10),
        )
        val agg = WeeklyStats.compute(recent, today, StatMetric.KEYSTROKES)
        assertEquals(2, agg.streak)        // the empty -2 day breaks the chain
    }

    @Test
    fun zeroTodayDoesNotBreakTheStreak() {
        // The keyboard was used yesterday but today hasn't started yet: the
        // streak counts back from the last ACTIVE day.
        val recent = listOf(
            DailyStatsEntity(dayAt(1).toString(), keystrokes = 10),
            DailyStatsEntity(dayAt(2).toString(), keystrokes = 10),
            DailyStatsEntity(dayAt(3).toString(), keystrokes = 10),
        )
        val agg = WeeklyStats.compute(recent, today, StatMetric.KEYSTROKES)
        assertEquals(3, agg.streak)
    }

    @Test
    fun streakIsMeasuredOnKeystrokesNotTheShownMetric() {
        // Active typing days shown through the WORDS metric: the chart
        // totals zero while the streak still counts BOTH days.
        val recent = listOf(
            DailyStatsEntity(dayAt(0).toString(), keystrokes = 5),
            DailyStatsEntity(dayAt(1).toString(), keystrokes = 8),
        )
        val agg = WeeklyStats.compute(recent, today, StatMetric.WORDS)
        assertEquals(0L, agg.total)
        assertEquals(2, agg.streak)
    }

    @Test
    fun emptyWeekIsAllZerosWithNoBest() {
        val agg = WeeklyStats.compute(emptyList(), today, StatMetric.KEYSTROKES)
        assertEquals(7, agg.days.size)
        assertEquals(0L, agg.total)
        assertEquals(0L, agg.average)
        assertNull(agg.best)
        assertEquals(0, agg.streak)
    }

    // ---------------------------------------------------------------- wiring

    @Test
    fun seededRowsFlowThroughRoomIntoTheViewModel() = runBlocking {
        seed(dayAt(0), ks = 500, words = 33, emo = 4)
        seed(dayAt(1), ks = 120, words = 9)
        seed(dayAt(5), ks = 40)

        // Room returns what we wrote…
        val recent = dao.observeRecent().first()
        assertTrue(
            "recent=$recent expectedDay=${dayAt(0)}",
            recent.any { it.day == dayAt(0).toString() && it.keystrokes == 500L }
        )

        // …and the VM folds it into the same picture the screen renders.
        // WhileSubscribed only runs the upstream with an ACTIVE collector,
        // so keep one alive (Unconfined) and pump the paused Robolectric
        // main looper (viewModelScope's dispatcher) until it converges.
        val vm = StatsViewModel(ApplicationProvider.getApplicationContext())
        val collector = launch(Dispatchers.Unconfined) { vm.weekly.collect {} }
        assertTrue("vm never reached 660", drain { vm.weekly.value.total == 660L })
        val agg = vm.weekly.value
        assertEquals(7, agg.days.size)
        assertEquals(660L, agg.total)      // 500 + 120 + 40
        assertEquals(dayAt(0), agg.best?.date)
        assertEquals(2, agg.streak)        // today + yesterday, gap at -2

        // Switching the metric re-projects the same rows without the DB.
        vm.pickMetric(StatMetric.WORDS)
        assertTrue("vm never reached 42", drain { vm.weekly.value.total == 42L })
        assertEquals(42L, vm.weekly.value.total)     // 33 + 9
        collector.cancel()
    }

    @Test
    fun dayStatTodayFlagFollowsTheClock() {
        val recent = listOf(DailyStatsEntity(today.toString(), keystrokes = 5))
        val agg = WeeklyStats.compute(recent, today, StatMetric.KEYSTROKES)
        assertTrue(agg.days.last().isToday)
        assertTrue(agg.days.dropLast(1).none { it.isToday })
        assertNotNull(agg.days.last().stats)
    }

    @Test
    fun repoBumpActuallyPersistsARow() = runBlocking {
        // REGRESSION GUARD: the production bump path used a raw
        // `INSERT OR IGNORE (day)` which silently violated the generated
        // NOT NULL constraints (no SQL DEFAULTs on daily_stats) and
        // recorded NOTHING. ensureRow now goes through Room's @Insert so
        // the Kotlin defaults fill every column. This must NEVER regress.
        val repo = AppGraph.repo
        repo.bumpKeystrokes(7)
        repo.bumpWords(2)
        val row = dao.observeDay(today.toString()).first()
        assertNotNull("today row must exist after a bump", row)
        assertEquals(7L, row?.keystrokes)
        assertEquals(2L, row?.words)
        assertEquals(0L, row?.emojis)
    }
}

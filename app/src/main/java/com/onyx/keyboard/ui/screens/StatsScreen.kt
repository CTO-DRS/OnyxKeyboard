package com.onyx.keyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onyx.keyboard.R
import com.onyx.keyboard.ui.components.GlassCard
import com.onyx.keyboard.ui.stats.DayStat
import com.onyx.keyboard.ui.stats.StatMetric
import com.onyx.keyboard.ui.stats.StatsViewModel
import com.onyx.keyboard.ui.stats.WeeklyAgg
import com.onyx.keyboard.ui.theme.OnyxBlue
import com.onyx.keyboard.ui.theme.OnyxPurple
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * WEEKLY USAGE STATS — a 7-day window over the on-device daily counters:
 * a switchable-metric bar chart, four summary cards (week total, daily
 * average, best day, active streak) and a day-by-day ledger. Everything is
 * device-local by construction; the note at the bottom says so.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    vm: StatsViewModel = viewModel(),
) {
    val agg by vm.weekly.collectAsState()
    val metric by vm.metric.collectAsState()
    val metrics = StatMetric.entries

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        ScreenHeader(title = stringResource(R.string.stats_week_title), onBack = onBack)
        Text(
            text = stringResource(R.string.stats_week_desc),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ---- metric switcher
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.forEach { m ->
                MetricChip(
                    label = metricLabel(m),
                    selected = m == metric,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.pickMetric(m) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- chart + summary
        GlassCard {
            WeeklyChart(days = agg.days, metric = metric)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCell(
                    label = stringResource(R.string.stats_card_total),
                    value = formatNumber(agg.total),
                    modifier = Modifier.weight(1f),
                )
                SummaryCell(
                    label = stringResource(R.string.stats_card_avg),
                    value = formatNumber(agg.average),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCell(
                    label = stringResource(R.string.stats_card_best),
                    value = agg.best?.let { dayName(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                SummaryCell(
                    label = stringResource(R.string.stats_card_streak),
                    value = "${agg.streak} " + stringResource(R.string.stats_days_unit),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (agg.total == 0L) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stats_empty),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- day-by-day ledger (today first)
        agg.days.reversed().forEach { d ->
            DayRow(day = d, metric = metric)
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Privacy note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OnyxBlue.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stats_privacy_note),
                color = OnyxBlue.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun metricLabel(m: StatMetric): String = stringResource(
    when (m) {
        StatMetric.KEYSTROKES -> R.string.stats_metric_keystrokes
        StatMetric.WORDS -> R.string.stats_metric_words
        StatMetric.EMOJIS -> R.string.stats_metric_emoji
        StatMetric.VOICE -> R.string.stats_metric_voice
        StatMetric.GIFS -> R.string.stats_metric_gifs
    }
)

@Composable
private fun MetricChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(OnyxPurple, Color(0xFF8A5CFF)))
                else SolidColor(Color.White.copy(alpha = 0.07f))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun WeeklyChart(days: List<DayStat>, metric: StatMetric) {
    val maxV = days.maxOf { it.value(metric) }
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        days.forEach { d ->
            val v = d.value(metric)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (v > 0) formatNumber(v) else " ",
                    color = OnyxBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val frac = if (maxV == 0L) 0f else v.toFloat() / maxV
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(if (frac <= 0f) 0.02f else maxOf(frac, 0.03f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (d.isToday) Brush.verticalGradient(listOf(OnyxPurple, Color(0xFF8A5CFF)))
                                else SolidColor(OnyxBlue.copy(alpha = if (v > 0) 0.6f else 0.18f))
                            )
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = dayShortName(d),
                    color = if (d.isToday) Color.White else Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DayRow(day: DayStat, metric: StatMetric) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (day.isToday) 0.09f else 0.05f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = dayName(day),
                color = if (day.isToday) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${formatNumber(day.value(StatMetric.KEYSTROKES))} " +
                    stringResource(R.string.stats_metric_keystrokes) +
                    " · ${formatNumber(day.value(StatMetric.WORDS))} " +
                    stringResource(R.string.stats_metric_words) +
                    " · ${formatNumber(day.value(StatMetric.EMOJIS))} " +
                    stringResource(R.string.stats_metric_emoji),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
        Text(
            text = formatNumber(day.value(metric)),
            color = OnyxBlue,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun dayShortName(d: DayStat): String =
    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
private fun dayName(d: DayStat): String =
    d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
        if (d.isToday) " · " + stringResource(R.string.stats_today) else ""

private fun formatNumber(v: Long): String =
    String.format(Locale.US, "%,d", v)

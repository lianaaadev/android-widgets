package com.liana.countdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liana.countdown.data.Occasion
import com.liana.countdown.data.OccasionRepository
import com.liana.countdown.domain.Countdown
import com.liana.countdown.domain.CountdownState
import com.liana.countdown.ui.theme.BorderSubtle
import com.liana.countdown.ui.theme.SurfaceCard
import com.liana.countdown.ui.theme.TextFaint
import com.liana.countdown.ui.theme.TextPrimary
import com.liana.countdown.ui.theme.TextSecondary
import com.liana.countdown.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun OccasionListScreen(
    repository: OccasionRepository,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val occasions by repository.observeAll().collectAsState(initial = emptyList())
    val today = LocalDate.now()

    val resolved = remember(occasions, today) {
        occasions
            .map { it to Countdown.stateFor(it.date, it.recurringYearly, today) }
            .sortedBy { (_, state) -> Countdown.sortKey(state) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (resolved.isEmpty()) {
            EmptyState(onAdd = onAdd)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 24.dp, end = 24.dp, top = 62.dp, bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    Text(
                        text = "COUNTDOWN",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                item {
                    val (occasion, state) = resolved.first()
                    HeroCard(occasion, state, onClick = { onEdit(occasion.id) })
                }
                if (resolved.size > 1) {
                    item {
                        Text(
                            text = "AFTER THAT",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextFaint,
                        )
                    }
                    items(resolved.drop(1), key = { it.first.id }) { (occasion, state) ->
                        OccasionRow(occasion, state, onClick = { onEdit(occasion.id) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 36.dp)
                .size(56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add an occasion")
        }
    }
}

@Composable
private fun HeroCard(occasion: Occasion, state: CountdownState, onClick: () -> Unit) {
    val accent = Color(occasion.accentColor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state is CountdownState.Past) "ALREADY PASSED" else "NEXT UP",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = occasion.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            occasion.emoji?.let { MarkTile(it, size = 44.dp) }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = headlineFor(state),
            style = MaterialTheme.typography.displayLarge,
            color = if (state is CountdownState.Past) TextFaint else accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = unitFor(state),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.target.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun OccasionRow(occasion: Occasion, state: CountdownState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkTile(occasion.emoji ?: "·", size = 40.dp)
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occasion.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(state.target.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                    if (occasion.recurringYearly) append(" · every year")
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = shortHeadlineFor(state),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp),
            color = if (state is CountdownState.Past) TextFaint else Color(occasion.accentColor),
            maxLines = 1,
        )
    }
}

@Composable
private fun MarkTile(mark: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = mark, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "00",
            style = MaterialTheme.typography.displayLarge,
            color = Color(0xFF22222C),
        )
        Spacer(Modifier.height(36.dp))
        Text(
            text = "Nothing on the horizon.",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "A birthday, a flight, a lease running out — anything worth watching get " +
                "closer. Add one and put it on your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton(label = "Add your first occasion", onClick = onAdd)
    }
}

private fun headlineFor(state: CountdownState): String = when (state) {
    is CountdownState.Upcoming -> state.days.toString()
    is CountdownState.Today -> "TODAY"
    is CountdownState.Past -> state.daysAgo.toString()
}

private fun shortHeadlineFor(state: CountdownState): String = when (state) {
    is CountdownState.Upcoming -> state.days.toString()
    is CountdownState.Today -> "NOW"
    is CountdownState.Past -> "−${state.daysAgo}"
}

private fun unitFor(state: CountdownState): String = when (state) {
    is CountdownState.Upcoming -> "DAYS"
    is CountdownState.Today -> "IT'S TODAY"
    is CountdownState.Past -> "DAYS AGO"
}

package com.liana.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.liana.countdown.CountdownApp
import com.liana.countdown.data.Occasion
import com.liana.countdown.data.OccasionRepository
import com.liana.countdown.domain.Countdown
import com.liana.countdown.domain.CountdownState
import com.liana.countdown.ui.OccasionEditScreen
import com.liana.widgets.core.design.BorderSubtle
import com.liana.widgets.core.design.PrimaryButton
import com.liana.widgets.core.design.SecondaryButton
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.SurfaceHigh
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary
import com.liana.widgets.core.design.WidgetTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Launched by the launcher when a widget is dropped on the home screen, and again from an
 * orphaned widget that wants repointing.
 *
 * This is what makes several countdown widgets possible: each instance arrives with its own
 * `appWidgetId` and leaves with its own occasion written into its own slice of Glance state.
 */
class CountdownWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Backing out must leave no widget behind, so the cancelled result is set up front.
        setResult(RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = (application as CountdownApp).repository

        setContent {
            WidgetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(
                        repository = repository,
                        onChosen = { occasionId -> bindAndFinish(repository, occasionId) },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun bindAndFinish(repository: OccasionRepository, occasionId: Long) {
        lifecycleScope.launch {
            bindCountdownWidget(this@CountdownWidgetConfigActivity, appWidgetId, occasionId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

private const val ModePick = -1L
private const val ModeCreate = -2L

@Composable
private fun ConfigScreen(
    repository: OccasionRepository,
    onChosen: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var mode by remember { mutableLongStateOf(ModePick) }

    if (mode == ModeCreate) {
        OccasionEditScreen(
            repository = repository,
            occasionId = null,
            onSaved = onChosen,
            onCancel = { mode = ModePick },
            saveLabel = "Save and add widget",
        )
        return
    }

    val occasions by repository.observeAll().collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val resolved = remember(occasions, today) {
        occasions
            .map { it to Countdown.stateFor(it.date, it.recurringYearly, today) }
            .sortedBy { (_, state) -> Countdown.sortKey(state) }
    }

    var selected by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // Preselect whatever is happening soonest, so a single tap on "Add widget" is usually right.
    LaunchedEffect(resolved) {
        if (selected == null) selected = resolved.firstOrNull()?.first?.id
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 62.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "What should this widget count?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Each widget follows one occasion. Add as many as you like.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            items(resolved, key = { it.first.id }) { (occasion, state) ->
                PickerRow(
                    occasion = occasion,
                    state = state,
                    selected = selected == occasion.id,
                    onClick = { selected = occasion.id },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .clickable { mode = ModeCreate }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.size(13.dp))
                    Text(
                        text = "Something new",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                label = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                label = "Add widget",
                enabled = selected != null,
                color = resolved.firstOrNull { it.first.id == selected }
                    ?.first?.accentColor?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.primary,
                onClick = { selected?.let { scope.launch { onChosen(it) } } },
                modifier = Modifier.weight(1.6f),
            )
        }
    }
}

@Composable
private fun PickerRow(
    occasion: Occasion,
    state: CountdownState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(occasion.accentColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) SurfaceCard else Color(0xFF121218))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else BorderSubtle,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = occasion.emoji ?: "·", color = TextSecondary)
        }
        Spacer(Modifier.size(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occasion.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(state.target.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                    if (occasion.recurringYearly) append(" · every year")
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

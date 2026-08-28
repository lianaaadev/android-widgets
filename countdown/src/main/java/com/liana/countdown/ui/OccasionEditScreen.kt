package com.liana.countdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liana.widgets.core.design.AccentPalette
import com.liana.countdown.data.Occasion
import com.liana.countdown.data.OccasionMarks
import com.liana.countdown.data.OccasionRepository
import com.liana.countdown.domain.Countdown
import com.liana.countdown.domain.CountdownState
import com.liana.widgets.core.design.BorderSubtle
import com.liana.widgets.core.design.Ink
import com.liana.widgets.core.design.PrimaryButton
import com.liana.widgets.core.design.SecondaryButton
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.SurfaceHigh
import com.liana.widgets.core.design.TextFaint
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary
import com.liana.countdown.widget.requestPinCountdownWidget
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionEditScreen(
    repository: OccasionRepository,
    occasionId: Long?,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit = onCancel,
    saveLabel: String = "Save occasion",
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pinUnsupported by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var recurring by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf(AccentPalette.Default) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var loaded by remember { mutableStateOf(occasionId == null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(occasionId) {
        if (occasionId != null) {
            repository.getById(occasionId)?.let {
                title = it.title
                date = it.date
                recurring = it.recurringYearly
                emoji = it.emoji
                accent = it.accentColor
                createdAt = it.createdAt
            }
            loaded = true
        }
    }

    if (!loaded) return

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 62.dp, bottom = 120.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onCancel),
                )
                Spacer(Modifier.size(14.dp))
                Text(
                    text = if (occasionId == null) "New occasion" else "Edit occasion",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (occasionId != null) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete this occasion",
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                scope.launch {
                                    repository.delete(occasionId)
                                    onDeleted()
                                }
                            },
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            FieldLabel("WHAT ARE YOU WAITING FOR")
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
                cursorBrush = SolidColor(Color(accent)),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                text = "Mum's Birthday",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                                color = TextFaint,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(accent)),
            )

            Spacer(Modifier.height(24.dp))

            FieldLabel("WHEN")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "Pick a date",
                    tint = TextTertiary,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Repeats every year",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Starts again the day after",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                Switch(
                    checked = recurring,
                    onCheckedChange = { recurring = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Color(accent),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = SurfaceHigh,
                        uncheckedBorderColor = BorderSubtle,
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))

            FieldLabel("MARK")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OccasionMarks.forEach { mark ->
                    val selected = emoji == mark
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) SurfaceHigh else SurfaceCard)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(accent) else BorderSubtle,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clickable { emoji = if (selected) null else mark },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = mark, fontSize = 20.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            FieldLabel("COLOUR")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentPalette.all.forEach { swatch ->
                    val selected = accent == swatch
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) TextPrimary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { accent = swatch },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            FieldLabel("ON YOUR HOME SCREEN")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetPreview(
                    title = title.ifBlank { "Your occasion" },
                    date = date,
                    recurring = recurring,
                    accent = accent,
                )
                Spacer(Modifier.size(16.dp))
                Text(
                    text = if (occasionId == null) {
                        "This is what the widget will look like. You can place it once " +
                            "you have saved."
                    } else {
                        "Long-press your home screen to place more of these, or use the " +
                            "button below."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }

            // Only offered for an occasion that already exists — there is nothing to bind a
            // pinned widget to until the row has an id.
            if (occasionId != null) {
                Spacer(Modifier.height(20.dp))
                SecondaryButton(
                    label = "Add to home screen",
                    onClick = {
                        if (!requestPinCountdownWidget(context, occasionId)) {
                            pinUnsupported = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pinUnsupported) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "This launcher cannot place widgets for you. Long-press an " +
                            "empty spot on your home screen and pick Countdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }
        }

        PrimaryButton(
            label = saveLabel,
            enabled = title.isNotBlank(),
            color = Color(accent),
            onClick = {
                scope.launch {
                    val id = repository.save(
                        Occasion(
                            id = occasionId ?: 0L,
                            title = title.trim(),
                            date = date,
                            recurringYearly = recurring,
                            emoji = emoji,
                            accentColor = accent,
                            createdAt = createdAt,
                        ),
                    )
                    onSaved(id)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // The picker works in UTC-anchored millis; read it back the same way so
                        // the user's chosen calendar day survives whatever zone they are in.
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("Choose", color = Color(accent))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = TextFaint)
}

@Composable
private fun WidgetPreview(title: String, date: LocalDate, recurring: Boolean, accent: Int) {
    val state = Countdown.stateFor(date, recurring, LocalDate.now())
    Column(
        modifier = Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (state is CountdownState.Today) Color(accent) else SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = if (state is CountdownState.Today) Ink else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = when (state) {
                is CountdownState.Upcoming -> state.days.toString()
                is CountdownState.Today -> "TODAY"
                is CountdownState.Past -> state.daysAgo.toString()
            },
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp),
            color = if (state is CountdownState.Today) Ink else Color(accent),
            maxLines = 1,
        )
        Text(
            text = when (state) {
                is CountdownState.Past -> "days ago · ${state.target.format(DateTimeFormatter.ofPattern("d MMM"))}"
                else -> "days · ${state.target.format(DateTimeFormatter.ofPattern("d MMM"))}"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = if (state is CountdownState.Today) Ink else TextTertiary,
            maxLines = 1,
        )
    }
}

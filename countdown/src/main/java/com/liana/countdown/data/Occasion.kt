package com.liana.countdown.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.liana.widgets.core.design.AccentPalette
import java.time.LocalDate

@Entity(tableName = "occasions")
data class Occasion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val date: LocalDate,
    @ColumnInfo(name = "recurring_yearly") val recurringYearly: Boolean = false,
    val emoji: String? = null,
    @ColumnInfo(name = "accent_color") val accentColor: Int = AccentPalette.Default,
    /**
     * Tombstone rather than a hard delete. Widgets are bound to an occasion id and outlive the
     * app's own screens; keeping the row lets an orphaned widget say what happened and offer to
     * be reconfigured, instead of rendering blank.
     */
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** The marks offered in the editor. Null means "no mark". */
val OccasionMarks = listOf("🎂", "🎈", "✈️", "💍", "🔑", "🎫", "🎓", "🏡", "❤️", "🎄")

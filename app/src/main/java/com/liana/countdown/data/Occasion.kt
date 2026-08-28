package com.liana.countdown.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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

/**
 * The accent a user can pick per occasion. Shared by the app and the widget, so it lives here as
 * plain ARGB ints rather than as Compose or Glance colours.
 */
object AccentPalette {
    val Amber = 0xFFFFB43C.toInt()
    val Coral = 0xFFFF7A6B.toInt()
    val Pink = 0xFFFF8FC4.toInt()
    val Violet = 0xFFB79BFF.toInt()
    val Cyan = 0xFF5BD1EA.toInt()
    val Lime = 0xFFA9E05A.toInt()

    val Default = Amber

    val all = listOf(Amber, Coral, Pink, Violet, Cyan, Lime)
}

/** The marks offered in the editor. Null means "no mark". */
val OccasionMarks = listOf("🎂", "🎈", "✈️", "💍", "🔑", "🎫", "🎓", "🏡", "❤️", "🎄")

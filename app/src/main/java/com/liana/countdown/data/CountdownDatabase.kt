package com.liana.countdown.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    /** Stored as an epoch day, not a timestamp: an occasion is a date, not a moment. */
    @TypeConverter
    fun toEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}

@Database(entities = [Occasion::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class CountdownDatabase : RoomDatabase() {

    abstract fun occasionDao(): OccasionDao

    companion object {
        @Volatile
        private var instance: CountdownDatabase? = null

        fun get(context: Context): CountdownDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CountdownDatabase::class.java,
                "countdown.db",
            ).build().also { instance = it }
        }
    }
}

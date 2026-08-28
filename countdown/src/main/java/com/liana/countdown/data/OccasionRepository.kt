package com.liana.countdown.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.liana.countdown.widget.CountdownWidget
import kotlinx.coroutines.flow.Flow

/**
 * Every write refreshes the widgets. The countdown itself only changes at midnight, but the
 * title, colour and date can change at any moment, and a widget showing last minute's name is
 * the kind of thing people notice immediately.
 */
class OccasionRepository(
    private val dao: OccasionDao,
    private val appContext: Context,
) {

    fun observeAll(): Flow<List<Occasion>> = dao.observeAll()

    suspend fun getById(id: Long): Occasion? = dao.getById(id)

    fun observeById(id: Long): Flow<Occasion?> = dao.observeById(id)

    suspend fun save(occasion: Occasion): Long {
        val id = if (occasion.id == 0L) {
            dao.insert(occasion)
        } else {
            dao.update(occasion)
            occasion.id
        }
        CountdownWidget().updateAll(appContext)
        return id
    }

    suspend fun delete(id: Long) {
        dao.softDelete(id)
        CountdownWidget().updateAll(appContext)
    }
}

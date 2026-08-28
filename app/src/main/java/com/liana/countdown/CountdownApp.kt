package com.liana.countdown

import android.app.Application
import com.liana.countdown.data.CountdownDatabase
import com.liana.countdown.data.OccasionRepository
import com.liana.countdown.work.MidnightAlarmScheduler

class CountdownApp : Application() {

    val repository: OccasionRepository by lazy {
        OccasionRepository(CountdownDatabase.get(this).occasionDao(), this)
    }

    override fun onCreate() {
        super.onCreate()
        MidnightAlarmScheduler.schedule(this)
    }
}

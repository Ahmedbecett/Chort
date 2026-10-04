package com.example

import android.app.Application
import com.example.data.repository.TokPulseRepository

class TokPulseApplication : Application() {

    lateinit var repository: TokPulseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = TokPulseRepository(this)
    }

    companion object {
        lateinit var instance: TokPulseApplication
            private set
    }
}

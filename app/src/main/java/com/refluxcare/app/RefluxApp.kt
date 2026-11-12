package com.refluxcare.app

import android.app.Application
import com.refluxcare.app.data.AppDatabase
import com.refluxcare.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RefluxApp : Application() {
    lateinit var repo: Repository
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.build(this))
    }
}

package com.refluxcare.app

import android.app.Application
import com.refluxcare.app.data.AppDatabase
import com.refluxcare.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.refluxcare.app.data.MeditationSession
import com.refluxcare.app.meditation.MeditationController
import com.refluxcare.app.util.T

class RefluxApp : Application() {
    lateinit var repo: Repository
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.build(this))
        MeditationController.onSessionEnded = { r ->
            scope.launch {
                repo.saveMeditation(
                    MeditationSession(
                        date = T.key(T.toLdt(r.startedAt).toLocalDate()),
                        startTime = r.startedAt,
                        plannedMin = r.plannedMin,
                        actualSec = r.actualSec,
                        sound = r.soundName,
                        completed = r.completed
                    )
                )
            }
        }
    }
}

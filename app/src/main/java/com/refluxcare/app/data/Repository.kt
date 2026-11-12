package com.refluxcare.app.data

import android.content.Context
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.RuleEngine
import com.refluxcare.app.domain.SleepTracker
import com.refluxcare.app.util.T
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime

class Repository(db: AppDatabase) {

    val dao: AppDao = db.dao()
    private val refreshLock = Mutex()

    // ---------------------------------------------------------------- Writes (each re-checks the rules)
    suspend fun saveMeal(m: MealLog) { dao.upsertMeal(m); reevaluate(m.date) }
    suspend fun deleteMeal(m: MealLog) { dao.deleteMeal(m); reevaluate(m.date) }

    suspend fun saveSleep(s: SleepRecord) { dao.upsertSleep(s); reevaluateAround(s.date) }
    suspend fun deleteSleep(s: SleepRecord) { dao.deleteSleep(s); reevaluateAround(s.date) }

    suspend fun saveWorkout(w: WorkoutLog) { dao.insertWorkout(w); reevaluate(w.date) }
    suspend fun deleteWorkout(w: WorkoutLog) { dao.deleteWorkout(w); reevaluate(w.date) }

    suspend fun setHabit(date: String, habitId: String, done: Boolean) {
        dao.upsertHabit(HabitCheck(date, habitId, done)); reevaluate(date)
    }

    suspend fun setSymptom(s: SymptomLog) { dao.upsertSymptom(s); reevaluate(s.date) }

    suspend fun addManualBreak(date: String, ruleId: String, detail: String) {
        dao.insertBreak(
            RuleBreak(
                date = date,
                ruleId = ruleId,
                title = Content.ruleTitle(ruleId),
                detail = detail,
                source = "manual",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBreak(b: RuleBreak) = dao.deleteBreak(b)

    suspend fun saveMeditation(m: MeditationSession) { dao.insertMeditation(m) }
    suspend fun deleteMeditation(m: MeditationSession) { dao.deleteMeditation(m) }

    suspend fun addFood(f: CustomFood) = dao.insertFood(f)
    suspend fun deleteFood(f: CustomFood) = dao.deleteFood(f)

    // ---------------------------------------------------------------- Rule evaluation
    suspend fun reevaluate(date: String) {
        val day = T.parse(date)
        val meals = dao.mealsOn(date)
        val workouts = dao.workoutsOn(date)
        val hasActivity = meals.isNotEmpty() || workouts.isNotEmpty() ||
            dao.habitsOn(date).isNotEmpty() || dao.symptomOn(date) != null
        val list = RuleEngine.evaluate(
            day = day,
            meals = meals,
            wakeRec = dao.sleepOn(date),
            nightRec = dao.sleepOn(T.key(day.plusDays(1))),
            workouts = workouts,
            hasActivity = hasActivity,
            today = T.today()
        )
        dao.replaceAuto(date, list)
    }

    /** A sleep record keyed by its wake-up day affects that day and the evening before. */
    suspend fun reevaluateAround(date: String) {
        reevaluate(date)
        reevaluate(T.key(T.parse(date).minusDays(1)))
    }

    // ---------------------------------------------------------------- Sleep detection
    /** Detects sleep for the last 10 nights from screen activity. Manual edits are never overwritten. */
    suspend fun syncSleep(context: Context): Int {
        if (!SleepTracker.hasPermission(context)) return 0
        var changed = 0
        val today = T.today()
        for (i in 0..9) {
            val day = today.minusDays(i.toLong())
            if (i == 0 && LocalTime.now().hour < 5) continue
            val key = T.key(day)
            val existing = dao.sleepOn(key)
            if (existing?.source == "manual") continue
            val found = SleepTracker.detect(context, day) ?: continue
            if (existing == null || existing.sleepStart != found.first || existing.wakeTime != found.second) {
                dao.upsertSleep(SleepRecord(key, found.first, found.second, "auto"))
                changed++
            }
        }
        return changed
    }

    /** Run when the app opens: detect sleep, then re-check the last 8 days. */
    suspend fun dailyRefresh(context: Context) {
        if (refreshLock.isLocked) return
        refreshLock.withLock {
            runCatching { syncSleep(context) }
            for (i in 0..7) reevaluate(T.key(T.today().minusDays(i.toLong())))
        }
    }
}

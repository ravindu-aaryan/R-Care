package com.refluxcare.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.refluxcare.app.domain.Content
import com.refluxcare.app.util.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Exports everything as JSON (full data) and CSV (one row per day, for spreadsheets). */
object Exporter {

    suspend fun export(context: Context, dao: AppDao): Intent {
        val meals = dao.allMeals()
        val sleep = dao.allSleep()
        val workouts = dao.allWorkouts()
        val symptoms = dao.allSymptoms()
        val habits = dao.allHabits()
        val breaks = dao.allBreaks()
        val foods = dao.allCustomFoods()
        val meditations = dao.allMeditations()

        return withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("exportedAt", T.toLdt(System.currentTimeMillis()).toString())
            root.put("meals", JSONArray().apply {
                meals.forEach {
                    put(JSONObject()
                        .put("date", it.date).put("mealType", it.mealType).put("time", T.time(it.time))
                        .put("items", it.items).put("portion", it.portion).put("tags", it.tags).put("note", it.note))
                }
            })
            root.put("sleep", JSONArray().apply {
                sleep.forEach {
                    put(JSONObject()
                        .put("wakeDate", it.date)
                        .put("sleepStart", T.toLdt(it.sleepStart).toString())
                        .put("wakeTime", T.toLdt(it.wakeTime).toString())
                        .put("hours", "%.2f".format((it.wakeTime - it.sleepStart) / 3_600_000.0))
                        .put("source", it.source))
                }
            })
            root.put("workouts", JSONArray().apply {
                workouts.forEach {
                    put(JSONObject()
                        .put("date", it.date).put("type", it.type).put("time", T.time(it.time))
                        .put("durationMin", it.durationMin).put("steps", it.steps).put("details", it.details))
                }
            })
            root.put("symptoms", JSONArray().apply {
                symptoms.forEach {
                    put(JSONObject().put("date", it.date).put("severity", it.severity).put("night", it.night).put("note", it.note))
                }
            })
            root.put("habits", JSONArray().apply {
                habits.forEach { put(JSONObject().put("date", it.date).put("habit", it.habitId).put("done", it.done)) }
            })
            root.put("ruleBreaks", JSONArray().apply {
                breaks.forEach {
                    put(JSONObject()
                        .put("date", it.date).put("rule", it.title).put("detail", it.detail).put("source", it.source))
                }
            })
            root.put("customFoods", JSONArray().apply {
                foods.forEach { put(JSONObject().put("name", it.name).put("mealType", it.mealType).put("note", it.note)) }
            })
            root.put("meditations", JSONArray().apply {
                meditations.forEach {
                    put(JSONObject()
                        .put("date", it.date).put("time", T.time(it.startTime))
                        .put("plannedMin", it.plannedMin).put("actualSec", it.actualSec)
                        .put("minutes", Math.round(it.actualSec / 60.0 * 10) / 10.0)
                        .put("sound", it.sound).put("completed", it.completed))
                }
            })

            // Daily CSV
            val allDates = (meals.map { it.date } + sleep.map { it.date } + workouts.map { it.date } +
                symptoms.map { it.date } + habits.map { it.date } + breaks.map { it.date } +
                meditations.map { it.date }).toSortedSet()
            val sleepBy = sleep.associateBy { it.date }
            val symBy = symptoms.associateBy { it.date }
            val csv = StringBuilder()
            csv.append("date,weekday,bedtime_prev_night,wake_time,sleep_hours,habits_done,habits_total,symptom,night_symptoms,meals,rule_breaks,rules,workouts,walk_steps,meditation_min\n")
            for (d in allDates) {
                val s = sleepBy[d]
                val sym = symBy[d]
                val dayBreaks = breaks.filter { it.date == d }
                val dayWorkouts = workouts.filter { it.date == d }
                csv.append(listOf(
                    d,
                    LocalDate.parse(d).dayOfWeek.name.take(3),
                    s?.let { T.time(it.sleepStart) } ?: "",
                    s?.let { T.time(it.wakeTime) } ?: "",
                    s?.let { "%.2f".format((it.wakeTime - it.sleepStart) / 3_600_000.0) } ?: "",
                    habits.count { it.date == d && it.done }.toString(),
                    Content.habits.size.toString(),
                    sym?.severity?.toString() ?: "",
                    sym?.night?.toString() ?: "",
                    meals.count { it.date == d }.toString(),
                    dayBreaks.size.toString(),
                    dayBreaks.joinToString(" | ") { it.title },
                    dayWorkouts.joinToString(" | ") { it.type },
                    dayWorkouts.sumOf { it.steps }.toString(),
                    "%.1f".format(Locale.US, meditations.filter { it.date == d }.sumOf { it.actualSec } / 60.0)
                ).joinToString(",") { csvCell(it) }).append('\n')
            }

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = T.todayKey()
            val jsonFile = File(dir, "refluxcare-$stamp.json").apply { writeText(root.toString(2)) }
            val csvFile = File(dir, "refluxcare-daily-$stamp.csv").apply { writeText(csv.toString()) }

            val authority = context.packageName + ".fileprovider"
            val uris = arrayListOf<Uri>(
                FileProvider.getUriForFile(context, authority, csvFile),
                FileProvider.getUriForFile(context, authority, jsonFile)
            )
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = ClipData.newRawUri("export", uris[0]).apply { addItem(ClipData.Item(uris[1])) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun csvCell(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

package com.refluxcare.app.domain

import com.refluxcare.app.data.HabitCheck
import com.refluxcare.app.data.MeditationSession
import com.refluxcare.app.data.MealLog
import com.refluxcare.app.data.RuleBreak
import com.refluxcare.app.data.SleepRecord
import com.refluxcare.app.data.SymptomLog
import com.refluxcare.app.data.WorkoutLog
import com.refluxcare.app.data.tagList
import com.refluxcare.app.util.T
import java.time.LocalDate
import kotlin.math.roundToInt

data class Trigger(val title: String, val withAvg: Float, val withoutAvg: Float, val days: Int)

data class InsightData(
    val days: List<LocalDate>,
    val habitPct: List<Float?>,
    val sleepHours: List<Float?>,
    val symptoms: List<Float?>,
    val breaksPerDay: List<Float?>,
    val avgHabitPct: Float?,
    val avgBed: Int?,
    val avgWake: Int?,
    val avgSleepMin: Int?,
    val nightsTracked: Int,
    val onTimeBed: Int,
    val onTimeWake: Int,
    val avgSymptom: Float?,
    val symptomDays: Int,
    val symptomFreeDays: Int,
    val nightSymptomDays: Int,
    val totalBreaks: Int,
    val topBreaks: List<Pair<String, Int>>,
    val triggers: List<Trigger>,
    val dumbbell: Int,
    val yoga: Int,
    val walks: Int,
    val plannedDumbbell: Int,
    val plannedYoga: Int,
    val steps: Int,
    val tagCounts: List<Pair<String, Int>>,
    val messages: List<String>,
    val meditationMin: List<Float?> = emptyList(),
    val totalMeditationMin: Int = 0,
    val meditationDays: Int = 0,
    val meditationStreak: Int = 0,
    val symptomsWithMeditation: Float? = null,
    val symptomsWithoutMeditation: Float? = null
)

object InsightsCalc {

    fun compute(
        from: LocalDate,
        to: LocalDate,
        meals: List<MealLog>,
        sleeps: List<SleepRecord>,
        workouts: List<WorkoutLog>,
        symptoms: List<SymptomLog>,
        habits: List<HabitCheck>,
        breaks: List<RuleBreak>,
        meditations: List<MeditationSession> = emptyList()
    ): InsightData {
        val today = T.today()
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val keys = days.map { T.key(it) }

        // Habits
        val habitsByDate = habits.groupBy { it.date }
        val habitPct = keys.map { k ->
            val list = habitsByDate[k] ?: return@map null
            if (list.isEmpty()) null else list.count { it.done } * 100f / Content.habits.size
        }
        val trackedHabitDays = habitPct.filterNotNull()
        val avgHabitPct = if (trackedHabitDays.isEmpty()) null else trackedHabitDays.average().toFloat()

        // Sleep
        val sleepByDate = sleeps.associateBy { it.date }
        val sleepHours = keys.map { k ->
            sleepByDate[k]?.let { (it.wakeTime - it.sleepStart) / 3_600_000f }
        }
        val inRangeSleeps = keys.mapNotNull { sleepByDate[it] }
        val avgBed = if (inRangeSleeps.isEmpty()) null else inRangeSleeps.map {
            val m = T.minutesOfDay(it.sleepStart)
            if (m < 12 * 60) m + 1440 else m
        }.average().roundToInt()
        val avgWake = if (inRangeSleeps.isEmpty()) null else inRangeSleeps.map { T.minutesOfDay(it.wakeTime) }.average().roundToInt()
        val avgSleepMin = if (inRangeSleeps.isEmpty()) null else inRangeSleeps.map { (it.wakeTime - it.sleepStart) / 60_000.0 }.average().roundToInt()
        val onTimeBed = inRangeSleeps.count {
            it.sleepStart <= T.millis(T.parse(it.date).minusDays(1), Content.BEDTIME_LIMIT_H, 0)
        }
        val onTimeWake = inRangeSleeps.count {
            it.wakeTime <= T.millis(T.parse(it.date), Content.WAKE_LIMIT_H, Content.WAKE_LIMIT_M)
        }

        // Symptoms
        val symByDate = symptoms.associateBy { it.date }
        val symptomSeries = keys.map { k -> symByDate[k]?.severity?.toFloat() }
        val symInRange = keys.mapNotNull { symByDate[it] }
        val avgSymptom = if (symInRange.isEmpty()) null else symInRange.map { it.severity }.average().toFloat()

        // Rule breaks
        val breaksByDate = breaks.groupBy { it.date }
        val breaksPerDay = days.mapIndexed { i, d ->
            if (d.isAfter(today)) null else (breaksByDate[keys[i]]?.size ?: 0).toFloat()
        }
        val topBreaks = breaks.groupBy { it.title }.map { it.key to it.value.size }
            .sortedByDescending { it.second }.take(6)

        // Triggers: symptoms on the same or next day after a rule break vs. other days
        val triggers = mutableListOf<Trigger>()
        if (symInRange.size >= 4) {
            for ((ruleId, list) in breaks.groupBy { it.ruleId }) {
                val breakDays = list.map { T.parse(it.date) }.toSet()
                val affected = (breakDays + breakDays.map { it.plusDays(1) }).map { T.key(it) }.toSet()
                val with = symInRange.filter { it.date in affected }.map { it.severity }
                val without = symInRange.filter { it.date !in affected }.map { it.severity }
                if (with.size >= 2 && without.size >= 2) {
                    val a = with.average().toFloat()
                    val b = without.average().toFloat()
                    if (a - b >= 0.3f) triggers += Trigger(Content.ruleTitle(ruleId), a, b, breakDays.size)
                }
            }
        }
        triggers.sortByDescending { it.withAvg - it.withoutAvg }

        // Workouts
        val dumbbell = workouts.count { it.type == WorkoutTypes.DUMBBELL }
        val yoga = workouts.count { it.type == WorkoutTypes.YOGA }
        val walks = workouts.count { it.type == WorkoutTypes.WALK }
        val pastDays = days.filter { !it.isAfter(today) }
        val plannedDumbbell = pastDays.count { Content.plannedWorkout(it.dayOfWeek) == WorkoutTypes.DUMBBELL }
        val plannedYoga = pastDays.count { Content.plannedWorkout(it.dayOfWeek) == WorkoutTypes.YOGA }
        val steps = workouts.sumOf { it.steps }

        // Meal tags
        val tagCounts = meals.flatMap { it.tagList() }.groupBy { it }
            .map { Tags.label(it.key) to it.value.size }.sortedByDescending { it.second }

        // Meditation
        val medSecByDate = meditations.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.actualSec } }
        val meditationMin = days.mapIndexed { i, d ->
            if (d.isAfter(today)) null else (medSecByDate[keys[i]] ?: 0) / 60f
        }
        val totalMeditationMin = keys.sumOf { medSecByDate[it] ?: 0 } / 60
        val meditationDays = keys.count { (medSecByDate[it] ?: 0) >= 60 }
        var meditationStreak = 0
        run {
            var idx = keys.lastIndex
            if (idx >= 0 && (medSecByDate[keys[idx]] ?: 0) < 60) idx--
            while (idx >= 0 && (medSecByDate[keys[idx]] ?: 0) >= 60) { meditationStreak++; idx-- }
        }
        val withMed = symInRange.filter { (medSecByDate[it.date] ?: 0) >= 300 }.map { it.severity }
        val withoutMed = symInRange.filter { (medSecByDate[it.date] ?: 0) < 300 }.map { it.severity }
        val symptomsWithMeditation = if (withMed.size >= 2 && withoutMed.size >= 2) withMed.average().toFloat() else null
        val symptomsWithoutMeditation = if (withMed.size >= 2 && withoutMed.size >= 2) withoutMed.average().toFloat() else null

        // Plain-language messages
        val msgs = mutableListOf<String>()
        if (avgSleepMin != null) {
            msgs += "Average sleep ${T.duration(avgSleepMin.toLong())} over ${inRangeSleeps.size} nights (target 7.5–8h)."
            msgs += "Asleep by 10 pm on $onTimeBed of ${inRangeSleeps.size} nights; up by 6:30 am on $onTimeWake."
        } else {
            msgs += "No sleep data yet. Grant usage access on the Sleep page for automatic tracking."
        }
        if (triggers.isNotEmpty()) {
            val t = triggers.first()
            msgs += "Your symptoms are worse on and after days with \"${t.title}\" " +
                "(avg ${"%.1f".format(t.withAvg)} vs ${"%.1f".format(t.withoutAvg)})."
        } else if (symInRange.size < 5) {
            msgs += "Log your symptoms every day — after about a week the app can show which habits trigger them."
        }
        if (avgHabitPct != null) msgs += "You completed ${avgHabitPct.roundToInt()}% of daily habits on tracked days."
        if (topBreaks.isNotEmpty()) msgs += "Most common rule break: ${topBreaks.first().first} (${topBreaks.first().second}×)."
        val planned = plannedDumbbell + plannedYoga
        if (planned > 0) msgs += "Workouts: ${dumbbell + yoga} done of $planned planned; $walks walks logged."
        if (avgSymptom != null) {
            msgs += "Average symptom score ${"%.1f".format(avgSymptom)} / 3 — ${symInRange.count { it.severity == 0 }} symptom-free days."
        }

        if (totalMeditationMin > 0 || meditationDays > 0) {
            msgs += "You meditated on $meditationDays of ${days.size} days ($totalMeditationMin min total)."
        }
        if (symptomsWithMeditation != null && symptomsWithoutMeditation != null) {
            msgs += "Symptoms averaged ${"%.1f".format(symptomsWithMeditation)} on meditation days vs " +
                "${"%.1f".format(symptomsWithoutMeditation)} on other days."
        }

        return InsightData(
            days = days,
            habitPct = habitPct,
            sleepHours = sleepHours,
            symptoms = symptomSeries,
            breaksPerDay = breaksPerDay,
            avgHabitPct = avgHabitPct,
            avgBed = avgBed,
            avgWake = avgWake,
            avgSleepMin = avgSleepMin,
            nightsTracked = inRangeSleeps.size,
            onTimeBed = onTimeBed,
            onTimeWake = onTimeWake,
            avgSymptom = avgSymptom,
            symptomDays = symInRange.size,
            symptomFreeDays = symInRange.count { it.severity == 0 },
            nightSymptomDays = symInRange.count { it.night },
            totalBreaks = breaks.size,
            topBreaks = topBreaks,
            triggers = triggers.take(5),
            dumbbell = dumbbell,
            yoga = yoga,
            walks = walks,
            plannedDumbbell = plannedDumbbell,
            plannedYoga = plannedYoga,
            steps = steps,
            tagCounts = tagCounts,
            messages = msgs,
            meditationMin = meditationMin,
            totalMeditationMin = totalMeditationMin,
            meditationDays = meditationDays,
            meditationStreak = meditationStreak,
            symptomsWithMeditation = symptomsWithMeditation,
            symptomsWithoutMeditation = symptomsWithoutMeditation
        )
    }
}

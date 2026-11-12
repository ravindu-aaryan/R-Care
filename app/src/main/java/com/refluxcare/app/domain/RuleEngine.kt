package com.refluxcare.app.domain

import com.refluxcare.app.data.MealLog
import com.refluxcare.app.data.RuleBreak
import com.refluxcare.app.data.SleepRecord
import com.refluxcare.app.data.WorkoutLog
import com.refluxcare.app.data.tagList
import com.refluxcare.app.util.T
import java.time.LocalDate

/**
 * Checks one day against the routine rules and returns the automatically detected rule breaks.
 *
 * @param wakeRec   sleep record that ended on the morning of [day] (keyed by [day])
 * @param nightRec  sleep record for the night after [day] (keyed by day + 1)
 * @param hasActivity true if anything was logged that day (so "missed" rules aren't raised for unused days)
 */
object RuleEngine {

    fun evaluate(
        day: LocalDate,
        meals: List<MealLog>,
        wakeRec: SleepRecord?,
        nightRec: SleepRecord?,
        workouts: List<WorkoutLog>,
        hasActivity: Boolean,
        today: LocalDate
    ): List<RuleBreak> {
        if (day.isAfter(today)) return emptyList()

        val out = mutableListOf<RuleBreak>()
        val date = T.key(day)
        val now = System.currentTimeMillis()

        fun add(ruleId: String, detail: String) {
            out += RuleBreak(
                date = date,
                ruleId = ruleId,
                title = Content.ruleTitle(ruleId),
                detail = detail,
                source = "auto",
                createdAt = now
            )
        }

        // ---- Food rules ----
        for (m in meals) {
            val tags = m.tagList()
            val at = "${MealTypes.label(m.mealType)} at ${T.time(m.time)}"
            val what = if (m.items.isBlank()) at else "$at — ${m.items}"

            if ("TEA" in tags && m.mealType == MealTypes.EARLY) add(RuleIds.TEA_EMPTY, at)
            if ("FRIED" in tags) add(RuleIds.FRIED, what)
            if ("PICKLE" in tags) add(RuleIds.PICKLE, what)
            if ("ALCOHOL" in tags) add(RuleIds.ALCOHOL, what)
            if ("FIZZY" in tags) add(RuleIds.FIZZY, what)
            if ("OUTSIDE" in tags) add(RuleIds.OUTSIDE, what)
            if (m.portion == Portions.HEAVY) add(RuleIds.OVEREAT, what)

            if (m.mealType == MealTypes.DINNER) {
                val mins = T.minutesOfDay(m.time)
                if (mins > Content.DINNER_LIMIT_MIN) {
                    add(RuleIds.LATE_DINNER, "Dinner at ${T.time(m.time)} (target 6:30–7:00 pm)")
                }
                val heavy = tags.filter { it in listOf("SPICY", "BIRYANI", "CURD", "PICKLE") }
                if (heavy.isNotEmpty()) {
                    add(RuleIds.HEAVY_DINNER, heavy.joinToString(", ") { Tags.label(it) } + " at dinner")
                }
            }
        }

        // ---- Food-to-sleep gap, bedtime, sleep length (night after this day) ----
        if (nightRec != null) {
            val lastFood = meals.maxOfOrNull { it.time }
            if (lastFood != null) {
                val gapMin = (nightRec.sleepStart - lastFood) / T.MINUTE
                if (gapMin < Content.MEAL_GAP_HOURS * 60) {
                    val gapText = if (gapMin < 0) "food logged after sleep start" else "${T.duration(gapMin)} gap"
                    add(
                        RuleIds.MEAL_GAP,
                        "Last food ${T.time(lastFood)}, asleep ${T.time(nightRec.sleepStart)} ($gapText)"
                    )
                }
            }
            if (nightRec.sleepStart > T.millis(day, Content.BEDTIME_LIMIT_H, 0)) {
                add(RuleIds.LATE_SLEEP, "Asleep at ${T.time(nightRec.sleepStart)}")
            }
            val sleptMin = (nightRec.wakeTime - nightRec.sleepStart) / T.MINUTE
            if (sleptMin < Content.MIN_SLEEP_HOURS * 60) {
                add(RuleIds.SHORT_SLEEP, "Slept ${T.duration(sleptMin)}")
            }
        }

        // ---- Wake-up time (morning of this day) ----
        if (wakeRec != null && wakeRec.wakeTime > T.millis(day, Content.WAKE_LIMIT_H, Content.WAKE_LIMIT_M)) {
            add(RuleIds.LATE_WAKE, "Woke at ${T.time(wakeRec.wakeTime)}")
        }

        // ---- Exercise too soon after food ----
        for (w in workouts) {
            if (w.type == WorkoutTypes.WALK) continue
            val recent = meals
                .filter { it.mealType != MealTypes.EARLY && it.time <= w.time && w.time - it.time < Content.EXERCISE_GAP_HOURS * T.HOUR }
                .maxByOrNull { it.time }
            if (recent != null) {
                val mins = (w.time - recent.time) / T.MINUTE
                add(
                    RuleIds.EXERCISE_AFTER_MEAL,
                    "${WorkoutTypes.label(w.type)} at ${T.time(w.time)}, $mins min after ${MealTypes.label(recent.mealType).lowercase()}"
                )
            }
        }

        // ---- Missed workout / walk (only for finished days that were tracked) ----
        if (day.isBefore(today) && hasActivity) {
            val planned = Content.plannedWorkout(day.dayOfWeek)
            if (planned != null && workouts.none { it.type == planned }) {
                add(RuleIds.MISSED_WORKOUT, "Planned: ${WorkoutTypes.label(planned)}")
            }
            if (workouts.none { it.type == WorkoutTypes.WALK }) {
                add(RuleIds.MISSED_WALK, "No walk logged")
            }
        }

        return out
    }
}

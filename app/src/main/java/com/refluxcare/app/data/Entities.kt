package com.refluxcare.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A meal or drink. tags is a comma separated list of Tags ids (e.g. "FRIED,SPICY"). */
@Entity(tableName = "meals")
data class MealLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String,
    val time: Long,
    val items: String,
    val portion: String,
    val tags: String,
    val note: String = ""
)

fun MealLog.tagList(): List<String> = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** One night of sleep. date = the day you WOKE UP. */
@Entity(tableName = "sleep")
data class SleepRecord(
    @PrimaryKey val date: String,
    val sleepStart: Long,
    val wakeTime: Long,
    val source: String // "auto" or "manual"
)

@Entity(tableName = "workouts")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String, // DUMBBELL, YOGA, WALK
    val time: Long,
    val durationMin: Int,
    val steps: Int = 0,
    val details: String = ""
)

/** Daily reflux symptom score: 0 none, 1 mild, 2 moderate, 3 severe. */
@Entity(tableName = "symptoms")
data class SymptomLog(
    @PrimaryKey val date: String,
    val severity: Int,
    val night: Boolean,
    val note: String = ""
)

@Entity(tableName = "habits", primaryKeys = ["date", "habitId"])
data class HabitCheck(
    val date: String,
    val habitId: String,
    val done: Boolean
)

@Entity(tableName = "rule_breaks")
data class RuleBreak(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val ruleId: String,
    val title: String,
    val detail: String,
    val source: String, // "auto" or "manual"
    val createdAt: Long
)

@Entity(tableName = "custom_foods")
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: String, // a MealTypes id or "ANY"
    val note: String = ""
)

/** A meditation session. date = yyyy-MM-dd of start; sound = display name ("Rain", "Silence"). */
@Entity(tableName = "meditations")
data class MeditationSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startTime: Long,
    val plannedMin: Int,
    val actualSec: Int,
    val sound: String,
    val completed: Boolean
)

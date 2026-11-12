package com.refluxcare.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppDao {

    // ---- Meals ----
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY time")
    abstract fun mealsFor(date: String): Flow<List<MealLog>>

    @Query("SELECT * FROM meals WHERE date BETWEEN :from AND :to ORDER BY time")
    abstract fun mealsBetween(from: String, to: String): Flow<List<MealLog>>

    @Query("SELECT * FROM meals WHERE date = :date ORDER BY time")
    abstract suspend fun mealsOn(date: String): List<MealLog>

    @Query("SELECT * FROM meals ORDER BY time")
    abstract suspend fun allMeals(): List<MealLog>

    @Upsert
    abstract suspend fun upsertMeal(m: MealLog)

    @Delete
    abstract suspend fun deleteMeal(m: MealLog)

    // ---- Sleep ----
    @Query("SELECT * FROM sleep WHERE date = :date")
    abstract fun sleepFor(date: String): Flow<SleepRecord?>

    @Query("SELECT * FROM sleep WHERE date = :date")
    abstract suspend fun sleepOn(date: String): SleepRecord?

    @Query("SELECT * FROM sleep WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    abstract fun sleepBetween(from: String, to: String): Flow<List<SleepRecord>>

    @Query("SELECT * FROM sleep ORDER BY date")
    abstract suspend fun allSleep(): List<SleepRecord>

    @Upsert
    abstract suspend fun upsertSleep(s: SleepRecord)

    @Delete
    abstract suspend fun deleteSleep(s: SleepRecord)

    // ---- Workouts ----
    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY time")
    abstract fun workoutsFor(date: String): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM workouts WHERE date BETWEEN :from AND :to ORDER BY time DESC")
    abstract fun workoutsBetween(from: String, to: String): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM workouts WHERE date = :date ORDER BY time")
    abstract suspend fun workoutsOn(date: String): List<WorkoutLog>

    @Query("SELECT * FROM workouts ORDER BY time")
    abstract suspend fun allWorkouts(): List<WorkoutLog>

    @Insert
    abstract suspend fun insertWorkout(w: WorkoutLog)

    @Delete
    abstract suspend fun deleteWorkout(w: WorkoutLog)

    // ---- Symptoms ----
    @Query("SELECT * FROM symptoms WHERE date = :date")
    abstract fun symptomFor(date: String): Flow<SymptomLog?>

    @Query("SELECT * FROM symptoms WHERE date = :date")
    abstract suspend fun symptomOn(date: String): SymptomLog?

    @Query("SELECT * FROM symptoms WHERE date BETWEEN :from AND :to ORDER BY date")
    abstract fun symptomsBetween(from: String, to: String): Flow<List<SymptomLog>>

    @Query("SELECT * FROM symptoms ORDER BY date")
    abstract suspend fun allSymptoms(): List<SymptomLog>

    @Upsert
    abstract suspend fun upsertSymptom(s: SymptomLog)

    // ---- Habits ----
    @Query("SELECT * FROM habits WHERE date = :date")
    abstract fun habitsFor(date: String): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habits WHERE date = :date")
    abstract suspend fun habitsOn(date: String): List<HabitCheck>

    @Query("SELECT * FROM habits WHERE date BETWEEN :from AND :to")
    abstract fun habitsBetween(from: String, to: String): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habits ORDER BY date")
    abstract suspend fun allHabits(): List<HabitCheck>

    @Upsert
    abstract suspend fun upsertHabit(h: HabitCheck)

    // ---- Rule breaks ----
    @Query("SELECT * FROM rule_breaks WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    abstract fun breaksBetween(from: String, to: String): Flow<List<RuleBreak>>

    @Query("SELECT * FROM rule_breaks ORDER BY date DESC, createdAt DESC")
    abstract fun allBreaksFlow(): Flow<List<RuleBreak>>

    @Query("SELECT * FROM rule_breaks ORDER BY date, createdAt")
    abstract suspend fun allBreaks(): List<RuleBreak>

    @Query("DELETE FROM rule_breaks WHERE date = :date AND source = 'auto'")
    abstract suspend fun clearAuto(date: String)

    @Insert
    abstract suspend fun insertBreaks(list: List<RuleBreak>)

    @Insert
    abstract suspend fun insertBreak(b: RuleBreak)

    @Delete
    abstract suspend fun deleteBreak(b: RuleBreak)

    @Transaction
    open suspend fun replaceAuto(date: String, list: List<RuleBreak>) {
        clearAuto(date)
        if (list.isNotEmpty()) insertBreaks(list)
    }

    // ---- Custom foods ----
    @Query("SELECT * FROM custom_foods ORDER BY name")
    abstract fun customFoods(): Flow<List<CustomFood>>

    @Query("SELECT * FROM custom_foods ORDER BY name")
    abstract suspend fun allCustomFoods(): List<CustomFood>

    @Insert
    abstract suspend fun insertFood(f: CustomFood)

    @Delete
    abstract suspend fun deleteFood(f: CustomFood)
}

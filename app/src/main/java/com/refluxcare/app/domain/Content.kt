package com.refluxcare.app.domain

import java.time.DayOfWeek

data class Habit(val id: String, val title: String, val hint: String)
data class Rule(val id: String, val title: String, val why: String)
data class RoutineItem(val time: String, val title: String, val details: List<String>)
data class FoodItem(val name: String, val note: String)
data class GuideSection(val title: String, val points: List<String>)

data class Exercise(
    val id: String,
    val name: String,
    val type: String,
    val prescription: String,
    val steps: List<String>,
    val tips: List<String>,
    val sets: Int = 0,          // target sets (0 = not counted)
    val holdSeconds: Int = 0    // timer length for holds (0 = no timer)
)

object MealTypes {
    const val EARLY = "EARLY"
    const val BREAKFAST = "BREAKFAST"
    const val MID = "MID"
    const val LUNCH = "LUNCH"
    const val SNACK = "SNACK"
    const val DINNER = "DINNER"
    const val OTHER = "OTHER"

    val all = listOf(
        EARLY to "Wake-up drink",
        BREAKFAST to "Breakfast",
        MID to "Mid-morning",
        LUNCH to "Lunch",
        SNACK to "Evening snack",
        DINNER to "Dinner",
        OTHER to "Other"
    )

    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: if (id == "ANY") "Any meal" else id

    /** Guess the meal type from the clock. */
    fun guess(hour: Int): String = when (hour) {
        in 4..6 -> EARLY
        in 7..9 -> BREAKFAST
        in 10..11 -> MID
        in 12..15 -> LUNCH
        in 16..17 -> SNACK
        in 18..22 -> DINNER
        else -> OTHER
    }

    fun defaultHour(id: String): Int = when (id) {
        EARLY -> 6
        BREAKFAST -> 8
        MID -> 11
        LUNCH -> 13
        SNACK -> 16
        DINNER -> 19
        else -> 12
    }
}

object Tags {
    val all = listOf(
        "TEA" to "Tea / coffee",
        "FRIED" to "Fried",
        "SPICY" to "Very spicy",
        "PICKLE" to "Pickle",
        "ALCOHOL" to "Alcohol",
        "FIZZY" to "Fizzy drink",
        "OUTSIDE" to "Outside food",
        "BIRYANI" to "Biryani",
        "CURD" to "Curd",
        "SWEETS" to "Sweets"
    )

    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: id
}

object Portions {
    const val LIGHT = "LIGHT"
    const val NORMAL = "NORMAL"
    const val HEAVY = "HEAVY"
    val all = listOf(LIGHT to "Light", NORMAL to "Normal (80%)", HEAVY to "Heavy / overate")
    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: id
}

object WorkoutTypes {
    const val DUMBBELL = "DUMBBELL"
    const val YOGA = "YOGA"
    const val WALK = "WALK"
    fun label(id: String): String = when (id) {
        DUMBBELL -> "Dumbbell workout"
        YOGA -> "Yoga & stretching"
        WALK -> "Walk"
        else -> id
    }
}

object RuleIds {
    const val TEA_EMPTY = "TEA_EMPTY"
    const val LATE_DINNER = "LATE_DINNER"
    const val MEAL_GAP = "MEAL_GAP"
    const val LATE_SLEEP = "LATE_SLEEP"
    const val SHORT_SLEEP = "SHORT_SLEEP"
    const val LATE_WAKE = "LATE_WAKE"
    const val FRIED = "FRIED"
    const val PICKLE = "PICKLE"
    const val ALCOHOL = "ALCOHOL"
    const val FIZZY = "FIZZY"
    const val OUTSIDE = "OUTSIDE"
    const val OVEREAT = "OVEREAT"
    const val HEAVY_DINNER = "HEAVY_DINNER"
    const val EXERCISE_AFTER_MEAL = "EXERCISE_AFTER_MEAL"
    const val MISSED_WORKOUT = "MISSED_WORKOUT"
    const val MISSED_WALK = "MISSED_WALK"
    const val OTHER = "OTHER"
}

object Content {

    // ------------------------------------------------------------------ Targets
    const val BEDTIME_LIMIT_H = 22      // in bed / asleep by 10:00 pm
    const val WAKE_LIMIT_H = 6
    const val WAKE_LIMIT_M = 30         // awake by 6:30 am
    const val DINNER_LIMIT_MIN = 19 * 60 + 15 // dinner started by 7:15 pm
    const val MEAL_GAP_HOURS = 3
    const val MIN_SLEEP_HOURS = 7
    const val EXERCISE_GAP_HOURS = 2

    // ------------------------------------------------------------------ Habits (daily checklist)
    val habits = listOf(
        Habit("warm_water", "Warm water after waking (no tea)", "1–2 glasses, jeera or ginger water also fine"),
        Habit("morning_ex", "Morning exercise or stretching", "Empty stomach, 6:15–7:00 am"),
        Habit("breakfast", "Breakfast by 8:30 am", "With protein: eggs, sambar, dal dosa"),
        Habit("lunch_walk", "10-minute walk after lunch", "Even inside the house"),
        Habit("vajrasana", "Vajrasana 5–10 min after meals", "The one pose that's good right after eating"),
        Habit("posture", "Sat upright while working", "No slouching, no tight waistband"),
        Habit("light_snack", "Healthy evening snack (not fried)", "Sundal, fruit, roasted chana, makhana"),
        Habit("dinner_early", "Light dinner by 7:00 pm", "3+ hours before sleep"),
        Habit("dinner_walk", "Walk after dinner", "15–20 minutes"),
        Habit("no_trigger", "No fried food, pickle or alcohol", "Includes fizzy drinks"),
        Habit("meditate", "Meditated today (5+ min)", "Use the Meditate tab — it logs automatically"),
        Habit("breathing", "5 minutes belly breathing", "Strengthens the diaphragm"),
        Habit("sleep_setup", "Left side, head of bed raised", "Asleep by 9:30–10:00 pm")
    )

    // ------------------------------------------------------------------ Rules (auto-detected breaks)
    val rules = listOf(
        Rule(RuleIds.TEA_EMPTY, "Tea/coffee on an empty stomach", "Tea or coffee first thing in the morning irritates the stomach and relaxes the valve that keeps acid down."),
        Rule(RuleIds.LATE_DINNER, "Dinner after 7:15 pm", "With bedtime at 9:30–10, dinner must be done by about 7 pm to leave 3 hours."),
        Rule(RuleIds.MEAL_GAP, "Less than 3 hours between food and sleep", "Lying down with a full stomach is the single biggest cause of night reflux."),
        Rule(RuleIds.LATE_SLEEP, "Slept after 10:00 pm", "Your target is to be asleep by 9:30–10:00 pm."),
        Rule(RuleIds.SHORT_SLEEP, "Slept less than 7 hours", "Poor sleep increases acid sensitivity and cravings."),
        Rule(RuleIds.LATE_WAKE, "Woke up after 6:30 am", "Waking late pushes exercise and meals later and breaks the routine."),
        Rule(RuleIds.FRIED, "Fried food", "Oily food slows stomach emptying and relaxes the valve (samosa, poori, murukku, gobi 65)."),
        Rule(RuleIds.PICKLE, "Pickle", "Oily, spicy and sour — a triple trigger."),
        Rule(RuleIds.ALCOHOL, "Alcohol", "Relaxes the valve and increases acid. Beer adds gas and volume."),
        Rule(RuleIds.FIZZY, "Fizzy / carbonated drink", "Gas pushes acid upward."),
        Rule(RuleIds.OUTSIDE, "Outside food", "Usually more oil, spice and bigger portions."),
        Rule(RuleIds.OVEREAT, "Overate (heavy portion)", "A stretched stomach pushes acid up. Stop at 80% full."),
        Rule(RuleIds.HEAVY_DINNER, "Spicy / heavy dinner", "Biryani, spicy curry, curd or pickle at night make night reflux worse."),
        Rule(RuleIds.EXERCISE_AFTER_MEAL, "Exercised within 2 hours of eating", "Bending and lifting on a full stomach pushes acid up."),
        Rule(RuleIds.MISSED_WORKOUT, "Skipped planned workout", "Mon/Wed/Fri dumbbells, Tue/Thu/Sat yoga."),
        Rule(RuleIds.MISSED_WALK, "Skipped evening walk", "A walk after dinner helps digestion."),
        Rule(RuleIds.OTHER, "Other", "Anything else you want to note.")
    )

    fun ruleTitle(id: String): String = rules.firstOrNull { it.id == id }?.title ?: id

    // ------------------------------------------------------------------ Weekly plan
    fun plannedWorkout(day: DayOfWeek): String? = when (day) {
        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY -> WorkoutTypes.DUMBBELL
        DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY -> WorkoutTypes.YOGA
        DayOfWeek.SUNDAY -> null
    }

    val weeklyPlan = listOf(
        "Monday" to "Dumbbells (25–30 min) + 5 min stretching",
        "Tuesday" to "Yoga stretches (15–20 min)",
        "Wednesday" to "Dumbbells (25–30 min) + 5 min stretching",
        "Thursday" to "Yoga stretches (15–20 min)",
        "Friday" to "Dumbbells (25–30 min) + 5 min stretching",
        "Saturday" to "Yoga stretches (15–20 min)",
        "Sunday" to "Rest day — just your walk",
        "Every evening" to "15–20 min walk after dinner (aim 6k+ steps)"
    )

    // ------------------------------------------------------------------ Daily routine
    val routine = listOf(
        RoutineItem("5:45 – 6:00 am", "Wake up", listOf(
            "Drink 1–2 glasses of warm water (or jeera / ginger water).",
            "No tea or coffee on an empty stomach.",
            "Open the app — your sleep is detected automatically from screen activity."
        )),
        RoutineItem("6:15 – 7:00 am", "Exercise on an empty stomach", listOf(
            "Mon/Wed/Fri: dumbbell workout. Tue/Thu/Sat: yoga stretches. Sunday: rest.",
            "If you feel weak, half a banana 15 minutes before is fine.",
            "Use the Exercise tab to follow the routine step by step."
        )),
        RoutineItem("8:00 – 8:30 am", "Breakfast", listOf(
            "Oats with milk, idli/dosa with sambar, ragi dosa, pesarattu, upma or poha.",
            "Add 2 eggs (boiled or omelette with little oil) for protein.",
            "Sit in Vajrasana 5–10 minutes after."
        )),
        RoutineItem("11:00 am", "Mid-morning (optional)", listOf(
            "A fruit (banana, papaya, apple) or a glass of buttermilk if hungry.",
            "Stand up and stretch — you've been at the desk for a while."
        )),
        RoutineItem("1:00 – 1:30 pm", "Lunch — biggest meal of the day", listOf(
            "Rice or 2–3 chapathis + dal/sambar + vegetable poriyal + buttermilk or curd.",
            "Chicken curry 2–3 times a week, less oil and chilli.",
            "Biryani: once a week, at lunch only, one plate.",
            "10-minute walk after, then sit upright at your desk."
        )),
        RoutineItem("4:30 pm", "Light evening snack", listOf(
            "Sundal, roasted chana, makhana, peanuts or fruit, or coconut water.",
            "If you want tea, have one weak cup now, with food.",
            "Fried snacks (samosa, poori, murukku) only as an occasional treat."
        )),
        RoutineItem("6:30 – 7:00 pm", "Light dinner", listOf(
            "2–3 idlis, 2 chapathis with dal, khichdi, soup with 1 chapathi, or ragi dosa.",
            "No curd, pickle, fried or very spicy food at night.",
            "Eat slowly, stop at 80% full."
        )),
        RoutineItem("7:15 – 7:45 pm", "Evening walk", listOf(
            "15–20 minutes at an easy pace, aim for 6k+ steps for the day.",
            "Log it in the app with steps."
        )),
        RoutineItem("8:30 – 9:30 pm", "Wind down", listOf(
            "Belly breathing for 5 minutes.",
            "10 minutes of meditation (Meditate tab) — calms the mind before sleep.",
            "Screens off or dimmed; no food after dinner. Water in sips is fine."
        )),
        RoutineItem("9:30 – 10:00 pm", "Sleep", listOf(
            "Head of the bed raised 15–20 cm (blocks or wedge, not just pillows).",
            "Sleep on your left side.",
            "Put the phone away — the app uses screen-off time as your bedtime."
        ))
    )

    val startPlan = listOf(
        "Week 1: move dinner to 6:30–7 pm and stop morning tea.",
        "Weeks 2–4: no alcohol, swap fried snacks for healthy ones, start exercise.",
        "After 4 weeks: check Insights to see which habits change your symptoms most."
    )

    // ------------------------------------------------------------------ Diet
    val foods: Map<String, List<FoodItem>> = linkedMapOf(
        MealTypes.EARLY to listOf(
            FoodItem("Warm water", "1–2 glasses right after waking"),
            FoodItem("Jeera water", "Soothing, aids digestion"),
            FoodItem("Ginger water", "Mild ginger, not too strong"),
            FoodItem("Soaked almonds (5–6)", "If you need something before exercise")
        ),
        MealTypes.BREAKFAST to listOf(
            FoodItem("Oats with milk", "Add banana or apple; low-fat milk is gentler"),
            FoodItem("Idli with sambar", "3 idlis, little coconut chutney"),
            FoodItem("Ragi dosa", "High fibre, filling"),
            FoodItem("Pesarattu (moong dal dosa)", "Good protein"),
            FoodItem("Vegetable upma", "Less oil"),
            FoodItem("Poha", "Light, skip lemon if it bothers you"),
            FoodItem("Boiled eggs / omelette", "2 eggs, very little oil")
        ),
        MealTypes.MID to listOf(
            FoodItem("Banana", "Low acid, easy on the stomach"),
            FoodItem("Papaya", "Aids digestion"),
            FoodItem("Apple or pear", ""),
            FoodItem("Buttermilk", "Plain, lightly salted")
        ),
        MealTypes.LUNCH to listOf(
            FoodItem("Rice + dal/sambar + poriyal", "Your main balanced meal"),
            FoodItem("Chapathi with vegetable curry", "2–3 chapathis"),
            FoodItem("Chicken curry (less oil & chilli)", "2–3 times a week"),
            FoodItem("Grilled / tandoori chicken", "Instead of fried"),
            FoodItem("Curd or buttermilk", "Fine at lunch"),
            FoodItem("Biryani (one plate)", "Once a week, lunch only")
        ),
        MealTypes.SNACK to listOf(
            FoodItem("Sundal", "Chickpea / green gram"),
            FoodItem("Roasted chana", "Crunchy replacement for murukku"),
            FoodItem("Makhana", "Dry roasted"),
            FoodItem("Peanuts or almonds", "A small handful"),
            FoodItem("Fruit (banana, papaya, watermelon)", "Avoid orange, lemon, pineapple"),
            FoodItem("Coconut water", "")
        ),
        MealTypes.DINNER to listOf(
            FoodItem("Idli (2–3)", "Easy to digest"),
            FoodItem("Chapathi with dal", "2 chapathis"),
            FoodItem("Moong dal khichdi", "With vegetables"),
            FoodItem("Vegetable or chicken soup", "With 1 chapathi"),
            FoodItem("Ragi dosa", "Little oil")
        )
    )

    val limitFoods = listOf(
        "Fried snacks: samosa, masala poori, murukku, gobi 65, bajji",
        "Pickle and very spicy curries",
        "Alcohol (beer is worse than whisky with water)",
        "Fizzy / carbonated drinks",
        "Tea or coffee on an empty stomach, or more than 2 cups a day",
        "Citrus fruits (orange, lemon, pineapple) and lots of tomato",
        "Chocolate, mint and peppermint tea",
        "Curd, biryani or heavy food at dinner"
    )

    // ------------------------------------------------------------------ Exercises
    val dumbbell = listOf(
        Exercise("warmup", "Warm-up", WorkoutTypes.DUMBBELL, "5 minutes",
            listOf(
                "Arm circles: 30 seconds forward, 30 seconds backward.",
                "March on the spot for 1 minute, lifting knees.",
                "Shoulder rolls ×10 and hip circles ×10 each way.",
                "10 slow bodyweight squats."
            ),
            listOf("Warm muscles lift better and get injured less."), sets = 1, holdSeconds = 300),
        Exercise("goblet_squat", "Goblet squat", WorkoutTypes.DUMBBELL, "3 × 10–12 reps",
            listOf(
                "Stand with feet shoulder-width apart, toes slightly out.",
                "Hold one dumbbell vertically at your chest with both hands.",
                "Push your hips back and bend your knees, keeping your chest up.",
                "Go down until thighs are near parallel (or as low as comfortable).",
                "Push through your heels to stand up."
            ),
            listOf("Knees follow the direction of your toes.", "Breathe out as you stand up."), sets = 3),
        Exercise("row", "One-arm dumbbell row", WorkoutTypes.DUMBBELL, "3 × 10–12 each side",
            listOf(
                "Put your left hand and left knee on a chair or bed; back flat.",
                "Hold the dumbbell in your right hand, arm straight down.",
                "Pull your elbow up toward your hip, squeezing your shoulder blade.",
                "Lower slowly. Finish all reps, then switch sides."
            ),
            listOf("Don't twist your body to lift the weight.", "Breathe out as you pull."), sets = 3),
        Exercise("pushup", "Push-ups (wall / knee / full)", WorkoutTypes.DUMBBELL, "3 × 8–12 reps",
            listOf(
                "Hands slightly wider than your shoulders.",
                "Keep your body in a straight line from head to knees (or feet).",
                "Lower your chest toward the floor or wall.",
                "Push back up, breathing out."
            ),
            listOf("Start on the wall, move to knees, then full push-ups as you get stronger."), sets = 3),
        Exercise("shoulder_press", "Standing shoulder press", WorkoutTypes.DUMBBELL, "3 × 10–12 reps",
            listOf(
                "Stand tall, dumbbells at shoulder height, palms facing forward.",
                "Press both dumbbells overhead until arms are straight.",
                "Lower slowly back to shoulder height."
            ),
            listOf("Don't arch your lower back — squeeze your glutes.", "Breathe out as you press."), sets = 3),
        Exercise("rdl", "Romanian deadlift", WorkoutTypes.DUMBBELL, "3 × 10–12 reps",
            listOf(
                "Stand holding dumbbells in front of your thighs, knees slightly bent.",
                "Push your hips back and slide the dumbbells down along your legs.",
                "Keep your back flat; stop just below the knees or when hamstrings feel tight.",
                "Squeeze your glutes to stand back up."
            ),
            listOf("This is a forward bend — only on an empty stomach.", "Never round your back."), sets = 3),
        Exercise("curl", "Bicep curl", WorkoutTypes.DUMBBELL, "2–3 × 10–12 reps",
            listOf(
                "Stand with elbows tucked at your sides, palms forward.",
                "Curl the dumbbells up toward your shoulders.",
                "Lower slowly over 3 seconds."
            ),
            listOf("Don't swing your body."), sets = 3),
        Exercise("triceps", "Overhead triceps extension", WorkoutTypes.DUMBBELL, "2–3 × 10–12 reps",
            listOf(
                "Hold one dumbbell with both hands above your head.",
                "Bend your elbows to lower it behind your head.",
                "Straighten your arms to lift it back up."
            ),
            listOf("Keep elbows close to your head."), sets = 3),
        Exercise("glute_bridge", "Glute bridge", WorkoutTypes.DUMBBELL, "3 × 12–15 reps",
            listOf(
                "Lie on your back, knees bent, feet flat. Optional: dumbbell on your hips.",
                "Lift your hips until your body is straight from shoulders to knees.",
                "Squeeze your glutes for 1 second, then lower."
            ),
            listOf("Lying flat — empty stomach only."), sets = 3),
        Exercise("cooldown", "Cool-down stretch", WorkoutTypes.DUMBBELL, "5 minutes",
            listOf(
                "Quad stretch, hamstring stretch, chest stretch on a doorway.",
                "Hold each 20–30 seconds, breathe slowly."
            ),
            emptyList(), sets = 1, holdSeconds = 300)
    )

    val yoga = listOf(
        Exercise("tadasana", "Tadasana side stretch", WorkoutTypes.YOGA, "20 sec each side",
            listOf(
                "Stand tall, feet together, arms overhead with fingers interlocked.",
                "Lean gently to the right, hold, then to the left."
            ),
            listOf("Lengthens the sides of the body after sitting."), sets = 2, holdSeconds = 20),
        Exercise("cat_cow", "Cat–cow", WorkoutTypes.YOGA, "8–10 slow rounds",
            listOf(
                "Go on hands and knees, wrists under shoulders, knees under hips.",
                "Breathe in: drop your belly, lift your chest and look up (cow).",
                "Breathe out: round your back, tuck your chin (cat).",
                "Move slowly with your breath."
            ),
            listOf("Great for back stiffness from desk work."), sets = 1, holdSeconds = 60),
        Exercise("cobra", "Bhujangasana (cobra)", WorkoutTypes.YOGA, "3 × 20–30 sec",
            listOf(
                "Lie on your stomach, hands under your shoulders.",
                "Press gently to lift your chest, elbows slightly bent.",
                "Keep hips on the floor, shoulders away from ears. Hold and breathe."
            ),
            listOf("Opens the chest and counters slouching.", "Empty stomach only — it presses the belly."), sets = 3, holdSeconds = 30),
        Exercise("child", "Child's pose", WorkoutTypes.YOGA, "30–60 sec",
            listOf(
                "Kneel, big toes together, knees wide apart.",
                "Sit back on your heels and stretch your arms forward on the floor.",
                "Rest your forehead down and breathe slowly."
            ),
            listOf("Knees wide so your belly isn't squeezed."), sets = 1, holdSeconds = 45),
        Exercise("butterfly", "Butterfly pose", WorkoutTypes.YOGA, "30–60 sec",
            listOf(
                "Sit with the soles of your feet together, knees out to the sides.",
                "Hold your feet, sit tall and let your knees relax down.",
                "Gently flap your knees for a few seconds, then hold."
            ),
            listOf("Opens tight hips from sitting."), sets = 1, holdSeconds = 45),
        Exercise("twist", "Supine twist", WorkoutTypes.YOGA, "30 sec each side",
            listOf(
                "Lie on your back, arms out in a T.",
                "Bend your knees and let them drop to the right; look left.",
                "Hold, then switch sides."
            ),
            listOf("Releases the lower back."), sets = 2, holdSeconds = 30),
        Exercise("neck", "Neck & shoulder rolls", WorkoutTypes.YOGA, "1 minute",
            listOf(
                "Sit tall. Slowly tilt your ear to each shoulder.",
                "Roll your shoulders backward 10 times, forward 10 times."
            ),
            listOf("Relieves screen strain."), sets = 1, holdSeconds = 60),
        Exercise("breathing", "Belly (diaphragmatic) breathing", WorkoutTypes.YOGA, "5 minutes",
            listOf(
                "Sit or lie comfortably, one hand on your chest, one on your belly.",
                "Breathe in through your nose for 4 seconds — only the belly hand rises.",
                "Breathe out slowly for 6 seconds.",
                "Continue for 5 minutes."
            ),
            listOf("Strengthens the diaphragm, which helps keep acid down. Can also do it at bedtime."), sets = 1, holdSeconds = 300),
        Exercise("vajrasana", "Vajrasana (after meals)", WorkoutTypes.YOGA, "5–10 min after meals",
            listOf(
                "Kneel and sit back on your heels, knees together.",
                "Keep your back straight, hands on your thighs.",
                "Breathe normally. Use a cushion under the ankles if needed."
            ),
            listOf("The one pose that's good right after eating — do it after lunch and dinner."), sets = 1, holdSeconds = 300)
    )

    fun exercisesFor(type: String): List<Exercise> = if (type == WorkoutTypes.DUMBBELL) dumbbell else yoga

    val exerciseRules = listOf(
        "Always exercise on an empty stomach, or at least 2 hours after a meal.",
        "Breathe out as you lift. Holding your breath raises pressure in your stomach.",
        "Avoid headstands, shoulder stands, plough pose and downward dog for now.",
        "Start with 3–5 kg dumbbells. 2 sets for the first 2 weeks, then 3 sets.",
        "Rest 60 seconds between sets. Sip water during the workout.",
        "If you have joint, back, heart problems or high blood pressure, check with a doctor first."
    )

    // ------------------------------------------------------------------ Reflux guide
    val guide = listOf(
        GuideSection("Your top 3 changes", listOf(
            "No food for 3 hours before sleep — dinner by 6:30–7 pm.",
            "No tea or coffee on an empty stomach.",
            "Head of the bed raised 15–20 cm, sleep on your left side."
        )),
        GuideSection("How to eat", listOf(
            "Smaller meals, eaten slowly. Stop at about 80% full.",
            "Make lunch the biggest meal and dinner the lightest.",
            "Biryani and outside food: lunch only, one plate, walk afterwards.",
            "Stay upright for 2–3 hours after eating. No lying on the sofa after lunch."
        )),
        GuideSection("Common triggers", limitFoods),
        GuideSection("Work-from-home habits", listOf(
            "Sit upright; don't hunch over the laptop after meals.",
            "Avoid tight belts or waistbands.",
            "Stand up and move every 45–60 minutes.",
            "Take a 10-minute walk after lunch."
        )),
        GuideSection("Sleep setup", listOf(
            "Raise the head of the bed with blocks or a wedge — extra pillows bend the stomach and don't help.",
            "Sleep on your left side; it keeps the stomach below the food pipe.",
            "Asleep by 9:30–10:00 pm, wake 5:45–6:00 am."
        )),
        GuideSection("See a doctor if", listOf(
            "Symptoms more than twice a week, or no better after 3–4 weeks of these changes.",
            "Difficulty or pain when swallowing, or food feels stuck.",
            "Unplanned weight loss, vomiting, black stools or blood in vomit.",
            "Chest pain — especially with exertion, sweating or breathlessness. Get the heart checked first."
        )),
        GuideSection("About this app", listOf(
            "This app helps you follow a routine and see patterns. It is not medical advice.",
            "If you need medicine for a while, that's fine — keep the lifestyle changes going alongside."
        ))
    )
}

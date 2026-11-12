# RefluxCare

A personal Android app to follow and track an acid-reflux lifestyle routine: daily habits, meals, sleep, exercise, rule breaks and insights. All data stays on the phone.

## What's inside

| Tab / page | What it does |
|---|---|
| **Today** | Date switcher (back-fill past days), habit checklist, symptom score, meal & drink log, workouts & walks, last night's sleep, rule breaks for the day |
| **Routine** | Full daily schedule (5:45 am → 10 pm) with details, weekly exercise plan, how to start |
| **Diet** | Reflux-friendly foods for each meal, foods to limit, **add your own healthy foods** (they appear as quick picks when logging meals) |
| **Exercise** | Today's planned session, this week's progress, rules for exercising with reflux, step-by-step instructions for every dumbbell exercise and yoga stretch |
| **Workout session** | Guided routine: tick off sets, note dumbbell weights, 60 s rest timer, hold timers for stretches (with a beep), warns you if you ate in the last 2 hours, saves to your log |
| **Sleep** | **Automatic bedtime/wake detection from your phone's screen on/off times**, 14-night chart, averages, manual edit for any night |
| **Breaks** | Every time a rule was broken — auto-detected from your logs plus ones you add yourself — with counts per rule and per day |
| **Insights** | Key insights in plain language, possible triggers (symptoms on/after rule-break days vs. other days), habit %, sleep, symptoms, exercise, food tags; **export CSV + JSON** |
| **Guide** | Why each rule matters, sleep setup, red-flag symptoms |

### Rules detected automatically
Tea/coffee on an empty stomach · dinner after 7:15 pm · less than 3 h between food and sleep · asleep after 10 pm · less than 7 h sleep · up after 6:30 am · fried food · pickle · alcohol · fizzy drinks · outside food · overeating · spicy/heavy dinner · exercise within 2 h of eating · skipped planned workout · skipped evening walk.

Detection is driven by what you log: pick the meal type, portion, and tags (Fried, Pickle, Alcohol, Outside food, Biryani, Curd, …) when you log a meal. Targets live in `domain/Content.kt` if you want to change them.

### How sleep tracking works
On first use, tap **Allow usage access** and enable RefluxCare in *Settings → Usage access*. Each time you open the app it reads screen on/off events (Android `UsageStatsManager`) for the last 10 nights, finds the longest screen-off period between 7 pm and 1 pm (brief checks under 10 minutes are merged), and records that as bedtime → wake time. Edit any night manually on the Sleep page; manual edits are never overwritten. Android only keeps these events for about a week, so open the app at least every few days.

## Build it

**Option A: Android Studio (recommended)**
1. Install Android Studio (Ladybug 2024.2 or newer).
2. *File → Open* → select the `RefluxCare` folder. Let Gradle sync (downloads dependencies the first time).
3. Plug in your phone with USB debugging on, then press **Run** ▶.
   Or *Build → Build App Bundle(s)/APK(s) → Build APK(s)* and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.

**Option B: GitHub builds the APK for you (no Android Studio)**
1. Create a new private GitHub repo and push this folder to it.
2. The included workflow (`.github/workflows/build-apk.yml`) runs automatically. Open the repo's **Actions** tab → latest run → download **RefluxCare-debug-apk**.
3. Unzip, copy the `.apk` to your phone and install it (allow "install unknown apps" for your file manager).

Requirements: minSdk 28 (Android 9+), target/compile SDK 35, JDK 17, Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Room 2.6.1 (KSP).

> Note: this project was written without being compiled. If the first build reports an error, it should be small — paste it to Claude and it can be fixed quickly.

## Project layout
```
app/src/main/java/com/refluxcare/app/
  RefluxApp.kt            Application, creates the database/repository
  data/                   Room entities, DAO, database, repository, CSV/JSON exporter
  domain/Content.kt       Routine, habits, rules, foods, exercises, guide text (edit here)
  domain/RuleEngine.kt    Auto-detects rule breaks for a day
  domain/SleepTracker.kt  Sleep detection from screen on/off events
  domain/Insights.kt      Insight and trigger calculations
  ui/                     Theme, shared components, navigation
  ui/screens/             All pages and dialogs
```

This app supports a routine; it isn't medical advice. See a doctor for frequent symptoms, trouble swallowing, weight loss, vomiting, black stools or chest pain.

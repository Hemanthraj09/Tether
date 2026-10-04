<div align="center">

<img src="logo.png" alt="Tether Logo" width="80"/>

## Tether

**No noise. Just you, your crew, and the grind.**

Tether is a native Android social accountability app built for people who work better with a little competition. Create a group with your friends, pick a goal, and show up every day. Log your hours, build your streak, and watch the leaderboard tell the truth about who's actually putting in the work.

[![Download APK](https://img.shields.io/badge/Download-APK-FF6B2B?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Hemanthraj09/Tether/releases/latest/download/Tether.apk)
[![Release](https://img.shields.io/github/v/release/Hemanthraj09/Tether?color=FF6B2B&style=for-the-badge)](https://github.com/Hemanthraj09/Tether/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://img.shields.io/github/actions/workflow/status/Hemanthraj09/Tether/ci.yml?branch=master&label=CI&style=for-the-badge)](https://github.com/Hemanthraj09/Tether/actions/workflows/ci.yml)

</div>

---

## What is Tether?

Nudge friends who've gone quiet. Run focused sessions with the built-in timer. Watch your heatmap fill up, one logged day at a time.

Tether keeps accountability tight — groups are invite-only, capped at 6 members, and the leaderboard resets every day. No passengers. Everyone shows up or the numbers say it all.

---

## Features

### 📊 Real-time Leaderboard
Daily leaderboard that resets sharply at midnight. Sorted by today's hours — not total, not weekly. Show up every day or fall behind.

### 🔥 Streaks & Heatmap
Per-group streaks tracked independently across all your groups. Full GitHub-style heatmap on your profile showing the entire calendar year at a glance.

### ⏱️ Focus Timer
Two modes — **Stopwatch** for tracking real work time (with 5/10-minute breaks), **Pomodoro** for 25/5 or 50/10 focus/break cycles. Runs as a foreground service with a live notification; time is computed from timestamps so it stays exact with the screen off, and a session survives the app being killed. Only focus time is logged.

### 👥 Group System
Create a group, share the 6-character invite code with your circle. Max 6 members per group — tight circles only. Creators can delete, members can leave anytime.

### 👊 Nudge
Tap anyone's avatar on the leaderboard or group screen to nudge them — they get a notification that opens the group. One nudge per person per day — use it wisely.

### 📈 Pace Indicator
If you're behind yesterday's pace, a chip appears on the leaderboard card. Disappears the moment you catch up.

### 🔔 Activity Feed
Today's Activity feed on the home screen collects your groups' logs and joins in real time. Unread dot on the bell icon when there's something new.

---

## Architecture

![Tether Architecture](architecture.png)

Tether follows **MVVM** with a Repository pattern across 4 layers:

- **UI Layer** — Single-Activity with Navigation Component (tab back stacks are saved and restored). Fragments collect state with `repeatOnLifecycle`; lists use `ListAdapter` + `DiffUtil`.
- **ViewModel Layer** — one ViewModel per screen exposing `StateFlow`s built from Firestore snapshot listeners (`stateIn(WhileSubscribed)`), so data survives navigation and renders from cache instantly.
- **Domain Layer** — pure, unit-tested Kotlin: `StreakCalculator`, `LeaderboardBuilder`, `TimerEngine`, `HeatmapGrid`, `InviteCodes`, `DateKeys`.
- **Repository Layer** — `LogRepository`, `LeaderboardRepository`, `GroupRepository`, `NudgeRepository`, … Multi-document writes use atomic batches/transactions.
- **Background** — `TetherTimerService` (foreground service, persisted state) and `RealtimeWatcher` (nudges + group activity while the app is alive).
- **Firebase** — Firestore (`users`, `groups`, `logs`, `groupStats/*`), Firebase Auth (email/password + Google), Crashlytics.

A detailed write-up of every feature, design decision and trade-off is in **[docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML + View Binding, Navigation Component |
| Architecture | MVVM, Single Activity |
| Async | Kotlin Coroutines + StateFlow |
| Backend | Firebase Firestore, Firebase Auth |
| Notifications | Local notifications driven by Firestore listeners |
| Local Storage | SharedPreferences, Firestore offline cache |
| Timer | Android Foreground Service + pure `TimerEngine` |
| Crash reporting | Firebase Crashlytics (release builds) |
| Testing | JUnit (domain logic), Firestore emulator + `@firebase/rules-unit-testing` (security rules) |
| CI/CD | GitHub Actions — tests, lint, rules tests; signed APK on `v*` tags |
| Release | R8 minify + resource shrinking, baseline profiles via ProfileInstaller |
| Min SDK | 26 (Android 8.0) |

---

## Engineering

**Testing** — 42 unit tests cover the logic that decides what users see: streak rules (month/year/leap boundaries), leaderboard ranking and pace, the timer state machine (Pomodoro catch-up after sleep, breaks, reboot recovery), date keys and the heatmap grid.

```bash
./gradlew testDebugUnitTest
```

**Security rules** — [`firestore.rules`](firestore.rules) only lets users write data about themselves, requires group membership for group writes, caps the 6-member limit server-side, and only allows leaderboard counters to increase by ≤ 24h per write. 38 emulator tests replay every write the app makes plus abuse cases.

```bash
cd firestore-tests && npm install && npm test   # needs Java 11+ for the emulator
firebase deploy --only firestore:rules          # deploy after the tests pass
```

**CI** — every push runs unit tests, lint, a debug build and the rules tests. Pushing a tag like `v1.1.0` builds a signed release APK and attaches it to a GitHub Release as `Tether.apk`.

---

## Installation

### Download APK directly

> **[⬇ Download Latest APK](https://github.com/Hemanthraj09/Tether/releases/latest/download/Tether.apk)**

1. Download the APK from the link above
2. On your Android device, enable **Install from unknown sources** in Settings → Security
3. Open the downloaded APK and install
4. Launch Tether and sign up

### Build from source

```bash
git clone https://github.com/Hemanthraj09/Tether.git
cd Tether
```

1. Open the project in **Android Studio**
2. Add your `google-services.json` from your Firebase project to `app/`
3. Build → **Run 'app'**

> Note: `google-services.json` is gitignored. You'll need your own Firebase project with Firestore, Auth, and FCM configured.

---

## Firestore Schema

```
users/{uid}
  → name, email, groupIds[], totalHours, uid

groups/{gid}
  → name, goalType, members[], inviteCode, createdBy, isSolo, createdAt

logs/{lid}
  → userId, groupId, userName, date, value (hours), note, createdAt

groupStats/{gid}
  → daily/{date}/{uid}: hours
  → weekly/{weekKey}/{uid}: hours
  → streaks/{uid}: currentStreak, longestStreak, lastLogDate
  → nudges/{date}_{fromUid}_{toUid}: nudgerUid, nudgerName, nudgedUid, date, timestamp
```

---

## Screens

| Screen | Description |
|---|---|
| Splash | Logo animation on every cold launch |
| Onboarding | 6-slide walkthrough on first launch |
| Auth | Email/password + Google Sign-In |
| Group List | All your groups with per-group streak |
| Group Feed | Real-time member stats, session button, invite code |
| Leaderboard | Daily rankings with pace indicators and nudge |
| Profile | Heatmap, total hours, About + FAQ |
| Timer | Stopwatch + Pomodoro with foreground service |

---

## Known Constraints

- Android only (no iOS, no web)
- Max 6 members per group by design
- Photo proof deferred (requires Firebase Blaze plan for Storage)
- Google Sign-In requires SHA-1 registration in Firebase console

---

## Built By

**Hemanth Raj** — CS (Data Science) undergrad at BMS College of Engineering, Bengaluru.

[![GitHub](https://img.shields.io/badge/GitHub-Hemanthraj09-181717?style=flat&logo=github)](https://github.com/Hemanthraj09)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-hemanth--raj-0A66C2?style=flat&logo=linkedin)](https://linkedin.com/in/hemanth-raj)

---

<div align="center">

*Builder. Explorer. Perpetually curious.*

</div>

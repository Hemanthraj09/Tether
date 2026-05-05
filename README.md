# Tether: Social Goal Tracking & Focus Management

Tether is a powerful Android application designed to harness the power of social accountability. Whether you're studying, working out, or building a new habit, Tether connects you with your crew to ensure no one falls behind.

## 🚀 Key Features

### ⏱️ Dual-Mode Focus Timer
*   **Stopwatch Mode:** For open-ended deep work sessions.
*   **Pomodoro Mode:** Structured 25-minute focus blocks with 5-minute recovery breaks.
*   **Background Persistence:** The timer runs as a foreground service, ensuring your progress is tracked even if you switch apps.

### 👥 Group Accountability
*   **Collaborative & Solo Groups:** Join forces with friends or track personal milestones.
*   **Invite-Only Access:** Secure group entry via unique invite codes.
*   **Dynamic Group Feed:** A real-time stream of your group's activity, logs, and progress.

### 📊 Progress Tracking & Analytics
*   **Activity Heatmap:** A GitHub-style visualization of your consistency over the past 52 weeks.
*   **Leaderboards:** Friendly competition with daily and weekly rankings.
*   **Streak System:** Build and maintain momentum. Missing a day resets your streak—don't let the chain break!

### 🔔 Social Interactions
*   **Nudges:** Send quick notifications to group members who haven't logged their progress lately.
*   **Live Updates:** Real-time synchronization via Firebase Firestore.

---

## 🛠️ Technical Stack

*   **Language:** 100% [Kotlin](https://kotlinlang.org/)
*   **Architecture:** MVVM (Model-View-ViewModel) for clean separation of concerns.
*   **UI Framework:** XML with View Binding and Material Design 3 (M3) components.
*   **Navigation:** Jetpack Navigation Component for type-safe fragment transitions.
*   **Backend:** 
    *   **Firebase Firestore:** Real-time NoSQL database.
    *   **Firebase Authentication:** Secure Google and Email-based login.
*   **Asynchronous Processing:** Kotlin Coroutines and LiveData.
*   **Services:** Android Foreground Services for the Focus Timer.

---

## 📂 Project Structure

```text
com.tether.app/
├── data/
│   ├── model/       # Firestore data entities (User, Group, Log, etc.)
│   └── repository/  # Data access logic and Firebase interactions
├── timer/           # Foreground service and timer-related UI
├── ui/              # Fragments, ViewModels, and Adapters
│   ├── auth/        # Login and Registration flows
│   ├── group/       # Group creation and management
│   ├── home/        # Home dashboard and group feeds
│   ├── leaderboard/ # Ranking and social stats
│   └── profile/     # User stats, Heatmap, About, and FAQ
└── utils/           # Extension functions and UI helpers
```

---

## 🏁 Getting Started

### Prerequisites
*   Android Studio Ladybug or newer.
*   JDK 11 or higher.
*   A Firebase project with Firestore and Authentication (Email/Google) enabled.

### Setup
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/Hemanthraj09/Tether.git
    ```
2.  **Add Firebase:**
    *   Download your `google-services.json` from the Firebase Console.
    *   Place it in the `app/` directory of the project.
3.  **Build and Run:**
    *   Open the project in Android Studio.
    *   Sync Gradle files.
    *   Run on a physical device or emulator (API 26+).

---

## 🤝 Built By

**Hemanth Raj**
*   [LinkedIn](https://www.linkedin.com/in/hemanthrajmv/)
*   [GitHub](https://github.com/Hemanthraj09)

Built with curiosity and a passion for productivity.

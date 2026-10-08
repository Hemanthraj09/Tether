package com.tether.app.domain

/**
 * "What do you want to stay accountable for?", asked once after sign-in.
 *
 * Each focus area matches one group goal, so hours logged in a group count
 * toward that area's weekly target (see [SaidVsDid]).
 */
object Interests {

    data class Option(val id: String, val label: String)

    val OPTIONS = listOf(
        Option("coding", "💻 Coding / DSA"),
        Option("studies", "📚 Studies & exams"),
        Option("fitness", "🏋️ Fitness"),
        Option("reading", "📖 Reading"),
        Option("work", "💼 Work & projects"),
        Option("habits", "🌱 Habits")
    )

    val STAGES = listOf(
        Option("student", "Student"),
        Option("working", "Working"),
        Option("other", "Other")
    )

    /** Group goals, in the order the create screen shows them. "Other" counts toward no area. */
    val GOALS = listOf("Study", "Gym", "Coding", "Reading", "Work", "Habits", "Other")

    private val AREA_TO_GOAL = mapOf(
        "coding" to "Coding",
        "studies" to "Study",
        "fitness" to "Gym",
        "reading" to "Reading",
        "work" to "Work",
        "habits" to "Habits"
    )
    private val GOAL_TO_AREA = AREA_TO_GOAL.entries.associate { (area, goal) -> goal.lowercase() to area }

    /** Default weekly target (hours) offered when an area is first picked. */
    const val DEFAULT_TARGET_HOURS = 5
    const val MAX_TARGET_HOURS = 60

    private val validIds = OPTIONS.map { it.id }.toSet()

    fun sanitize(selected: Collection<String>): List<String> =
        OPTIONS.map { it.id }.filter { it in selected && it in validIds }

    fun label(areaId: String): String = OPTIONS.firstOrNull { it.id == areaId }?.label ?: areaId

    /** The focus area a group's hours count toward, or null ("Other", unknown). */
    fun areaForGoal(goal: String?): String? = goal?.let { GOAL_TO_AREA[it.lowercase()] }

    fun goalForArea(areaId: String): String? = AREA_TO_GOAL[areaId]

    /** Goal pre-selected when creating a group: the user's first focus area. */
    fun defaultGoal(interests: List<String>?): String =
        interests?.firstNotNullOfOrNull { goalForArea(it) } ?: "Study"

    /** Keeps targets for chosen areas only, clamped to 1..MAX hours. */
    fun sanitizeTargets(interests: List<String>, targets: Map<String, Int>): Map<String, Int> =
        interests.associateWith { (targets[it] ?: DEFAULT_TARGET_HOURS).coerceIn(1, MAX_TARGET_HOURS) }
}

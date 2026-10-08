package com.tether.app.domain

/**
 * "Said vs. Did": what you said matters (focus areas + weekly targets)
 * against what you actually logged this week, across all your groups.
 */
object SaidVsDid {

    enum class Status { DONE, ON_TRACK, BEHIND, NOT_STARTED, NO_GROUP }

    data class AreaProgress(
        val area: String,
        val targetHours: Double,
        val doneHours: Double,
        val status: Status,
        /** Hours still needed to be on pace today (0 when on pace). */
        val behindBy: Double
    ) {
        val fraction: Double get() = if (targetHours <= 0) 0.0 else (doneHours / targetHours).coerceIn(0.0, 1.0)
    }

    /**
     * @param interests focus areas in display order
     * @param targets area → weekly target hours
     * @param groupGoals groupId → goal of each group you're in
     * @param weeklyHours groupId → your hours in that group this week
     * @param dayOfWeek 1 (first day of the week) .. 7, for the pace check
     */
    fun compute(
        interests: List<String>,
        targets: Map<String, Int>,
        groupGoals: Map<String, String>,
        weeklyHours: Map<String, Double>,
        dayOfWeek: Int
    ): List<AreaProgress> {
        val areaOfGroup = groupGoals.mapValues { Interests.areaForGoal(it.value) }
        val coveredAreas = areaOfGroup.values.filterNotNull().toSet()
        val doneByArea = weeklyHours.entries
            .mapNotNull { (gid, hours) -> areaOfGroup[gid]?.let { it to hours } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }

        return interests.map { area ->
            val target = (targets[area] ?: Interests.DEFAULT_TARGET_HOURS).toDouble()
            val done = doneByArea[area] ?: 0.0
            // Expected by the end of today, assuming an even spread over the week.
            val expected = target * dayOfWeek.coerceIn(1, 7) / 7.0
            val status = when {
                done >= target - EPSILON -> Status.DONE
                area !in coveredAreas -> Status.NO_GROUP
                done <= EPSILON -> Status.NOT_STARTED
                done >= expected * ON_PACE_SHARE -> Status.ON_TRACK
                else -> Status.BEHIND
            }
            val behindBy = if (status == Status.BEHIND || status == Status.NOT_STARTED)
                (expected - done).coerceAtLeast(0.0) else 0.0
            AreaProgress(area, target, done, status, behindBy)
        }
    }

    /** Within 80% of the even-spread pace still counts as on track. */
    private const val ON_PACE_SHARE = 0.8
    private const val EPSILON = 0.0001
}

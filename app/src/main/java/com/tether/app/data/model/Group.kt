package com.tether.app.data.model

data class Group(
    val id: String = "",
    val name: String = "",
    val goalType: String = "",
    val members: List<String> = emptyList(),
    val inviteCode: String = "",
    val createdBy: String = "",
    val isSolo: Boolean = false,
    val createdAt: Long = 0L,
    /**
     * What the leaderboard ranks: "hours" (default) or "solves" (Coding groups,
     * verified LeetCode problems solved). Only the creator can change it.
     */
    val metric: String = METRIC_HOURS,
    /** Photo proof on logs: "off", "optional" (default) or "required". Creator only. */
    val proof: String = com.tether.app.domain.Proof.MODE_OPTIONAL
) {
    companion object {
        const val METRIC_HOURS = "hours"
        const val METRIC_SOLVES = "solves"
    }
}

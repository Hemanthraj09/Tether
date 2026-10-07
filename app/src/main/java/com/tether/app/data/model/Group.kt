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
    /** Track this group is racing through ("" = none). Only offered for goals that have tracks. */
    val trackId: String = ""
)

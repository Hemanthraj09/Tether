package com.tether.app.ui.home

data class FeedItem(
    val id: Int,
    val name: String,
    val initials: String,
    val hours: Double,
    val note: String,
    val timeAgo: String,
    val avatarColorHex: String
)
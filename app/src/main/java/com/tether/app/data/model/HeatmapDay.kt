package com.tether.app.data.model

data class HeatmapDay(
    val date: String,
    val hours: Double = 0.0,
    val intensity: Int = 0
)

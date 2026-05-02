package com.tether.app.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyStatusBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) {
        v, insets ->
        val statusBarHeight = insets.getInsets(
            WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(
            v.paddingLeft,
            statusBarHeight,
            v.paddingRight,
            v.paddingBottom
        )
        insets
    }
}

fun View.applyNavigationBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) {
        v, insets ->
        val navBarHeight = insets.getInsets(
            WindowInsetsCompat.Type.navigationBars()).bottom
        v.setPadding(
            v.paddingLeft,
            v.paddingTop,
            v.paddingRight,
            navBarHeight
        )
        insets
    }
}
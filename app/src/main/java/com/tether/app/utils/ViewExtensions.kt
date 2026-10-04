package com.tether.app.utils

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController

/**
 * Navigates with an action only if the current destination owns it.
 * Prevents the "navigation destination is unknown" crash when a button is
 * double-tapped and the first tap already navigated away.
 */
fun NavController.navigateSafe(@IdRes actionId: Int, args: Bundle? = null) {
    if (currentDestination?.getAction(actionId) != null) {
        navigate(actionId, args)
    }
}

/** Shows a dialog only if one with the same tag isn't already showing (double-tap guard). */
fun DialogFragment.showOnce(fragmentManager: FragmentManager, tag: String) {
    if (fragmentManager.findFragmentByTag(tag) == null && !fragmentManager.isStateSaved) {
        show(fragmentManager, tag)
    }
}

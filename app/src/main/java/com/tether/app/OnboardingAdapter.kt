package com.tether.app

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int
)

/**
 * Creates a fresh fragment per page (FragmentStateAdapter requirement);
 * re-using pre-built fragment instances can crash when pages are restored.
 */
class OnboardingAdapter(
    activity: FragmentActivity,
    private val slides: List<OnboardingSlide>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = slides.size

    override fun createFragment(position: Int): Fragment = slides[position].let {
        OnboardingSlideFragment.newInstance(it.title, it.subtitle, it.iconRes)
    }
}

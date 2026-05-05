package com.tether.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import com.tether.app.databinding.FragmentOnboardingSlideBinding

class OnboardingSlideFragment : Fragment() {

    private var _binding: FragmentOnboardingSlideBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_ICON = "icon"

        fun newInstance(title: String, subtitle: String, iconRes: Int): OnboardingSlideFragment {
            val fragment = OnboardingSlideFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_SUBTITLE, subtitle)
                putInt(ARG_ICON, iconRes)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingSlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE)
        val subtitle = arguments?.getString(ARG_SUBTITLE)
        val iconRes = arguments?.getInt(ARG_ICON) ?: R.drawable.ic_tether_logo

        binding.tvSlideTitle.text = title
        binding.tvSlideSubtitle.text = subtitle
        binding.ivSlideIcon.setImageResource(iconRes)

        startEntranceAnimations()
    }

    private fun startEntranceAnimations() {
        // Icon animation
        val iconScaleX = ObjectAnimator.ofFloat(binding.ivSlideIcon, View.SCALE_X, 0.8f, 1.0f)
        val iconScaleY = ObjectAnimator.ofFloat(binding.ivSlideIcon, View.SCALE_Y, 0.8f, 1.0f)
        val iconAlpha = ObjectAnimator.ofFloat(binding.ivSlideIcon, View.ALPHA, 0f, 1.0f)
        
        val iconSet = AnimatorSet().apply {
            playTogether(iconScaleX, iconScaleY, iconAlpha)
            duration = 400
            interpolator = DecelerateInterpolator()
        }

        // Title animation
        val titleTranslateY = ObjectAnimator.ofFloat(binding.tvSlideTitle, View.TRANSLATION_Y, 30f, 0f)
        val titleAlpha = ObjectAnimator.ofFloat(binding.tvSlideTitle, View.ALPHA, 0f, 1.0f)
        
        val titleSet = AnimatorSet().apply {
            playTogether(titleTranslateY, titleAlpha)
            duration = 400
            startDelay = 100
            interpolator = DecelerateInterpolator()
        }

        // Subtitle animation
        val subtitleTranslateY = ObjectAnimator.ofFloat(binding.tvSlideSubtitle, View.TRANSLATION_Y, 30f, 0f)
        val subtitleAlpha = ObjectAnimator.ofFloat(binding.tvSlideSubtitle, View.ALPHA, 0f, 1.0f)
        
        val subtitleSet = AnimatorSet().apply {
            playTogether(subtitleTranslateY, subtitleAlpha)
            duration = 400
            startDelay = 200
            interpolator = DecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(iconSet, titleSet, subtitleSet)
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
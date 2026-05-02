package com.tether.app.ui.auth

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tether.app.R
import com.tether.app.databinding.FragmentAuthBinding
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    private var isLoginMode = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(
            inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setLogoSpan()
        updateUIMode(isLogin = true)
        observeAuthState()

        binding.btnToggleLogin.setOnClickListener {
            if (!isLoginMode) {
                isLoginMode = true
                updateUIMode(true)
            }
        }

        binding.btnToggleSignup.setOnClickListener {
            if (isLoginMode) {
                isLoginMode = false
                updateUIMode(false)
            }
        }

        binding.btnAuthPrimary.setOnClickListener {
            handleAuthAction()
        }
    }

    private fun handleAuthAction() {
        val email = binding.etEmail.text
            .toString().trim()
        val password = binding.etPassword.text
            .toString().trim()

        var isValid = true

        if (email.isEmpty()) {
            binding.etEmail.error = "Email required"
            isValid = false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password required"
            isValid = false
        }
        if (password.isNotEmpty() && password.length < 6) {
            binding.etPassword.error = "Min 6 characters"
            isValid = false
        }

        if (!isLoginMode) {
            val name = binding.etName.text
                .toString().trim()
            if (name.isEmpty()) {
                binding.etName.error = "Name required"
                isValid = false
            }
            if (isValid) {
                viewModel.signup(name, email, password)
            }
        } else {
            if (isValid) {
                viewModel.login(email, password)
            }
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> {
                        binding.btnAuthPrimary.isEnabled =
                            false
                        binding.btnAuthPrimary.text =
                            "Please wait..."
                    }
                    is AuthState.Success -> {
                        binding.btnAuthPrimary.isEnabled =
                            true
                        findNavController().navigate(
                            R.id.action_auth_to_groupList)
                    }
                    is AuthState.Error -> {
                        binding.btnAuthPrimary.isEnabled =
                            true
                        updateUIMode(isLoginMode)
                        TetherToast.show(
                            requireContext(),
                            state.message,
                            isError = true)
                    }
                    is AuthState.Idle -> {
                        binding.btnAuthPrimary.isEnabled =
                            true
                    }
                }
            }
        }
    }

    private fun setLogoSpan() {
        val text = SpannableString("Tether.")
        text.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.colorAccent)
            ),
            6, 7,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvLogo.text = text
    }

    private fun updateUIMode(isLogin: Boolean) {
        if (isLogin) {
            binding.tvAuthTitle.text = "Welcome back"
            binding.tvAuthSubtitle.text =
                "Enter your details to continue."
            binding.btnAuthPrimary.text =
                getString(R.string.login)
            binding.etName.visibility = View.GONE
            binding.tvForgotPassword.visibility =
                View.VISIBLE
            binding.btnToggleLogin.background =
                ContextCompat.getDrawable(requireContext(),
                    R.drawable.bg_toggle_active)
            binding.btnToggleLogin.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextPrimary))
            binding.btnToggleSignup.background = null
            binding.btnToggleSignup.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextSecondary))
        } else {
            binding.tvAuthTitle.text = "Create account"
            binding.tvAuthSubtitle.text =
                "Join your friends and start tracking."
            binding.btnAuthPrimary.text =
                getString(R.string.signup)
            binding.etName.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.GONE
            binding.btnToggleSignup.background =
                ContextCompat.getDrawable(requireContext(),
                    R.drawable.bg_toggle_active)
            binding.btnToggleSignup.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextPrimary))
            binding.btnToggleLogin.background = null
            binding.btnToggleLogin.setTextColor(
                ContextCompat.getColor(requireContext(),
                    R.color.colorTextSecondary))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

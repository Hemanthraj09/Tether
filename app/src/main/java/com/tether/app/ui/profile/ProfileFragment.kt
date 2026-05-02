package com.tether.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.R
import com.tether.app.databinding.FragmentProfileBinding
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()
        setupBottomNav()

        binding.btnLogout.setOnClickListener {
            com.tether.app.data.repository.AuthRepository().logout()
            TetherToast.show(
                requireContext(),
                "Logged out successfully"
            )
            findNavController().navigate(
                R.id.authFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .setLaunchSingleTop(true)
                    .build()
            )
        }
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val email = FirebaseAuth.getInstance().currentUser?.email ?: ""

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .get()
                    .await()

                val name = userDoc.getString("name")
                    ?.takeIf { it.isNotEmpty() }
                    ?: email.substringBefore("@")

                val initials = name
                    .split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2)
                    .joinToString("")
                    .uppercase()
                    .takeIf { it.isNotEmpty() } ?: "U"

                val currentStreak = userDoc.getLong("currentStreak")?.toInt() ?: 0
                val totalHours = userDoc.getDouble("totalHours") ?: 0.0

                binding.tvProfileName.text = name
                binding.tvProfileEmail.text = email
                binding.tvProfileInitials.text = initials
                binding.tvStreakCount.text = currentStreak.toString()
                binding.tvTotalHours.text = String.format("%.1f", totalHours) + "h"
                binding.tvGroupCount.text = (userDoc.get("groupIds") as? List<*>)?.size?.toString() ?: "0"

                loadHeatmapData(uid)

            } catch (e: Exception) {
                binding.tvProfileName.text = email.substringBefore("@")
                binding.tvProfileEmail.text = email
                binding.tvProfileInitials.text = "U"
            }
        }
    }

    private fun loadHeatmapData(uid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val logs = FirebaseFirestore.getInstance()
                    .collection("logs")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()

                val heatmapData = mutableMapOf<String, Double>()

                logs.documents.forEach { doc ->
                    val date = doc.getString("date") ?: return@forEach
                    val hours = doc.getDouble("value") ?: 0.0
                    heatmapData[date] = (heatmapData[date] ?: 0.0) + hours
                }

                renderHeatmap(heatmapData)

            } catch (e: Exception) {
                // Heatmap load failed, show empty
            }
        }
    }

    private fun renderHeatmap(data: Map<String, Double>) {
        val container = binding.heatmapPlaceholder
        container.removeAllViews()

        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd", java.util.Locale.getDefault())

        // Build 364 days (52 weeks x 7 days)
        // starting from 363 days ago
        val days = mutableListOf<Pair<String, Double>>()
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, -363)

        // Align to Monday of that week
        while (cal.get(java.util.Calendar.DAY_OF_WEEK)
            != java.util.Calendar.MONDAY) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }

        for (i in 0..363) {
            val dateStr = sdf.format(cal.time)
            val hours = data[dateStr] ?: 0.0
            days.add(Pair(dateStr, hours))
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        // Outer horizontal ScrollView for wide grid
        val scrollView = android.widget.HorizontalScrollView(
            requireContext())
        scrollView.layoutParams =
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams
                    .MATCH_PARENT,
                android.view.ViewGroup.LayoutParams
                    .WRAP_CONTENT)
        scrollView.isHorizontalScrollBarEnabled = false

        // TableLayout: 7 rows (Mon-Sun), 52 columns (weeks)
        val tableLayout = android.widget.TableLayout(
            requireContext())
        tableLayout.layoutParams =
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams
                    .WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams
                    .WRAP_CONTENT)

        val colors = intArrayOf(
            0xFF2A2A2A.toInt(),  // 0 - no activity
            0xFF0E4429.toInt(),  // 1h
            0xFF0F542E.toInt(),  // 2h
            0xFF136535.toInt(),  // 3h
            0xFF1A7A3C.toInt(),  // 4h
            0xFF1E8F42.toInt(),  // 5h
            0xFF22A348.toInt(),  // 6h
            0xFF26B84E.toInt(),  // 7h
            0xFF2DC653.toInt(),  // 8h
            0xFF39D35A.toInt(),  // 9h
            0xFF45E061.toInt(),  // 10h
            0xFF56E870.toInt(),  // 11h
            0xFF6BF080.toInt()   // 12h
        )

        val cellSizePx = (14 *
                resources.displayMetrics.density).toInt()
        val marginPx = (2 *
                resources.displayMetrics.density).toInt()

        for (row in 0..6) {
            val tableRow = android.widget.TableRow(
                requireContext())
            tableRow.layoutParams =
                android.widget.TableLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams
                        .WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams
                        .WRAP_CONTENT)

            for (col in 0..51) {
                val index = col * 7 + row
                val hours = if (index < days.size)
                    days[index].second else 0.0

                val intensity = when {
                    hours <= 0.0 -> 0
                    hours >= 12.0 -> 12
                    else -> hours.toInt().coerceIn(1, 12)
                }

                val cell = android.view.View(requireContext())
                val params = android.widget.TableRow
                    .LayoutParams(cellSizePx, cellSizePx)
                params.setMargins(
                    marginPx, marginPx, marginPx, marginPx)
                cell.layoutParams = params

                val drawable = android.graphics.drawable
                    .GradientDrawable()
                drawable.setColor(colors[intensity])
                drawable.cornerRadius = 3 *
                        resources.displayMetrics.density
                cell.background = drawable

                tableRow.addView(cell)
            }
            tableLayout.addView(tableRow)
        }

        scrollView.addView(tableLayout)
        container.addView(scrollView)
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_profile

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    findNavController().navigate(
                        R.id.groupListFragment,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.groupListFragment, true)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                    true
                }
                R.id.nav_leaderboard -> {
                    findNavController().navigate(
                        R.id.leaderboardFragment,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.groupListFragment, false)
                            .setLaunchSingleTop(true)
                            .build()
                    )
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

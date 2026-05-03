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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
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

                // Calculate today's hours instead of all-time
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayLogs = FirebaseFirestore.getInstance()
                    .collection("logs")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("date", today)
                    .get()
                    .await()
                val todayHours = todayLogs.documents
                    .sumOf { it.getDouble("value") ?: 0.0 }

                // Find highest streak across all user's groups
                val groupIds = (userDoc.get("groupIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                var highestStreak = 0
                groupIds.forEach { gid ->
                    try {
                        val streakDoc = FirebaseFirestore.getInstance()
                            .collection("groupStats")
                            .document(gid)
                            .collection("streaks")
                            .document(uid)
                            .get().await()
                        val streak = streakDoc.getLong("currentStreak")?.toInt() ?: 0
                        if (streak > highestStreak) highestStreak = streak
                    } catch (e: Exception) {
                        // skip this group if fetch fails, continue with others
                    }
                }

                binding.tvProfileName.text = name
                binding.tvProfileEmail.text = email
                binding.tvProfileInitials.text = initials
                binding.tvStreakCount.text = highestStreak.toString()
                binding.tvTotalHours.text = formatHours(todayHours)
                binding.tvGroupCount.text = groupIds.size.toString()

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

                android.util.Log.d("HeatmapDebug", "Total logs fetched: ${logs.documents.size}")

                val heatmapData = mutableMapOf<String, Double>()

                logs.documents.forEach { doc ->
                    val date = doc.getString("date") ?: return@forEach
                    val hours = doc.getDouble("value") ?: 0.0
                    heatmapData[date] = (heatmapData[date] ?: 0.0) + hours
                }

                android.util.Log.d("HeatmapDebug", "Heatmap data: $heatmapData")

                renderHeatmap(heatmapData)
                android.util.Log.d("HeatmapDebug", "Render called with ${heatmapData.size} entries")

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

        val days = mutableListOf<Pair<String, Double>>()

        // Build full current year from Jan 1 to Dec 31
        val yearCal = java.util.Calendar.getInstance()
        val currentYear = yearCal.get(java.util.Calendar.YEAR)
        
        // Start from Jan 1 of current year
        val startCal = java.util.Calendar.getInstance()
        startCal.set(currentYear, java.util.Calendar.JANUARY, 1)
        
        // Align back to Monday before Jan 1
        while (startCal.get(java.util.Calendar.DAY_OF_WEEK)
            != java.util.Calendar.MONDAY) {
            startCal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }

        // End on Dec 31 of current year
        val endCal = java.util.Calendar.getInstance()
        endCal.set(currentYear, java.util.Calendar.DECEMBER, 31)

        // Fill every day from aligned start to Dec 31
        while (!startCal.after(endCal)) {
            val dateStr = sdf.format(startCal.time)
            val hours = data[dateStr] ?: 0.0
            days.add(Pair(dateStr, hours))
            startCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        // Pad to complete last week column
        while (days.size % 7 != 0) {
            days.add(Pair("", 0.0))
        }

        android.util.Log.d("HeatmapDebug", "First day: ${days.firstOrNull()?.first}, Last day: ${days.lastOrNull()?.first}, Today in list: ${days.any { it.first == java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }}")

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

        // Build month label row
        val monthRow = android.widget.TableRow(requireContext())
        monthRow.layoutParams = android.widget.TableLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
        val numCols = days.size / 7
        var lastMonthAdded = -1

        for (col in 0 until numCols) {
            val index = col * 7
            val dateStr = days[index].first
            val month = if (dateStr.isNotEmpty()) {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .parse(dateStr)?.let {
                        java.util.Calendar.getInstance().apply { time = it }
                            .get(java.util.Calendar.MONTH)
                    } ?: -1
            } else -1

            val label = android.widget.TextView(requireContext())
            val params = android.widget.TableRow.LayoutParams(cellSizePx + marginPx * 2, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            label.layoutParams = params
            label.textSize = 9f
            label.setTextColor(0xFF888888.toInt())
            label.maxLines = 1

            if (month != -1 && month != lastMonthAdded) {
                // Only show label if this column's date is in the current year
                val yearOfCol = if (dateStr.isNotEmpty()) {
                    dateStr.substring(0, 4).toIntOrNull() ?: -1
                } else -1
                if (yearOfCol == currentYear) {
                    label.text = monthNames[month]
                    lastMonthAdded = month
                } else {
                    label.text = ""
                }
            } else {
                label.text = ""
            }
            monthRow.addView(label)
        }

        tableLayout.addView(monthRow, 0) // Insert as first row

        for (row in 0..6) {
            val tableRow = android.widget.TableRow(
                requireContext())
            tableRow.layoutParams =
                android.widget.TableLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams
                        .WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams
                        .WRAP_CONTENT)

            for (col in 0 until (days.size / 7)) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

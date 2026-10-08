package com.tether.app.ui.log

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tether.app.R
import com.tether.app.databinding.LayoutLogBottomSheetBinding
import com.tether.app.domain.Proof
import com.tether.app.ui.common.SheetOption
import com.tether.app.ui.common.TetherSheet
import com.tether.app.ui.home.GroupFeedViewModel
import com.tether.app.utils.ProofImage
import com.tether.app.utils.TetherToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class LogBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: LayoutLogBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var currentHours = 0
    private var currentMinutes = 0
    private var groupId: String = ""
    // Shared with the hosting GroupFeedFragment (shown in its childFragmentManager).
    private val viewModel: GroupFeedViewModel by viewModels(ownerProducer = { requireParentFragment() })

    /** The compressed proof photo (≤ ~120 KB), or null. */
    private var photo: ByteArray? = null

    private val proofMode: String
        get() = Proof.normalizeMode(viewModel.group.value?.proof)

    // The camera app writes into our cache through the FileProvider. If Android
    // kills this process while the camera is open, the result still arrives here
    // after the sheet is recreated (the file path is fixed).
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) usePhoto(Uri.fromFile(captureFile()))
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutLogBottomSheetBinding.inflate(inflater, container, false)
        groupId = arguments?.getString("groupId") ?: ""
        savedInstanceState?.let {
            currentHours = it.getInt(STATE_HOURS)
            currentMinutes = it.getInt(STATE_MINUTES)
        }
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(
        requireContext(),
        R.style.BottomSheetDialogTheme
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        updateDisplay()
        // The group (and its proof mode) may still be loading, e.g. after process death.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.group.collect { if (_binding != null) setUpProofSection() }
        }
        // A photo that was already compressed before the sheet was recreated.
        if (savedInstanceState?.getBoolean(STATE_HAS_PHOTO) == true) {
            pendingFile().takeIf { it.exists() }?.readBytes()?.let { showPhoto(it) }
        }

        binding.btnHourMinus.setOnClickListener {
            if (currentHours > 0) {
                currentHours--
                updateDisplay()
            }
        }

        binding.btnHourPlus.setOnClickListener {
            if (currentHours < 12) {
                currentHours++
                updateDisplay()
            }
        }

        binding.btnMinuteMinus.setOnClickListener {
            if (currentMinutes > 0) {
                currentMinutes -= 5
                updateDisplay()
            } else if (currentHours > 0) {
                currentHours--
                currentMinutes = 55
                updateDisplay()
            }
        }

        binding.btnMinutePlus.setOnClickListener {
            if (currentMinutes < 55) {
                currentMinutes += 5
                updateDisplay()
            } else {
                currentMinutes = 0
                if (currentHours < 12) currentHours++
                updateDisplay()
            }
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnLogIt.setOnClickListener {
            if (currentHours == 0 && currentMinutes == 0) {
                TetherToast.show(requireContext(), "Please log at least 5 minutes", isError = true)
                return@setOnClickListener
            }
            val note = binding.etNote.text.toString().trim()
            if (!Proof.isNoteValid(note)) {
                binding.etNote.error = "Tell your group what you did"
                binding.etNote.requestFocus()
                return@setOnClickListener
            }
            if (photo == null && Proof.photoRequired(proofMode, Proof.SOURCE_MANUAL)) {
                TetherToast.show(requireContext(), "This group needs a photo. Tap the box to take one.", isError = true)
                return@setOnClickListener
            }
            binding.btnLogIt.isEnabled = false
            val totalHours = currentHours + (currentMinutes / 60.0)
            val timeStr = when {
                currentHours == 0 -> "${currentMinutes}m"
                currentMinutes == 0 -> "${currentHours}h"
                else -> "${currentHours}h ${currentMinutes}m"
            }
            viewModel.writeLog(groupId, totalHours, note, Proof.SOURCE_MANUAL,
                photo.takeIf { Proof.photoAllowed(proofMode) })
            TetherToast.show(requireContext(), "Logged $timeStr! Keep it up 🔥")
            clearTempFiles()
            dismiss()
        }
    }

    // ── Photo proof ──────────────────────────────────────────────

    private fun setUpProofSection() {
        val mode = proofMode
        binding.sectionProof.visibility = if (Proof.photoAllowed(mode)) View.VISIBLE else View.GONE
        val required = mode == Proof.MODE_REQUIRED
        binding.tvProofLabel.text = if (required) "Photo proof (required by this group)" else "Photo proof (optional)"
        binding.tvProofHint.text = if (required) "Take a photo of what you did" else "Add a photo of what you did"
        binding.boxProof.setOnClickListener {
            if (photo != null) return@setOnClickListener
            // Required proof must be fresh: camera only, no old gallery photos.
            if (required) openCamera() else chooseSource()
        }
        binding.btnRemoveProof.setOnClickListener {
            photo = null
            pendingFile().delete()
            binding.ivProofPreview.setImageDrawable(null)
            binding.ivProofPreview.visibility = View.GONE
            binding.btnRemoveProof.visibility = View.GONE
            binding.proofPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun chooseSource() {
        TetherSheet.show(
            requireContext(),
            title = "Add a photo",
            subtitle = "Show your group what you did. Only they can see it, and only today.",
            options = listOf(
                SheetOption("Take a photo", "Use your camera now", icon = R.drawable.ic_camera) { openCamera() },
                SheetOption("Choose from gallery", "Pick one you already took", icon = R.drawable.ic_image) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        )
    }

    private fun openCamera() {
        val file = captureFile().apply { parentFile?.mkdirs(); delete() }
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.proofs", file)
        runCatching { takePicture.launch(uri) }.onFailure {
            TetherToast.show(requireContext(), "No camera app found", isError = true)
        }
    }

    private fun usePhoto(uri: Uri) {
        val context = context?.applicationContext ?: return
        val capture = captureFile()
        val pending = pendingFile()
        _binding?.proofProgress?.visibility = View.VISIBLE
        _binding?.proofPlaceholder?.visibility = View.GONE
        // Runs on the fragment's scope: the view may be recreated meanwhile.
        lifecycleScope.launch {
            val bytes = ProofImage.compress(context, uri)
            capture.delete()
            if (bytes != null) withContext(Dispatchers.IO) {
                pending.apply { parentFile?.mkdirs() }.writeBytes(bytes)
            }
            if (_binding == null) return@launch
            binding.proofProgress.visibility = View.GONE
            if (bytes == null) {
                binding.proofPlaceholder.visibility = View.VISIBLE
                TetherToast.show(requireContext(), "Couldn't read that photo", isError = true)
            } else {
                showPhoto(bytes)
            }
        }
    }

    private fun showPhoto(bytes: ByteArray) {
        photo = bytes
        binding.ivProofPreview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        binding.ivProofPreview.visibility = View.VISIBLE
        binding.btnRemoveProof.visibility = View.VISIBLE
        binding.proofPlaceholder.visibility = View.GONE
    }

    // Resolved once while attached, so background work never needs the fragment's context.
    private val proofDir by lazy { File(requireContext().applicationContext.cacheDir, "proofs") }
    private fun captureFile() = File(proofDir, "capture.jpg")
    private fun pendingFile() = File(proofDir, "pending.jpg")

    private fun clearTempFiles() {
        captureFile().delete()
        pendingFile().delete()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOURS, currentHours)
        outState.putInt(STATE_MINUTES, currentMinutes)
        outState.putBoolean(STATE_HAS_PHOTO, photo != null)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val screenHeight = resources.displayMetrics.heightPixels
            behavior.peekHeight = (screenHeight * 0.85).toInt()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            it.layoutParams.height = (screenHeight * 0.85).toInt()
        }
    }

    private fun updateDisplay() {
        binding.tvHoursValue.text = currentHours.toString()
        binding.tvMinutesValue.text = String.format(Locale.getDefault(), "%02d", currentMinutes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LogBottomSheet"
        private const val STATE_HOURS = "hours"
        private const val STATE_MINUTES = "minutes"
        private const val STATE_HAS_PHOTO = "hasPhoto"

        fun newInstance(groupId: String): LogBottomSheetFragment {
            val fragment = LogBottomSheetFragment()
            fragment.arguments = Bundle().apply {
                putString("groupId", groupId)
            }
            return fragment
        }
    }
}

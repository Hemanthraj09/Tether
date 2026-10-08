package com.tether.app.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.tether.app.R
import com.tether.app.databinding.DialogPhotoBinding
import com.tether.app.databinding.DialogTetherBinding

/**
 * Tether's centred dialogs, in the app's own style (dark card, rounded
 * corners, orange primary / red destructive button) instead of the
 * framework AlertDialog. Choices and menus use [TetherSheet] instead.
 */
object TetherDialogs {

    /** Title, message, Cancel + one action. [onConfirm] runs after the dialog closes. */
    fun confirm(
        context: Context,
        title: String,
        message: CharSequence? = null,
        confirmText: String,
        cancelText: String = "Cancel",
        destructive: Boolean = false,
        content: View? = null,
        onConfirm: () -> Unit
    ): Dialog {
        val (dialog, binding) = build(context, title, message, confirmText, cancelText, destructive, content)
        binding.btnDialogConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
        return dialog
    }

    /** A single text field. The dialog stays open until something is typed. */
    fun input(
        context: Context,
        title: String,
        message: CharSequence? = null,
        hint: String,
        confirmText: String,
        onConfirm: (String) -> Unit
    ): Dialog {
        val field = EditText(context).apply {
            this.hint = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = ContextCompat.getDrawable(context, R.drawable.bg_input_field)
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary))
            setHintTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary))
            textSize = 15f
        }
        val (dialog, binding) = build(context, title, message, confirmText, "Cancel", false, field)
        binding.btnDialogConfirm.setOnClickListener {
            val text = field.text.toString().trim()
            if (text.isEmpty()) {
                field.error = "Required"
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(text)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
        return dialog
    }

    /** Full-screen photo with a caption; tap anywhere or ✕ to close. */
    fun photo(context: Context, bitmap: Bitmap, caption: String): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val binding = DialogPhotoBinding.inflate(LayoutInflater.from(context))
        binding.ivPhotoFull.setImageBitmap(bitmap)
        binding.tvPhotoCaption.text = caption
        binding.tvPhotoCaption.visibility = if (caption.isBlank()) View.GONE else View.VISIBLE
        binding.root.setOnClickListener { dialog.dismiss() }
        binding.btnPhotoClose.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(binding.root)
        dialog.window?.setWindowAnimations(R.style.TetherDialogAnimation)
        dialog.show()
        return dialog
    }

    private fun build(
        context: Context,
        title: String,
        message: CharSequence?,
        confirmText: String,
        cancelText: String,
        destructive: Boolean,
        content: View?
    ): Pair<Dialog, DialogTetherBinding> {
        val dialog = Dialog(context, R.style.TetherDialog)
        val binding = DialogTetherBinding.inflate(LayoutInflater.from(context))
        binding.tvDialogTitle.text = title
        binding.tvDialogMessage.text = message
        binding.tvDialogMessage.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        if (content != null) {
            binding.dialogContent.visibility = View.VISIBLE
            binding.dialogContent.addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        binding.btnDialogCancel.text = cancelText
        binding.btnDialogCancel.setOnClickListener { dialog.dismiss() }
        binding.btnDialogConfirm.text = confirmText
        binding.btnDialogConfirm.setBackgroundResource(
            if (destructive) R.drawable.bg_button_danger else R.drawable.bg_button_primary)

        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setLayout((context.resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog to binding
    }
}

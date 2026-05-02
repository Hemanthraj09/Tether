package com.tether.app.utils

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tether.app.R

object TetherToast {

    fun show(
        context: Context,
        message: String,
        isError: Boolean = false
    ) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(
            R.layout.layout_custom_toast, null)

        val tvMessage = layout.findViewById<TextView>(
            R.id.tvToastMessage)
        val ivIcon = layout.findViewById<ImageView>(
            R.id.ivToastIcon)

        tvMessage.text = message

        if (isError) {
            ivIcon.setImageResource(R.drawable.ic_close)
            ivIcon.imageTintList =
                android.content.res.ColorStateList
                    .valueOf(android.graphics.Color
                        .parseColor("#EF4444"))
        } else {
            ivIcon.setImageResource(R.drawable.ic_check)
            ivIcon.imageTintList =
                android.content.res.ColorStateList
                    .valueOf(ContextCompat.getColor(
                        context, R.color.colorAccent))
        }

        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.setGravity(
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            0, 120)
        toast.show()
    }
}
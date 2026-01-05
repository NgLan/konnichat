package com.example.konnichat.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog

object DialogUtils {
    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveLabel: String = "Đồng ý",
        negativeLabel: String = "Hủy",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(negativeLabel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
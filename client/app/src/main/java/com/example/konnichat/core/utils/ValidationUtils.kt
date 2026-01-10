package com.example.konnichat.core.utils

import android.util.Patterns

object ValidationUtils {
    // Regex cho mật khẩu: Ít nhất 8 ký tự, 1 hoa, 1 thường, 1 số, 1 ký tự đặc biệt
    private val PASSWORD_PATTERN = Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$")

    fun isValidName(name: String): Boolean {
        return name.isNotBlank() && name.length <= 63
    }

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() &&
                email.length <= 255 &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8 && PASSWORD_PATTERN.matches(password)
    }
}
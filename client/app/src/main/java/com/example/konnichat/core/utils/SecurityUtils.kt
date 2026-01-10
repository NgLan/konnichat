package com.example.konnichat.core.utils

import java.security.MessageDigest

object SecurityUtils {
    /**
     * Băm chuỗi văn bản bằng thuật toán SHA-256
     * Trả về chuỗi Hexadecimal (64 ký tự)
     */
    fun hashSHA256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
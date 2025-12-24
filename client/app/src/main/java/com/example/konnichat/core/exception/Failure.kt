package com.example.konnichat.core.exception

sealed class Failure {
    // Wrapper cho lỗi từ Native (chứa message và code)
    data class NativeError(val exception: NativeException) : Failure()

    // Lỗi không xác định
    object UnknownError : Failure()
}

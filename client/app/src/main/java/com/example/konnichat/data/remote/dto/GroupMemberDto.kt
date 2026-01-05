package com.example.konnichat.data.remote.dto

data class GroupMemberDto(
    val userId: Int,
    val name: String,
    val email: String,
    val isOnline: Boolean,
    val role: String // "admin" or "member"
)

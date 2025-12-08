package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val email: String,
    val password: String,
    val name: String,
    val age: Int?,
    val status: String,
    @ColumnInfo(name = "is_online") val isOnline: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "age") val age: Int?,
    @ColumnInfo(name = "status") val status: String, // 'active', 'banned'
    @ColumnInfo(name = "is_online") val isOnline: Boolean,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "relation_type") val relationType: Int = 0,
    @ColumnInfo(name = "created_at") override val createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at") override val updatedAt: Date = Date()
) : HasCreatedAt, HasUpdatedAt

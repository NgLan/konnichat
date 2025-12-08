package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    val notification: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

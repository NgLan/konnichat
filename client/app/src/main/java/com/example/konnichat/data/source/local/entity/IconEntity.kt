package com.example.konnichat.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Icons")
data class IconEntity(
    @PrimaryKey val id: Int,
    val icon: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: String
)

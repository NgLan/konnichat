package com.example.konnichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "icons")
data class IconEntity(
    @PrimaryKey
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "code") val code: String, // 'like', 'haha', 'love',...
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "created_at") override val createdAt: Date = Date()
) : HasCreatedAt

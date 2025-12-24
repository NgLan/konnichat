package com.example.konnichat.data.local.entity

import java.util.Date

// Interface cho bảng nào có created_at
interface HasCreatedAt {
    val createdAt: Date
}

// Interface cho bảng nào có updated_at
interface HasUpdatedAt {
    val updatedAt: Date
}

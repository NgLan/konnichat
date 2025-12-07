package com.example.konnichat.data.mapper

import com.example.konnichat.data.source.local.entity.IconEntity
import com.example.konnichat.domain.model.Icon

class IconMapper {
    fun mapToDomain(entity: IconEntity): Icon {
        return Icon(
            id = entity.id,
            iconCode = entity.icon,
            imageUrl = entity.imageUrl
        )
    }
}

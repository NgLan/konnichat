// File: client/app/src/main/java/com/example/konnichat/data/local/dao/IconDao.kt
package com.example.konnichat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.konnichat.data.local.entity.IconEntity

@Dao
interface IconDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIcons(icons: List<IconEntity>)

    @Query("SELECT * FROM icons")
    suspend fun getAllIcons(): List<IconEntity>
}
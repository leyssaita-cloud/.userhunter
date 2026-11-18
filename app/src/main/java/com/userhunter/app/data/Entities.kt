package com.userhunter.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checked_cache",
    indices = [Index("checkedAt"), Index("state")]
)
data class CheckedCacheEntity(
    @PrimaryKey val username: String,
    val state: String,
    val checkedAt: Long,
    val httpStatus: Int?,
    val detail: String
)

@Entity(
    tableName = "hunt_results",
    indices = [Index("rarity"), Index("length"), Index("discoveredAt")]
)
data class HuntResultEntity(
    @PrimaryKey val username: String,
    val rarity: Int,
    val tagsCsv: String,
    val length: Int,
    val discoveredAt: Long,
    val lastCheckedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val username: String,
    val createdAt: Long
)

@Entity(tableName = "hunt_session")
data class HuntSessionEntity(
    @PrimaryKey val id: Int = 1,
    val requestJson: String,
    val nextIndex: String,
    val status: String,
    val checked: Long,
    val found: Long,
    val taken: Long,
    val blocked: Long,
    val errors: Long,
    val startedAt: Long,
    val updatedAt: Long,
    val pauseReason: String? = null,
    val requestsPerSecond: Double = 0.0
)

data class ResultWithFavorite(
    @androidx.room.Embedded val result: HuntResultEntity,
    val favoriteFlag: Int
) {
    val isFavorite: Boolean get() = favoriteFlag != 0
}

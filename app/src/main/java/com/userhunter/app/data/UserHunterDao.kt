package com.userhunter.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserHunterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertChecked(items: List<CheckedCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertResults(items: List<HuntResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFavorite(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE username = :username")
    abstract suspend fun removeFavorite(username: String)

    @Query("SELECT * FROM checked_cache WHERE username = :username AND checkedAt >= :since LIMIT 1")
    abstract suspend fun findRecentCheck(username: String, since: Long): CheckedCacheEntity?

    @Query("SELECT * FROM checked_cache WHERE username = :username LIMIT 1")
    abstract suspend fun findAnyCheck(username: String): CheckedCacheEntity?

    @Query("""
        SELECT r.*, CASE WHEN f.username IS NULL THEN 0 ELSE 1 END AS favoriteFlag
        FROM hunt_results r
        LEFT JOIN favorites f ON f.username = r.username
        ORDER BY r.rarity DESC, r.discoveredAt DESC
    """)
    abstract fun observeResults(): Flow<List<ResultWithFavorite>>

    @Query("""
        SELECT r.*, CASE WHEN f.username IS NULL THEN 0 ELSE 1 END AS favoriteFlag
        FROM hunt_results r
        LEFT JOIN favorites f ON f.username = r.username
        WHERE r.username = :username
        LIMIT 1
    """)
    abstract suspend fun findResult(username: String): ResultWithFavorite?

    @Query("SELECT COUNT(*) FROM hunt_results")
    abstract fun observeResultCount(): Flow<Int>

    @Query("DELETE FROM checked_cache")
    abstract suspend fun clearCheckedCache()

    @Query("DELETE FROM hunt_results")
    abstract suspend fun clearResults()

    @Query("DELETE FROM favorites")
    abstract suspend fun clearFavorites()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveSession(session: HuntSessionEntity)

    @Query("SELECT * FROM hunt_session WHERE id = 1 LIMIT 1")
    abstract suspend fun getSession(): HuntSessionEntity?

    @Query("SELECT * FROM hunt_session WHERE id = 1 LIMIT 1")
    abstract fun observeSession(): Flow<HuntSessionEntity?>

    @Query("DELETE FROM hunt_session WHERE id = 1")
    abstract suspend fun clearSession()

    @Transaction
    open suspend fun recordBatch(
        checked: List<CheckedCacheEntity>,
        results: List<HuntResultEntity>
    ) {
        if (checked.isNotEmpty()) upsertChecked(checked)
        if (results.isNotEmpty()) upsertResults(results)
    }

    @Transaction
    open suspend fun setFavorite(username: String, favorite: Boolean) {
        if (favorite) upsertFavorite(FavoriteEntity(username, System.currentTimeMillis()))
        else removeFavorite(username)
    }

    @Transaction
    open suspend fun clearAllLocalData() {
        clearCheckedCache()
        clearResults()
        clearFavorites()
        clearSession()
    }
}

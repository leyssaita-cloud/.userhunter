package com.userhunter.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CheckedCacheEntity::class,
        HuntResultEntity::class,
        FavoriteEntity::class,
        HuntSessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UserHunterDatabase : RoomDatabase() {
    abstract fun dao(): UserHunterDao

    companion object {
        @Volatile
        private var instance: UserHunterDatabase? = null

        fun get(context: Context): UserHunterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UserHunterDatabase::class.java,
                "user_hunter.db"
            ).build().also { instance = it }
        }
    }
}

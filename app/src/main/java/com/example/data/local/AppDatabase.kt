package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AppDao
import com.example.data.local.entities.CommentEntity
import com.example.data.local.entities.FollowEntity
import com.example.data.local.entities.LikeEntity
import com.example.data.local.entities.NotificationEntity
import com.example.data.local.entities.PrivacyRequestEntity
import com.example.data.local.entities.ReportEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.VideoEntity
import com.example.data.local.entities.ViolationEntity

@Database(
    entities = [
        UserEntity::class,
        VideoEntity::class,
        CommentEntity::class,
        LikeEntity::class,
        FollowEntity::class,
        NotificationEntity::class,
        ReportEntity::class,
        ViolationEntity::class,
        PrivacyRequestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chort_social.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package org.inventivetalent.postboxapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.inventivetalent.notificationlogger.database.converters.DateConverter
import org.inventivetalent.notificationlogger.database.converters.JsonConverter
import org.inventivetalent.postboxapp.database.daos.DataDao
import org.inventivetalent.postboxapp.database.daos.EmailDao
import org.inventivetalent.postboxapp.database.entities.Data
import org.inventivetalent.postboxapp.database.entities.Email

@Database(entities = [Data::class, Email::class], version = 2, exportSchema = false)
@TypeConverters(DateConverter::class, JsonConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dataDao(): DataDao

    abstract fun emailDao(): EmailDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // https://codelabs.developers.google.com/codelabs/android-room-with-a-view-kotlin/#6
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "postbox"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }

}

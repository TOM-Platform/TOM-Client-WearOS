package com.hci.tom.android.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(version = 1, entities = [ExerciseData::class], exportSchema = false)
abstract class ExerciseDataDatabase : RoomDatabase() {
    abstract fun exerciseDataDao(): ExerciseDataDao

    companion object {
        private var instance: ExerciseDataDatabase? = null

        fun getInstance(context: Context): ExerciseDataDatabase {
            if (instance == null)
                instance = Room.databaseBuilder(
                    context,
                    ExerciseDataDatabase::class.java,
                    "ExerciseDataDB"
                ).build()
            return instance!!
        }
    }
}
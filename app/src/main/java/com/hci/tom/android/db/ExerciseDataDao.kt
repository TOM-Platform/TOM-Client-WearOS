package com.hci.tom.android.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExerciseDataDao {
    @Upsert
    suspend fun upsertExerciseData(exerciseServiceData: ExerciseData)

    @Query("SELECT * FROM exercise_data WHERE startTime = :startTime")
    suspend fun getPrevExerciseData(startTime: Long): ExerciseData?

    @Query("SELECT * FROM exercise_data ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestExerciseData(): ExerciseData?
}
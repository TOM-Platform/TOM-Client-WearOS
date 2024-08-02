package com.hci.tom.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_data")
data class ExerciseData(
    @PrimaryKey(autoGenerate = false)
    // Refers to the system time of the watch when an exercise has started. Stored in ms.
    var startTime: Long = 0L,
    // Refers to the system time of the watch when the data is inserted into the database. Stored in ms.
    var updateTime: Long = System.currentTimeMillis(),
    // Calculated by updateTime minus startTime.
    var duration: Long = 0,
    var currLat: Double? = 0.0,
    var currLng: Double? = 0.0,
    var destLat: Double? = 0.0,
    var destLng: Double? = 0.0,
    var bearing: Int = 0,
    // Distance travelled so far in meters
    var distance: Double? = 0.0,
    var calories: Double? = 0.0,
    // Stored in bpm
    var heartRate: Double? = 0.0,
    // Refers to average heart rate of the entire exercise so far.
    var heartRateAvg: Double? = 0.0,
    var steps: Int? = 0,
    // Refers to current speed of the user. Stored in m/s.
    var speed: Double? = 0.0,
    // Refers to average speed of the entire exercise so far. Stored in m/s.
    var speedAvg: Double? = 0.0,
    // Refers to the current status of the exercise. e.g ACTIVE, STOPPED. Currently unused field.
    var currentStatus: String? = "UNKNOWN",
)

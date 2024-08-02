package com.hci.tom.android.db

import androidx.health.services.client.data.DataType
import com.hci.tom.android.service.ForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExerciseDataRepository @Inject constructor(
    private val exerciseDataDao: ExerciseDataDao,
    private val scope: CoroutineScope
) {
    private var saveDataJob: Job? = null
    private var isPaused: Boolean = false
    private var resumedTime: Long = 0L
    // totalActiveDuration is the sum of all the time intervals when the exercise is active, excluding paused time.
    private var totalActiveDuration: Long = 0L
    // exerciseStatus is used to determine whether the exercise is active, paused or stopped, still wip
    private var exerciseStatus: String = "UNKNOWN"

    suspend fun startSavingData(
        exerciseServiceState: ForegroundService.ExerciseServiceState,
        destination: DestinationData?,
        bearing: Int
    ) {
        saveDataJob?.cancel()
        exerciseStatus = "ACTIVE"
        saveDataJob = createSaveDataJob(exerciseServiceState, destination, bearing, exerciseStatus)
    }

    fun stopSavingData() {
        saveDataJob?.cancel()
        saveDataJob = null
        // reset fields for next exercise
        resumedTime = 0L
        totalActiveDuration = 0L
        isPaused = false
        exerciseStatus = "STOPPED"
    }

    fun pauseSavingData() {
        if (!isPaused) {
            isPaused = true
            exerciseStatus = "PAUSED"
            // add time elapsed since the last resume to totalActiveDuration
            totalActiveDuration += System.currentTimeMillis() - resumedTime
        }
    }

    fun resumeSavingData() {
        isPaused = false
        exerciseStatus = "ACTIVE"
        resumedTime = System.currentTimeMillis()
    }

    private suspend fun createSaveDataJob(
        exerciseServiceState: ForegroundService.ExerciseServiceState,
        destination: DestinationData?,
        bearing: Int,
        exerciseStatus: String
    ): Job {
        return scope.launch(Dispatchers.IO) {
            // retrieve previous exercise data to fallback on if current exercise data is missing certain fields
            val prevExerciseData =
                exerciseDataDao.getPrevExerciseData(exerciseServiceState.startTime!!.toEpochMilli())
            // not to be confused with ACTIVE state in ExerciseState, isActive refers to whether the coroutine is active
            while (isActive) {
                // Only save data when exercise is not paused
                if (!isPaused) {
                    // init resumedTime to startTime for when the exercise has just started
                    if (resumedTime == 0L) {
                        resumedTime = exerciseServiceState.startTime.toEpochMilli()
                    }
                    val exerciseData = parseExerciseUpdate(exerciseServiceState, prevExerciseData, destination, bearing, exerciseStatus)
                    // creates a new row if startTime does not exist in db, else updates the row with the same startTime
                    exerciseDataDao.upsertExerciseData(exerciseData)
                }
            }
        }
    }

    private fun parseExerciseUpdate(
        exerciseServiceState: ForegroundService.ExerciseServiceState,
        prevExerciseData: ExerciseData?,
        destination: DestinationData?,
        bearing: Int,
        exerciseStatus: String
    ): ExerciseData {
        val locationDataList = exerciseServiceState.exerciseMetrics?.getData(DataType.LOCATION)
        val currentLocationData = locationDataList?.lastOrNull()?.value
        // instantaneous data is retrieved slightly differently from cumulative data since its an array of data points
        val updatedHeartRate: Double? =
            if (exerciseServiceState.exerciseMetrics?.getData(DataType.HEART_RATE_BPM)!!.isNotEmpty()) {
                exerciseServiceState.exerciseMetrics.getData(DataType.HEART_RATE_BPM)
                    .last().value
            } else {
                prevExerciseData?.heartRate
            }
        val updatedSpeed: Double? =
            if (exerciseServiceState.exerciseMetrics.getData(DataType.SPEED).isNotEmpty()) {
                exerciseServiceState.exerciseMetrics.getData(DataType.SPEED)
                    .last().value
            } else {
                prevExerciseData?.speed
            }
        return ExerciseData(
            startTime = exerciseServiceState.startTime!!.toEpochMilli(),
            updateTime = System.currentTimeMillis(),
            // exerciseUpdate actually has proto.ActiveDurationMs field, but it is internal.
            // so we have to calculate it ourselves
            duration = calculateActiveDuration(),
            // gets current data if not null, else returns previous data (even if null)
            // not sure if we should retain previous location data if current location data is null
            currLat = currentLocationData?.latitude ?: prevExerciseData?.currLat,
            currLng = currentLocationData?.longitude ?: prevExerciseData?.currLng,
            destLat = destination?.latitude ?: prevExerciseData?.destLat,
            destLng = destination?.longitude ?: prevExerciseData?.destLng,
            bearing = bearing,
            distance = exerciseServiceState.exerciseMetrics.getData(DataType.DISTANCE_TOTAL)?.total
                ?: prevExerciseData?.distance,
            calories = exerciseServiceState.exerciseMetrics.getData(DataType.CALORIES_TOTAL)?.total
                ?: prevExerciseData?.calories,
            heartRate = updatedHeartRate,
            heartRateAvg = exerciseServiceState.exerciseMetrics.getData(DataType.HEART_RATE_BPM_STATS)?.average
                ?: prevExerciseData?.heartRateAvg,
            steps = exerciseServiceState.exerciseMetrics.getData(DataType.STEPS_TOTAL)?.total?.toInt()
                ?: prevExerciseData?.steps,
            speed = updatedSpeed,
            speedAvg = exerciseServiceState.exerciseMetrics.getData(DataType.SPEED_STATS)?.average ?: prevExerciseData?.speedAvg,
            currentStatus = exerciseStatus
        )
    }

    private fun calculateActiveDuration(): Long {
        return totalActiveDuration + (System.currentTimeMillis() - resumedTime)
    }
}
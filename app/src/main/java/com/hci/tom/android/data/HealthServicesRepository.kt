/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hci.tom.android.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.AXIS_X
import android.hardware.SensorManager.AXIS_Z
import android.hardware.SensorManager.getOrientation
import android.hardware.SensorManager.getRotationMatrixFromVector
import android.hardware.SensorManager.remapCoordinateSystem
import android.os.IBinder
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.services.client.data.LocationAvailability
import com.hci.tom.android.db.DestinationData
import com.hci.tom.android.db.DestinationDataStore
import com.hci.tom.android.service.ActiveDurationUpdate
import com.hci.tom.android.service.ForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore("selectedLocation")

class HealthServicesRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) {

    @Inject
    lateinit var exerciseClientManager: ExerciseClientManager

    private var exerciseService: ForegroundService? = null

    private val isChangingDestSharedPreferences: SharedPreferences =
        applicationContext.getSharedPreferences("changingDest", Context.MODE_PRIVATE)

    private val bearingSharedPreferences: SharedPreferences =
        applicationContext.getSharedPreferences("bearing", Context.MODE_PRIVATE)

    private val dataStore = applicationContext.dataStore
    private val destinationDataStore = DestinationDataStore(dataStore)

    private val sensorManager: SensorManager =
        applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var sensorListenerRegistered = false

    // referenced from https://stackoverflow.com/a/69968562/18753727
    // used to get user's bearing, 0 is North, 90 is East, 180 is South, 270 is West
    private val sensorEventListener = object : SensorEventListener {
        var lastVectorOrientation: FloatArray = FloatArray(5)
        var lastVectorHeading: Float = 0f
        var currentVectorHeading: Float = 0f

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor?.type) {
                null -> return
                Sensor.TYPE_ROTATION_VECTOR -> {
                    lastVectorOrientation = event.values
                    lastVectorHeading = currentVectorHeading

                    val tempRotationMatrix = FloatArray(9)
                    val tempOrientationMatrix = FloatArray(3)
                    getRotationMatrixFromVector(tempRotationMatrix, event.values)
                    // based on rotating about y-axis only. Not sure if we should use all 3 axes, readings get weird
                    remapCoordinateSystem(tempRotationMatrix, AXIS_X, AXIS_Z, tempRotationMatrix)
                    getOrientation(tempRotationMatrix, tempOrientationMatrix)
                    currentVectorHeading =
                        Math.toDegrees(tempOrientationMatrix[0].toDouble()).toFloat()

                    if (currentVectorHeading < 0) {
                        // heading = 360 - abs(neg heading), which is really 360 + (-heading)
                        currentVectorHeading += 360
                    }
                    // save bearing in shared preferences
                    setBearing(currentVectorHeading.toInt())
                }

                else -> return
            }
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            // currently unused
        }
    }

    fun startBearingSensor() {
        if (!sensorListenerRegistered) {
            rotationSensor?.let { sensor ->
                sensorManager.registerListener(
                    sensorEventListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                sensorListenerRegistered = true
            }
        }
    }

    fun stopBearingSensor() {
        if (sensorListenerRegistered) {
            sensorManager.unregisterListener(sensorEventListener)
            sensorListenerRegistered = false
        }
    }

    suspend fun hasExerciseCapability(): Boolean =
        getExerciseCapabilities() != null

    private suspend fun getExerciseCapabilities() =
        exerciseClientManager.getExerciseCapabilities()

    suspend fun isExerciseInProgress(): Boolean = exerciseClientManager.isExerciseInProgress()

    suspend fun isTrackingExerciseInAnotherApp() =
        exerciseClientManager.isTrackingExerciseInAnotherApp()

    fun prepareExercise() = exerciseService?.prepareExercise()
    fun startExercise() = exerciseService?.startExercise()
    fun pauseExercise() = exerciseService?.pauseExercise()
    fun endExercise() = exerciseService?.endExercise()
    fun resumeExercise() = exerciseService?.resumeExercise()

    var bound = mutableStateOf(false)

    var serviceState: MutableState<ServiceState> = mutableStateOf(ServiceState.Disconnected)

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ForegroundService.LocalBinder
            binder.getService().let {
                exerciseService = it
                serviceState.value = ServiceState.Connected(
                    exerciseServiceState = it.exerciseServiceState,
                    locationAvailabilityState = it.locationAvailabilityState,
                    activeDurationUpdate = it.exerciseServiceState.value.exerciseDurationUpdate,
                )
            }
            bound.value = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound.value = false
            exerciseService = null
            serviceState.value = ServiceState.Disconnected
        }

    }

    fun createService() {
        Intent(applicationContext, ForegroundService::class.java).also { intent ->
            applicationContext.startService(intent)
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun getIsChangingDest(): Boolean {
        return isChangingDestSharedPreferences.getBoolean("isChangingDest", false)
    }

    fun setIsChangingDest(isChangingDest: Boolean) {
        isChangingDestSharedPreferences.edit { putBoolean("isChangingDest", isChangingDest) }
    }

    fun getBearing(): Int {
        return bearingSharedPreferences.getInt("Bearing", 0)
    }

    fun setBearing(bearing: Int) {
        bearingSharedPreferences.edit { putInt("Bearing", bearing) }
    }


    fun saveDestination(destinationData: DestinationData) {
        destinationDataStore.saveDestination(destinationData)
    }

    fun clearDestination() {
        destinationDataStore.clearDestination()
    }

    fun getDestination(): Flow<DestinationData?> = destinationDataStore.selectedLocation
}

/** Store exercise values in the service state. While the service is connected,
 * the values will persist.**/
sealed class ServiceState {
    object Disconnected : ServiceState()
    data class Connected(
        val exerciseServiceState: StateFlow<ForegroundService.ExerciseServiceState>,
        val locationAvailabilityState: StateFlow<LocationAvailability>,
        val activeDurationUpdate: ActiveDurationUpdate?,
    ) : ServiceState()
}
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
package com.hci.tom.android.presentation

import android.Manifest
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.health.services.client.data.ExerciseState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.maps.errors.ApiException
import com.hci.tom.android.data.HealthServicesRepository
import com.hci.tom.android.data.ServiceState
import com.hci.tom.android.db.ExerciseDataRepository
import com.hci.tom.android.db.DestinationData
import com.hci.tom.android.maps.LocationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

data class ExerciseUiState(
    val hasExerciseCapabilities: Boolean = true,
    val isTrackingAnotherExercise: Boolean = false,
)

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseDataRepository: ExerciseDataRepository,
    private val healthServicesRepository: HealthServicesRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            healthServicesRepository.createService()
        }
    }

    companion object {
        // 0 for OpenStreetMap, 1 for Google Maps
        private const val PLACES_OPTION = 0
    }

    val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    val uiState: StateFlow<ExerciseUiState> = flow {
        emit(
            ExerciseUiState(
                hasExerciseCapabilities = healthServicesRepository.hasExerciseCapability(),
                isTrackingAnotherExercise = healthServicesRepository.isTrackingExerciseInAnotherApp(),
            )
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3_000),
        ExerciseUiState()
    )

    private var _serviceState: MutableState<ServiceState> =
        healthServicesRepository.serviceState
    val serviceState = _serviceState

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _locationsErrMsg = MutableStateFlow("")
    val locationsErrMsg = _locationsErrMsg.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val locations = searchText
        // update isSearching if user keys in something
        .onEach { text -> _isSearching.value = text.isNotBlank() }
        .flatMapLatest { text ->
            flow {
                // get location suggestions from maps api and display to user
                val suggestedLocations = fetchLocationSuggestions(text, PLACES_OPTION)
                emit(suggestedLocations)
            }
        }
        .onEach { _isSearching.update { false } }
        // stop sharing updates after 3 seconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    suspend fun isExerciseInProgress(): Boolean = healthServicesRepository.isExerciseInProgress()

    fun prepareExercise() = viewModelScope.launch { healthServicesRepository.prepareExercise() }
    fun startExercise() = viewModelScope.launch {
        startBearingSensor()
        healthServicesRepository.startExercise()
        saveExerciseDataToDB()
    }

    fun pauseExercise() = viewModelScope.launch {
        stopBearingSensor()
        healthServicesRepository.pauseExercise()
        exerciseDataRepository.pauseSavingData()
    }

    fun endExercise() = viewModelScope.launch {
        stopBearingSensor()
        healthServicesRepository.endExercise()
        exerciseDataRepository.stopSavingData()
    }

    fun resumeExercise() = viewModelScope.launch {
        startBearingSensor()
        healthServicesRepository.resumeExercise()
        exerciseDataRepository.resumeSavingData()
    }

    // Choosing destination functions
    private suspend fun fetchLocationSuggestions(
        searchText: String,
        option: Int
    ): List<DestinationData> {
        _locationsErrMsg.value = ""
        return try {
            when (option) {
                // Use Nominatim OpenStreetMap API
                0 -> LocationUtil.fetchLocationSuggestionsOSM(searchText)
                // Use Google Places API instead
                1 -> LocationUtil.fetchLocationSuggestionsGoogle(searchText)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e("LocationSuggestions", e.stackTraceToString())
            val error = when (e) {
                is ApiException -> "Google Maps API error occurred. Please try again later."
                is SocketTimeoutException -> "Connection timed out. Please check your internet connection."
                is ConnectException -> "Failed to connect to server. Please check your internet connection."
                is UnknownHostException -> "Failed to resolve hostname. Please check your internet connection."
                else -> e.message ?: e.toString()
            }
            _locationsErrMsg.value = error
            emptyList()
        }
    }

    fun onSearchTextChange(text: String) {
        _searchText.value = text
        viewModelScope.launch {
            fetchLocationSuggestions(text, PLACES_OPTION)
        }
    }

    fun saveDestination(destinationData: DestinationData) =
        healthServicesRepository.saveDestination(destinationData)

    private fun getDestination(): StateFlow<DestinationData?> {
        return healthServicesRepository.getDestination()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    fun clearDestination() = healthServicesRepository.clearDestination()

    fun getIsChangingDest(): Boolean {
        return healthServicesRepository.getIsChangingDest()
    }

    fun setIsChangingDest(isChangingDest: Boolean) {
        healthServicesRepository.setIsChangingDest(isChangingDest)
    }

    // Bearing functions
    private fun getCurrentBearing(): Int {
        Log.d("CurrentBearing", "Current bearing: ${healthServicesRepository.getBearing()}")
        return healthServicesRepository.getBearing()
    }

    private fun stopBearingSensor() {
        healthServicesRepository.stopBearingSensor()
    }

    private fun startBearingSensor() {
        healthServicesRepository.startBearingSensor()
    }

    // Database functions
    private suspend fun saveExerciseDataToDB() {
        when (val currentState = serviceState.value) {
            is ServiceState.Connected -> {
                // combine exerciseState flow and destination flow
                combine(
                    currentState.exerciseServiceState,
                    getDestination()
                ) { exerciseServiceState, destination ->
                    exerciseServiceState to destination // Pair<ExerciseServiceState, LocationData>
                }.collect { (exerciseServiceState, destination) ->
                    // only start saving data if exercise is active
                    if (exerciseServiceState.exerciseState == ExerciseState.ACTIVE) {
                        exerciseDataRepository.startSavingData(
                            exerciseServiceState,
                            destination,
                            getCurrentBearing()
                        )
                    }
                }
            }

            else -> {}
        }
    }
}


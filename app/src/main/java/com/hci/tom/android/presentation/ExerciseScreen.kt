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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.hci.tom.android.R
import com.hci.tom.android.Screens
import com.hci.tom.android.data.ServiceState
import com.hci.tom.android.presentation.component.CaloriesText
import com.hci.tom.android.presentation.component.DistanceText
import com.hci.tom.android.presentation.component.HRText
import com.hci.tom.android.presentation.component.SpeedText
import com.hci.tom.android.presentation.component.StepsText
import com.hci.tom.android.presentation.component.formatCalories
import com.hci.tom.android.presentation.component.formatDistanceKm
import com.hci.tom.android.presentation.component.formatElapsedTime
import com.hci.tom.android.service.ExerciseStateChange
import com.hci.tom.android.theme.TOMWearOSTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Shows while an exercise is in progress
 */

@Composable
fun ExerciseScreen(
    onPauseClick: () -> Unit = {},
    onEndClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onChangeDestination: () -> Unit = {},
    serviceState: ServiceState,
    navController: NavHostController,
) {
    KeepScreenOn()
    TOMWearOSTheme {
        Scaffold(timeText = {
            TimeText(
                timeSource = TimeTextDefaults.timeSource(
                    TimeTextDefaults.timeFormat()
                )
            )
        }) {
            Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExerciseMetrics(
                        onPauseClick = onPauseClick,
                        onEndClick = onEndClick,
                        onResumeClick = onResumeClick,
                        onStartClick = onStartClick,
                        onChangeDestination = onChangeDestination,
                        serviceState = serviceState,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseMetrics(
    onPauseClick: () -> Unit = {},
    onEndClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onChangeDestination: () -> Unit = {},
    serviceState: ServiceState,
    navController: NavHostController,
) {
    val chronoTickJob = remember { mutableStateOf<Job?>(null) }

    /** Only collect metrics while we are connected to the Foreground Service. **/
    when (serviceState) {
        is ServiceState.Connected -> {
            val scope = rememberCoroutineScope()
            val getExerciseServiceState by serviceState.exerciseServiceState.collectAsStateWithLifecycle()
            val exerciseMetrics by mutableStateOf(getExerciseServiceState.exerciseMetrics)
            val baseActiveDuration = remember { mutableStateOf(Duration.ZERO) }
            var activeDuration by remember { mutableStateOf(Duration.ZERO) }
            val exerciseStateChange by mutableStateOf(getExerciseServiceState.exerciseStateChange)

            /** Collect [DataPoint]s from the aggregate and exercise metric flows. Because
             * collectAsStateWithLifecycle() is asynchronous, store the last known value from each flow,
             * and update the value on screen only when the flow re-connects. **/
            val tempHeartRate = remember { mutableStateOf(0.0) }
            if (exerciseMetrics?.getData(DataType.HEART_RATE_BPM)
                    ?.isNotEmpty() == true
            ) tempHeartRate.value =
                exerciseMetrics?.getData(DataType.HEART_RATE_BPM)
                    ?.last()?.value!!
            else tempHeartRate.value = tempHeartRate.value

            val tempSpeed = remember { mutableStateOf(0.0) }
            if (exerciseMetrics?.getData(DataType.SPEED)
                    ?.isNotEmpty() == true
            ) tempSpeed.value =
                exerciseMetrics?.getData(DataType.SPEED)
                    ?.last()?.value!!
            else tempSpeed.value = tempSpeed.value

            /**Store previous calorie and distance values to avoid rendering null values from
             * [getExerciseServiceState] flow**/
            val distance =
                exerciseMetrics?.getData(DataType.DISTANCE_TOTAL)?.total
            val tempDistance = remember { mutableStateOf(0.0) }

            val calories =
                exerciseMetrics?.getData(DataType.CALORIES_TOTAL)?.total
            val tempCalories = remember { mutableStateOf(0.0) }

            val steps = exerciseMetrics?.getData(DataType.STEPS_TOTAL)?.total?.toInt()
            val tempSteps = remember { mutableStateOf(0) }

            val averageSpeed = exerciseMetrics?.getData(DataType.SPEED_STATS)?.average
            val tempAverageSpeed = remember { mutableStateOf(0.0) }

            val averageHeartRate =
                exerciseMetrics?.getData(DataType.HEART_RATE_BPM_STATS)?.average
            val tempAverageHeartRate = remember { mutableStateOf(0.0) }

            /** Update the Pause and End buttons according to [ExerciseState].**/
            val pauseOrResume = when (exerciseStateChange.exerciseState.isPaused) {
                true -> Icons.Default.PlayArrow
                false -> Icons.Default.Pause
            }
            val startOrEnd =
                when (exerciseStateChange.exerciseState.isEnded || exerciseStateChange.exerciseState.isEnding) {
                    true -> Icons.Default.PlayArrow
                    false -> Icons.Default.Stop

                }

            // The ticker coroutine updates activeDuration, but the ticker fires more often than
            // once a second, so we use derivedStateOf to update the elapsedTime state only when
            // the string representing the time on the screen changes. Recomposition then only
            // happens when elapsedTime changes, so once a second.

            val elapsedTime = derivedStateOf {
                formatElapsedTime(
                    activeDuration.toKotlinDuration(), true
                ).toString()
            }

            // Instead of watching the ExerciseState state, or active duration, I've defined a
            // ExerciseStateChange object in the service (and exposed it in the view), which is only
            // updated in the service when it transitions from one State to another.
            // This means that when the exercise state changes to ACTIVE, on that first transition
            // then again, the ticker is started, or if the state is no longer ACTIVE, the ticker is
            // stopped.
            // Also, the ExerciseStateChange object, when active, has an ActiveDurationCheckpoint
            // which allows the base ActiveDuration to be set. This is again set in the service and
            // avoids exposing ActiveDuration as state to compose, which could cause recomposition,
            // and you want the ActiveDurationCheckpoint to be associated with exactly when the
            // state changed to active.
            LaunchedEffect(exerciseStateChange) {
                if (exerciseStateChange is ExerciseStateChange.ActiveStateChange
                ) {
                    val activeStateChange =
                        exerciseStateChange as ExerciseStateChange.ActiveStateChange
                    val timeOffset =
                        (System.currentTimeMillis() -
                                activeStateChange.durationCheckPoint.time.toEpochMilli())
                    baseActiveDuration.value =
                        activeStateChange.durationCheckPoint.activeDuration.plusMillis(timeOffset)
                    chronoTickJob.value = startTick(chronoTickJob.value, scope) { tickerTime ->
                        activeDuration = baseActiveDuration.value.plusMillis(tickerTime)
                    }
                } else {
                    chronoTickJob.value?.cancel()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colors.background),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.WatchLater,
                        contentDescription = stringResource(id = R.string.duration)
                    )
                    Text(elapsedTime.value)
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(id = R.string.heart_rate)
                    )
                    HRText(
                        hr = tempHeartRate.value
                    )
                    if (averageHeartRate != null) {
                        tempAverageHeartRate.value = averageHeartRate
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = stringResource(id = R.string.calories)
                    )
                    if (calories != null) {
                        CaloriesText(
                            calories
                        )
                        tempCalories.value = calories
                    } else {
                        CaloriesText(
                            tempCalories.value
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_steps),
                        contentDescription = stringResource(id = R.string.steps)
                    )
                    if (steps != null) {
                        StepsText(
                            steps
                        )
                        tempSteps.value = steps
                    } else {
                        StepsText(
                            tempSteps.value
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = stringResource(id = R.string.distance)
                    )
                    if (distance != null) {
                        DistanceText(
                            distance
                        )
                        tempDistance.value = distance
                    } else {
                        DistanceText(
                            tempDistance.value
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(id = R.string.speed)
                    )
                    SpeedText(speed = tempSpeed.value)
                    if (averageSpeed != null) {
                        tempAverageSpeed.value = averageSpeed
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Need to capture both states here due to differences in state change in
                    // different devices. We also check if activeDuration is positive to avoid
                    // the case where the state is temporarily ENDED when the user has just started
                    // the exercise. For more information please refer here:
                    // https://github.com/android/health-samples/issues/85
                    if ((exerciseStateChange.exerciseState.isEnded || exerciseStateChange.exerciseState.isEnding) && activeDuration.toKotlinDuration()
                            .isPositive()
                    ) {

                        //In a production fitness app, you might upload workout metrics to your app
                        // either via network connection or to your mobile app via the Data Layer API.

                        // transfer values to summary screen for user to view
                        navController.navigate(
                            Screens.SummaryScreen.route + "/${tempAverageHeartRate.value.toInt()}/${
                                formatDistanceKm(
                                    tempDistance.value
                                )
                            }/${formatCalories(tempCalories.value)}/${tempSteps.value}/${
                                tempAverageSpeed.value
                            }/" + formatElapsedTime(
                                activeDuration.toKotlinDuration(), true
                            ).toString()
                        ) { popUpTo(Screens.ExerciseScreen.route) { inclusive = true } }

                        Button(onClick = { onStartClick() }) {
                            Icon(
                                imageVector = startOrEnd,
                                contentDescription = stringResource(
                                    id = R.string.startOrEnd
                                )
                            )
                        }

                    } else {
                        Button(onClick = { onEndClick() }) {
                            Icon(
                                imageVector = startOrEnd,
                                contentDescription = stringResource(
                                    id = R.string.startOrEnd
                                )
                            )
                        }

                    }
                    if (exerciseStateChange.exerciseState.isPaused) {
                        Button(onClick = {
                            onResumeClick()
                        }) {
                            Icon(
                                imageVector = pauseOrResume,
                                contentDescription = stringResource(id = R.string.pauseOrResume)
                            )
                        }
                    } else {
                        Button(onClick = {
                            onPauseClick()
                        }) {
                            Icon(
                                imageVector = pauseOrResume,
                                contentDescription = stringResource(id = R.string.pauseOrResume)
                            )
                        }

                    }
                    Button(
                        onClick = { onChangeDestination() }
                    ) {
                        Icon(imageVector=Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        }

        else -> {}
    }
}

// This is a temporary solution to the issue of ExerciseUpdateCallback not being triggered when the screen is dim.
// For more details, please refer here: https://github.com/android/health-samples/issues/77
@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}


// A coroutine is used to update a ticker whilst the exercise is active. This is necessary because
// WHS might not give us ExerciseUpdates every second on some devices. So the transition to the
// Active state is used to start the ticker, but once started, delivery of ExerciseUpdates shouldn't
// be relied on to make each tick, instead using the coroutine.
private fun startTick(
    chronoTickJob: Job?, scope: CoroutineScope, block: (tickTime: Long) -> Unit
): Job? {
    if (chronoTickJob == null || !chronoTickJob.isActive) {
        return scope.launch {
            val tickStart = System.currentTimeMillis()
            while (isActive) {
                val tickSpan = System.currentTimeMillis() - tickStart
                block(tickSpan)
                delay(CHRONO_TICK_MS)
            }
        }
    }
    return null
}

const val CHRONO_TICK_MS = 200L
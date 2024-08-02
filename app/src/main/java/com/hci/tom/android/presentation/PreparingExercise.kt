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

import android.content.ContentValues
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.services.client.data.LocationAvailability
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.dialog.Alert
import com.hci.tom.android.R
import com.hci.tom.android.data.ServiceState
import com.hci.tom.android.presentation.component.AcquiredCheck
import com.hci.tom.android.presentation.component.ExerciseInProgressAlert
import com.hci.tom.android.presentation.component.NotAcquired
import com.hci.tom.android.presentation.component.ProgressBar
import com.hci.tom.android.theme.TOMWearOSTheme
import kotlinx.coroutines.launch

/**
 * Screen that appears while the device is preparing the exercise.
 */
@Composable
fun PreparingExercise(
    onStartClick: () -> Unit = {},
    prepareExercise: () -> Unit,
    serviceState: ServiceState,
    permissions: Array<String>,
    isTrackingAnotherExercise: Boolean,
) {
    if (isTrackingAnotherExercise) {
        ExerciseInProgressAlert(true)
    }
    /** Request permissions prior to launching exercise.**/
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.all { it.value }) {
            Log.d(ContentValues.TAG, "All required permissions granted")
        }
    }

    when (serviceState) {
        is ServiceState.Connected -> {
            LaunchedEffect(Unit) {
                launch {
                    permissionLauncher.launch(permissions)
                    prepareExercise()
                }
            }

            val location by serviceState.locationAvailabilityState.collectAsStateWithLifecycle()
            var showAlert by remember { mutableStateOf(false) }

            TOMWearOSTheme {
                Scaffold(timeText =
                { TimeText(timeSource = TimeTextDefaults.timeSource(TimeTextDefaults.timeFormat())) }) {
                    if (showAlert) {
                        ShowConfirmationAlert(
                            onStartClick = {
                                showAlert = false
                                onStartClick()
                            },
                            onCancelClick = {
                                showAlert = false
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.height(25.dp)
                            ) {
                                Text(
                                    textAlign = TextAlign.Center,
                                    text = stringResource(id = R.string.preparing_exercise),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.height(40.dp)
                            ) {
                                when (location) {
                                    LocationAvailability.ACQUIRING, LocationAvailability.UNKNOWN -> ProgressBar()
                                    LocationAvailability.ACQUIRED_TETHERED, LocationAvailability.ACQUIRED_UNTETHERED -> AcquiredCheck()
                                    else -> NotAcquired()
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    textAlign = TextAlign.Center,
                                    text = updatePrepareLocationStatus(locationAvailability = location),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Button(
                                        // change to only onStartClick()
                                        onClick = {
                                            if (location != LocationAvailability.ACQUIRED_TETHERED && location != LocationAvailability.ACQUIRED_UNTETHERED) {
                                                showAlert = true
                                            } else {
                                                onStartClick()
                                            }
                                        },
                                        modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = stringResource(id = R.string.start)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun ShowConfirmationAlert(onStartClick: () -> Unit, onCancelClick: () -> Unit) {
    Alert(
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "warning",
                modifier = Modifier
                    .size(24.dp)
                    .wrapContentSize(align = Alignment.Center),
            )
        },
        title = { Text("GPS Not Acquired", textAlign = TextAlign.Center) },
        negativeButton = {
            Button(
                colors = ButtonDefaults.secondaryButtonColors(),
                onClick = {
                    onCancelClick()
                }) {
                Text("No")
            }
        },
        positiveButton = {
            Button(onClick = {
                onStartClick()
            }) { Text("Yes") }
        },
        contentPadding =
        PaddingValues(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.current_location_unavailable),
            textAlign = TextAlign.Center
        )
    }
}

/**Return [LocationAvailability] value code as a string**/
@Composable
private fun updatePrepareLocationStatus(locationAvailability: LocationAvailability): String {
    val gpsText = when (locationAvailability) {
        LocationAvailability.ACQUIRED_TETHERED, LocationAvailability.ACQUIRED_UNTETHERED -> R.string.GPS_acquired
        LocationAvailability.NO_GNSS -> R.string.GPS_disabled // TODO Consider redirecting user to change device settings in this case
        LocationAvailability.ACQUIRING -> R.string.GPS_acquiring
        LocationAvailability.UNKNOWN -> R.string.GPS_initializing
        else -> R.string.GPS_unavailable
    }

    return stringResource(id = gpsText)
}
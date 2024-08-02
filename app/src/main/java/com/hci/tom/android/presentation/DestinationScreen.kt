package com.hci.tom.android.presentation

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.hci.tom.android.R
import com.hci.tom.android.db.DestinationData
import com.hci.tom.android.presentation.component.heightRatio
import com.hci.tom.android.theme.TOMWearOSTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Screen that shows when the user chooses which destination to go to. Uses Google Maps API to get
 * location data from user.
 */
@Composable
fun Destination(onConfirmOrSkipClick: () -> Unit = {}) {
    val viewModel = hiltViewModel<ExerciseViewModel>()
    // the location inputted by the user when searching for a destination
    val searchText by viewModel.searchText.collectAsState()
    // the search results from maps API
    val locations by viewModel.locations.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val locationsErrMsg by viewModel.locationsErrMsg.collectAsState()
    var selectedIndex: Int by remember { mutableStateOf(-1) }
    val selectedLocation: MutableStateFlow<DestinationData?> = remember { MutableStateFlow(null) }

    TOMWearOSTheme {
        Scaffold(timeText = { TimeText(timeSource = TimeTextDefaults.timeSource(TimeTextDefaults.timeFormat())) }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .heightRatio(0.55f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        // custom way of inputting text on wearos, see stackoverflow link below for more info
                        TextInput()
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    // show progress indicator if locations result are not ready yet
                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    // if locations result is empty, show error message
                    else if (locations.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp)
                        ) {
                            if (searchText.trim().isBlank()) {
                                Text(
                                    text = stringResource(id = R.string.enter_location),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 14.sp
                                )
                            } else {
                                if (locationsErrMsg.isNotBlank()) {
                                    Text(
                                        text = locationsErrMsg,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 14.sp,
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.no_locations_found),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = stringResource(id = R.string.spelled_correctly),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // else, locations were found by maps api, display results to user to choose from
                    else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(2.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            itemsIndexed(locations) { index, location ->
                                val isSelected = selectedIndex == index
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .background(
                                            if (isSelected) Color.DarkGray else Color.Transparent,
                                            RoundedCornerShape(5.dp)
                                        )
                                        .clickable {
                                            selectedIndex = index
                                            selectedLocation.value = location
                                            Log.d(
                                                "DestinationScreen",
                                                "Selected location: ${location.name}, ${location.address}"
                                            )
                                        })
                                {
                                    Text(
                                        text = location.name,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = location.address,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                // buttons for user to skip or confirm choosing destination
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onConfirmOrSkipClick()
                        },
                        modifier = Modifier
                            .height(25.dp)
                            .padding(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = Color.DarkGray,
                            contentColor = MaterialTheme.colors.onPrimary
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(text = stringResource(id = R.string.skip), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (selectedLocation.value != null) {
                                viewModel.saveDestination(selectedLocation.value!!)
                            }
                            onConfirmOrSkipClick()
                        },
                        enabled = selectedIndex != -1,
                        modifier = Modifier
                            .height(25.dp)
                            .padding(horizontal = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (selectedIndex != -1) Color.DarkGray else Color.DarkGray.copy(
                                alpha = 0.8f
                            ),
                            contentColor = if (selectedIndex != -1) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSecondary,
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(text = stringResource(id = R.string.confirm), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// adapted from https://stackoverflow.com/questions/70099277/problem-with-basictextfield-in-jetpack-compose-on-wear-os/70294785#70294785
@Composable
fun TextInput() {
    val viewModel = hiltViewModel<ExerciseViewModel>()
    val searchText by viewModel.searchText.collectAsState()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.let { data ->
                val results: Bundle = RemoteInput.getResultsFromIntent(data)
                val location: CharSequence? = results.getCharSequence("location")
                // update search location in viewmodel
                if (!location?.trim().isNullOrBlank()) {
                    viewModel.onSearchTextChange(location.toString())
                } else {
                    viewModel.onSearchTextChange("")
                }
            }
        }
    Column {
        OutlinedButton(
            // note that it opens up Gboard on emulator, but samsung keyboard on galaxy watch
            onClick = {
                val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                val remoteInputs: List<RemoteInput> = listOf(
                    RemoteInput.Builder("location")
                        // this label doesn't show up on galaxy watch, but does on emulator
                        .setLabel("Please enter a location")
                        .wearableExtender {
                            setEmojisAllowed(false)
                            setInputActionType(EditorInfo.IME_ACTION_DONE)
                        }.build()
                )

                RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)

                launcher.launch(intent)
            },
            modifier = Modifier
                .height(25.dp)
                .padding(horizontal = 6.dp)
                .wrapContentWidth(Alignment.Start),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.DarkGray,
                contentColor = MaterialTheme.colors.onPrimary
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            if (searchText.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            } else {
                // replace search icon with user's search query
                Text(
                    text = searchText,
                    fontSize = 12.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
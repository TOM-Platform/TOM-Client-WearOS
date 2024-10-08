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
package com.hci.tom.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hci.tom.android.presentation.ExerciseViewModel
import com.hci.tom.android.presentation.TOMWearOSApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private lateinit var navController: NavHostController

    private val exerciseViewModel by viewModels<ExerciseViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {

            /** Check if we have an active exercise. If true, set our destination as the
             * Exercise Screen. If false, route to preparing a new exercise. **/
            val destination = when (exerciseViewModel.isExerciseInProgress()) {
                false -> Screens.StartingUp.route
                true -> Screens.ExerciseScreen.route
            }

            setContent {
                navController = rememberSwipeDismissableNavController()
                TOMWearOSApp(
                    navController,
                    startDestination = destination
                )


            }

        }
    }


}

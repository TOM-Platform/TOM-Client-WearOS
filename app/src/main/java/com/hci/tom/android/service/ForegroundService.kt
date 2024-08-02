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
package com.hci.tom.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseEndReason
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hci.tom.android.MainActivity
import com.hci.tom.android.R
import com.hci.tom.android.data.ExerciseClientManager
import com.hci.tom.android.data.ExerciseMessage
import com.hci.tom.android.network.SendExerciseDataWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ForegroundService : LifecycleService() {

    @Inject
    lateinit var exerciseClientManager: ExerciseClientManager

    private var isBound = false
    private var isStarted = false
    private val localBinder = LocalBinder()
    private var serviceRunningInForeground = false

    private val _locationAvailabilityState = MutableStateFlow(LocationAvailability.UNKNOWN)
    val locationAvailabilityState: StateFlow<LocationAvailability> = _locationAvailabilityState

    private var lastActiveDurationCheckpoint: ExerciseUpdate.ActiveDurationCheckpoint? = null

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private val sendExerciseDataRequest = OneTimeWorkRequestBuilder<SendExerciseDataWorker>()
        .setConstraints(constraints)
        .build()

    //Capturing most of the values associated with our exercise in a data class
    data class ExerciseServiceState(
        val startTime: Instant? = null,
        val exerciseState: ExerciseState = ExerciseState.ENDED,
        val exerciseMetrics: DataPointContainer? = null,
        val exerciseDurationUpdate: ActiveDurationUpdate? = null,
        val exerciseStateChange: ExerciseStateChange = ExerciseStateChange.OtherStateChange(
            ExerciseState.ENDED
        ),
        val exerciseConfig: ExerciseConfig? = null
    )

    private val _exerciseServiceState = MutableStateFlow(ExerciseServiceState())
    val exerciseServiceState: StateFlow<ExerciseServiceState> = _exerciseServiceState.asStateFlow()

    private lateinit var workManager: WorkManager

    private suspend fun isExerciseInProgress() = exerciseClientManager.isExerciseInProgress()

    /**
     * Prepare exercise in this service's coroutine context.
     */
    fun prepareExercise() {
        lifecycleScope.launch {
            exerciseClientManager.prepareExercise()
        }
    }

    /**
     * Start exercise in this service's coroutine context.
     */
    fun startExercise() {
        lifecycleScope.launch {
            exerciseClientManager.startExercise()
        }
        // start scheduled worker to send exercise data to server
        workManager.enqueueUniqueWork(
            "sendExerciseDataWorker",
            ExistingWorkPolicy.REPLACE,
            sendExerciseDataRequest
        )
        postOngoingActivityNotification()
    }

    /**
     * Pause exercise in this service's coroutine context.
     */
    fun pauseExercise() {
        lifecycleScope.launch {
            exerciseClientManager.pauseExercise()
        }
    }

    /**
     * Resume exercise in this service's coroutine context.
     */
    fun resumeExercise() {
        lifecycleScope.launch {
            exerciseClientManager.resumeExercise()
        }
    }

    /**
     * End exercise in this service's coroutine context.
     */
    fun endExercise() {
        lifecycleScope.launch {
            exerciseClientManager.endExercise()
        }
        removeOngoingActivityNotification()
        // cancel sending data to the server
        workManager.cancelUniqueWork("sendExerciseDataWorker")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(ContentValues.TAG, "onStartCommand")

        if (!isStarted) {
            isStarted = true

            if (!isBound) {
                // We may have been restarted by the system. Manage our lifetime accordingly.
                stopSelfIfNotRunning()
            }
            // Start collecting exercise information. We might stop shortly (see above), in which
            // case launchWhenStarted takes care of canceling this coroutine.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        exerciseClientManager.exerciseUpdateFlow.collect {
                            when (it) {
                                is ExerciseMessage.ExerciseUpdateMessage ->
                                    processExerciseUpdate(it.exerciseUpdate)

                                is ExerciseMessage.LocationAvailabilityMessage ->
                                    _locationAvailabilityState.value = it.locationAvailability

                            }
                        }
                    }
                }
            }
        }
        // If our process is stopped, we might have an active exercise. We want the system to
        // recreate our service so that we can present the ongoing notification in that case.
        return START_STICKY
    }

    private fun stopSelfIfNotRunning() {
        lifecycleScope.launch {
            // We may have been restarted by the system. Check for an ongoing exercise.
            if (!isExerciseInProgress()) {
                // Need to cancel [prepareExercise()] to prevent battery drain.
                if (exerciseServiceState.value.exerciseState == ExerciseState.PREPARING) {
                    lifecycleScope.launch {
                        endExercise()
                    }
                }
                // We have nothing to do, so we can stop.
                stopSelf()
            }
        }
    }

    private fun processExerciseUpdate(exerciseUpdate: ExerciseUpdate) {
        val oldState = exerciseServiceState.value.exerciseState
        if (!oldState.isEnded && exerciseUpdate.exerciseStateInfo.state.isEnded) {
            // Our exercise ended. Gracefully handle this termination be doing the following:
            // TODO Save partial workout state, show workout summary, and let the user know why the exercise was ended.

            // Dismiss any ongoing activity notification.
            removeOngoingActivityNotification()

            // Custom flow for the possible states captured by the isEnded boolean
            when (exerciseUpdate.exerciseStateInfo.endReason) {
                ExerciseEndReason.AUTO_END_SUPERSEDED -> {
                    // TODO Send the user a notification (another app ended their workout)
                    Log.i(
                        ContentValues.TAG,
                        "Your exercise was terminated because another app started tracking an exercise"
                    )
                }

                ExerciseEndReason.AUTO_END_MISSING_LISTENER -> {

                    // TODO Send the user a notification
                    Log.i(
                        ContentValues.TAG,
                        "Your exercise was auto ended because there were no registered listeners"
                    )
                }

                ExerciseEndReason.AUTO_END_PERMISSION_LOST -> {

                    // TODO Send the user a notification
                    Log.w(
                        ContentValues.TAG,
                        "Your exercise was auto ended because it lost the required permissions"
                    )
                }

                else -> {
                }
            }
        }

        // If the state of the exercise changes, then update the ExerciseStateChange object. Change
        // in this state then causes recomposition, which can be used to start or stop a coroutine
        // in the screen for updating the timer.
        if (oldState != exerciseUpdate.exerciseStateInfo.state) {
            _exerciseServiceState.update {
                it.copy(
                    exerciseStateChange = when (exerciseUpdate.exerciseStateInfo.state) {
                        // ActiveStateChange also takes an ActiveDurationCheckpoint, so that when the ticker
                        // is started in the screen, the base Duration can be set correctly.
                        ExerciseState.ACTIVE -> ExerciseStateChange.ActiveStateChange(
                            exerciseUpdate.activeDurationCheckpoint!!
                        )

                        else -> ExerciseStateChange.OtherStateChange(exerciseUpdate.exerciseStateInfo.state)
                    }
                )
            }
        }
        _exerciseServiceState.update { it ->
            it.copy(
                startTime = exerciseUpdate.startTime,
                exerciseState = exerciseUpdate.exerciseStateInfo.state,
                exerciseMetrics = exerciseUpdate.latestMetrics,
                exerciseDurationUpdate = exerciseUpdate.activeDurationCheckpoint?.let {
                    ActiveDurationUpdate(
                        it.activeDuration,
                        Instant.now()
                    )
                },
                exerciseConfig = exerciseUpdate.exerciseConfig
            )
        }
        lastActiveDurationCheckpoint = exerciseUpdate.activeDurationCheckpoint
    }


    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        handleBind()
        return localBinder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        handleBind()
    }

    override fun onCreate() {
        super.onCreate()
        workManager = WorkManager.getInstance(application)
    }

    private fun handleBind() {
        if (!isBound) {
            isBound = true
            // Start ourself. This will begin collecting exercise state if we aren't already.
            startService(Intent(this, this::class.java))
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        lifecycleScope.launch {
            // Client can unbind because it went through a configuration change, in which case it
            // will be recreated and bind again shortly. Wait a few seconds, and if still not bound,
            // manage our lifetime accordingly.
            delay(UNBIND_DELAY_MILLIS)
            if (!isBound) {
                stopSelfIfNotRunning()
            }
        }
        // Allow clients to re-bind. We will be informed of this in onRebind().
        return true
    }

    private fun removeOngoingActivityNotification() {
        if (serviceRunningInForeground) {
            Log.d(ContentValues.TAG, "Removing ongoing activity notification")
            serviceRunningInForeground = false
            stopForeground(STOP_FOREGROUND_REMOVE)

        }
    }

    private fun postOngoingActivityNotification() {
        if (!serviceRunningInForeground) {
            serviceRunningInForeground = true
            Log.d(ContentValues.TAG, "Posting ongoing activity notification")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            NOTIFICATION_CHANNEL_DISPLAY,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(notificationChannel)
    }

    private fun buildNotification(): Notification {
        // Make an intent that will take the user straight to the exercise UI.
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Build the notification.
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(R.drawable.ic_baseline_directions_run_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Ongoing Activity allows an ongoing Notification to appear on additional surfaces in the
        // Wear OS user interface, so that users can stay more engaged with long running tasks.

        val duration = if (lastActiveDurationCheckpoint != null) {
            lastActiveDurationCheckpoint!!.activeDuration + Duration.between(
                lastActiveDurationCheckpoint!!.time,
                Instant.now()
            )
        } else {
            Duration.ZERO
        }


        val startMillis = SystemClock.elapsedRealtime() - duration.toMillis()
        val ongoingActivityStatus = Status.Builder()
            .addTemplate(ONGOING_STATUS_TEMPLATE)
            .addPart("duration", Status.StopwatchPart(startMillis))
            .build()
        val ongoingActivity =
            OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
                .setAnimatedIcon(R.drawable.ic_baseline_directions_run_24)
                .setStaticIcon(R.drawable.ic_baseline_directions_run_24)
                .setTouchIntent(pendingIntent)
                .setStatus(ongoingActivityStatus)
                .build()

        ongoingActivity.apply(applicationContext)

        return notificationBuilder.build()
    }


    /** Local clients will use this to access the service. */
    inner class LocalBinder : Binder() {
        fun getService() = this@ForegroundService
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL =
            "com.hci.tom.ONGOING_EXERCISE"
        private const val NOTIFICATION_CHANNEL_DISPLAY = "Ongoing Exercise"
        private const val NOTIFICATION_TITLE = "TOM Client"
        private const val NOTIFICATION_TEXT = "Ongoing Exercise"
        private const val ONGOING_STATUS_TEMPLATE = "Ongoing Exercise #duration#"
        private const val UNBIND_DELAY_MILLIS = 3_000L

    }


}


/** Keeps track of the last time we received an update for active exercise duration. */
data class ActiveDurationUpdate(
    /** The last active duration reported. */
    val duration: Duration = Duration.ZERO,
    /** The instant at which the last duration was reported. */
    val timestamp: Instant = Instant.now()

)

sealed class ExerciseStateChange(val exerciseState: ExerciseState) {
    data class ActiveStateChange(val durationCheckPoint: ExerciseUpdate.ActiveDurationCheckpoint) :
        ExerciseStateChange(
            ExerciseState.ACTIVE
        )

    data class OtherStateChange(val state: ExerciseState) : ExerciseStateChange(state)
}

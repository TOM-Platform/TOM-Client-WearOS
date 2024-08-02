package com.hci.tom.android.network

import ExerciseWearOsDataOuterClass
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.hci.tom.android.db.ExerciseData
import com.hci.tom.android.db.ExerciseDataDatabase
import com.hci.tom.android.network.Credentials.SERVER_URL
import com.hci.tom.android.proto.DataTypes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

class SendExerciseDataWorker(
    context: Context, params: WorkerParameters
) : Worker(context, params), ReconnectableWorker {

    private val client = OkHttpClient()
    private val request = Request.Builder()
        .url(SERVER_URL)
        .addHeader("websocket_client_type", "wearOS")
        .build()

    private val webSocketListener = TOMWebSocketListener(this)
    private lateinit var webSocket: WebSocket

    // the time interval between each update to the server
    private val updateInterval = 2000L
    private val exerciseDataDao =
        ExerciseDataDatabase.getInstance(applicationContext).exerciseDataDao()
    private var exerciseEnded = false
    private var retryCount = 0
    private val maxRetries = 10

    // to track if waypoints have been sent to server before
    private var sentWaypoints = false
    private var newestDestLat: Double? = null
    private var newestDestLng: Double? = null

    override fun doWork(): Result = runBlocking {
        try {
            // attempt to get websocket
            webSocket = client.newWebSocket(request, webSocketListener)
            // only send data to server when exercise is not stopped
            while (!isStopped) {
                // check if websocket is open
                if (webSocketListener.isConnected) {
                    // reset retry count if previously failed to connect to server
                    retryCount = 0
                    // to set a fixed time interval between each update to the server
                    delay(updateInterval)
                    // get the most recent exercise data based on the latest start time of the exercise
                    val latestExerciseData = exerciseDataDao.getLatestExerciseData()
                    sendExerciseData(latestExerciseData)

                    val currLat = latestExerciseData?.currLat
                    val currLng = latestExerciseData?.currLng
                    val destLat = latestExerciseData?.destLat
                    val destLng = latestExerciseData?.destLng

                    if (currLat != null && currLng != null) {
                        // if destination changes or waypoints have not been sent to server before
                        if (newestDestLat != destLat || newestDestLng != destLng || !sentWaypoints) {
                            newestDestLat = destLat
                            newestDestLng = destLng
                            sendWaypointsData(currLat, currLng, destLat, destLng)
                        }
                    }

                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    override fun attemptReconnect() {
        // this is to prevent a previous exercise from attempting to reconnect to the server
        // in the case where it was stopped by the user while failing to achieve a connection
        if (exerciseEnded) {
            Log.d(
                "SendExerciseDataWorker",
                "Previous exercise had ended. Stop reconnecting to server."
            )
            return
        }
        retryCount++
        // try reconnecting to server if max retries is not reached
        if (retryCount <= maxRetries) {
            Log.d("SendExerciseDataWorker", "Failed to connect to server. Retrying in 10s...")
            webSocket = client.newWebSocket(request, webSocketListener)
        } else {
            Log.d(
                "SendExerciseDataWorker",
                "Failed to connect to server after $maxRetries attempts."
            )
        }
    }

    override fun onStopped() {
        exerciseEnded = true
        webSocket.close(1000, "WorkManager stopped")
        super.onStopped()
    }

    private fun sendExerciseData(latestExerciseData: ExerciseData?) {
        val exerciseWearOsDataProto = ExerciseWearOsDataOuterClass.ExerciseWearOsData.newBuilder()
            .setCalories(latestExerciseData?.calories ?: 0.0)
            .setCurrentStatus(latestExerciseData?.currentStatus ?: "UNKNOWN")
            .setDistance(latestExerciseData?.distance ?: 0.0)
            .setDuration(latestExerciseData?.duration ?: 0)
            .setHeartRate(latestExerciseData?.heartRate ?: 0.0)
            .setHeartRateAvg(latestExerciseData?.heartRateAvg ?: 0.0)
            .setCurrLat(latestExerciseData?.currLat ?: 0.0)
            .setCurrLng(latestExerciseData?.currLng ?: 0.0)
            .setBearing(latestExerciseData?.bearing ?: 0)
            .setSpeed(latestExerciseData?.speed ?: 0.0)
            .setSpeedAvg(latestExerciseData?.speedAvg ?: 0.0)
            .setStartTime(latestExerciseData?.startTime ?: 0L)
            .setSteps(latestExerciseData?.steps ?: 0)
            .setUpdateTime(latestExerciseData?.updateTime ?: 0L)
            .build()
        val bytes =
            wrapMessageWithMetadata(DataTypes.EXERCISE_WEAR_OS_DATA, exerciseWearOsDataProto)
        webSocketListener.sendBytes(webSocket, bytes)
        // Log the current time when the data is sent
        val currentTimeMillis = System.currentTimeMillis()
        Log.d("SendExerciseDataWorker", "Sent exercise data to server. $currentTimeMillis")
    }

    private fun sendWaypointsData(
        currLat: Double,
        currLng: Double,
        destLat: Double?,
        destLng: Double?
    ) {
        val waypointsListDataProto = WaypointsListDataOuterClass.WaypointsListData.newBuilder()
            .addWaypointsList(
                WaypointData.Waypoint.newBuilder()
                    .setLat(currLat)
                    .setLng(currLng)
                    .build()
            )
        if (destLat != null && destLng != null) {
            waypointsListDataProto.addWaypointsList(
                WaypointData.Waypoint.newBuilder()
                    .setLat(destLat)
                    .setLng(destLng)
                    .build()
            )
        }
        val bytes = wrapMessageWithMetadata(DataTypes.WAYPOINTS_LIST_DATA, waypointsListDataProto.build())
        webSocketListener.sendBytes(webSocket, bytes)
        sentWaypoints = true
        Log.d("SendExerciseDataWorker", "Sent waypoints list data to server.")
    }

    private fun wrapMessageWithMetadata(messageType: Int, message: MessageLite): ByteArray {
        val wrapperBuilder = SocketDataOuterClass.SocketData.newBuilder()
            .setDataType(messageType)
            .setData(ByteString.copyFrom(message.toByteArray()))
        val wrapper = wrapperBuilder.build()
        return wrapper.toByteArray()
    }
}

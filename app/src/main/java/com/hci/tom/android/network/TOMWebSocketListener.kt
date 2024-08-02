package com.hci.tom.android.network

import android.util.Log
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

open class TOMWebSocketListener(private val worker: ReconnectableWorker): WebSocketListener() {
    var isConnected = false
    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("TOMWebSocketListener", "Websocket opened")
        isConnected = true
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("TOMWebSocketListener","Received message: $text")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w("TOMWebSocketListener", t.stackTraceToString())
        isConnected = false
        worker.attemptReconnect()
    }

    fun sendBytes(webSocket: WebSocket, bytes: ByteArray?) {
        try {
            val byteString = ByteString.of(*bytes!!)
            webSocket.send(byteString)
        } catch (e: Exception) {
            Log.e("TOMWebSocketListener", e.stackTraceToString())
        }
    }
}
package com.hci.tom.android.db

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class DestinationDataStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("selectedLocation")
    val selectedLocation: Flow<DestinationData?> = dataStore.data
        .map { preferences ->
            val serializedLocation = preferences[key]
            serializedLocation?.let {
                Json.decodeFromString(DestinationData.serializer(), it)
            }
        }

    fun saveDestination(destinationData: DestinationData) {
        runBlocking {
            dataStore.edit { preferences ->
                Log.d("LocationDataStore", Json.encodeToString(DestinationData.serializer(), destinationData))
                preferences[key] = Json.encodeToString(DestinationData.serializer(), destinationData)
            }
        }
    }

    fun clearDestination() {
        runBlocking {
            Log.d("LocationDataStore", "clearDestination")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }
        }
    }
}

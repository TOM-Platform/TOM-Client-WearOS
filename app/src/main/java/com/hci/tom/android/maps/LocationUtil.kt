package com.hci.tom.android.maps

import android.util.Log
import com.google.maps.GeoApiContext
import com.google.maps.PlacesApi
import com.hci.tom.android.db.DestinationData
import com.hci.tom.android.network.ApiKeys
import com.hci.tom.android.network.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LocationUtil {
    internal suspend fun fetchLocationSuggestionsOSM(searchText: String): List<DestinationData> {
        val destinationDataList = mutableListOf<DestinationData>()
        withContext(Dispatchers.IO) {
            val query = URLEncoder.encode(searchText, "UTF-8")
            val url = "${Credentials.OSM_BASE_URL}?q=$query&format=geocodejson"
            val connection = URL(url).openConnection() as HttpURLConnection

            val responseCode = connection.responseCode
            // check if response is ok, then parse response accordingly
            val response: String = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream.bufferedReader().use { it.readText() }
            }
            Log.d("LocationSuggestions", response)
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val features = JSONObject(response).getJSONArray("features")

                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val properties = feature.getJSONObject("properties")
                    val geocoding = properties.getJSONObject("geocoding")
                    val geometry = feature.getJSONObject("geometry")

                    val address = geocoding.optString("label")
                    val name = geocoding.optString("name")

                    var lat: Double? = null
                    var lng: Double? = null

                    when (geometry.optString("type")) {
                        "Point" -> {
                            val coordinatesArray = geometry.getJSONArray("coordinates")
                            lat = coordinatesArray.optDouble(1)
                            lng = coordinatesArray.optDouble(0)
                        }
                    }

                    val destinationData =
                        DestinationData(address, name, lat ?: 0.0, lng ?: 0.0)
                    destinationDataList.add(destinationData)
                }
            } else {
                val error = JSONObject(response).getJSONObject("error")
                val message = error.optString("message")
                throw IOException("OSM Error: $message")
            }
        }
        return destinationDataList
    }

    internal fun fetchLocationSuggestionsGoogle(searchText: String) : List<DestinationData> {
        val context: GeoApiContext = GeoApiContext.Builder()
            .apiKey(ApiKeys.GOOGLE_MAP_API_KEY)
            .build()

        val response = PlacesApi.textSearchQuery(context, searchText)
            .await()

        return response.results.map { result ->
            val latLng = result.geometry.location
            val address = result.formattedAddress ?: ""
            DestinationData(address, result.name, latLng.lat, latLng.lng)
        }
    }
}
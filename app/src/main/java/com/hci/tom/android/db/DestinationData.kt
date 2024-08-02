package com.hci.tom.android.db

import kotlinx.serialization.Serializable

@Serializable
data class DestinationData(
    val address: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
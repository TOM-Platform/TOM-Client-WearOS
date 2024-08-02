package com.hci.tom.android.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Text

@Composable
fun SpeedText(speed: Double) {
    Text(text = formatSpeed(speed).toString())
}

@Preview
@Composable
fun SpeedTextPreview() {
    SpeedText(speed = 10.0)
}

package com.hci.tom.android.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Text

@Composable
fun StepsText(steps: Int) {
    Text(text = formatSteps(steps).toString())

}

@Preview
@Composable
fun StepsTextPreview() {
    StepsText(steps = 750)
}
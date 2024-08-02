package com.hci.tom.android.presentation.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

fun Modifier.heightRatio(ratio: Float): Modifier {
    return layout { measurable, constraints ->
        val height = (constraints.maxHeight * ratio).roundToInt()
        val placeable = measurable.measure(constraints.copy(maxHeight = height))
        layout(placeable.width, height) {
            placeable.placeRelative(0, 0)
        }
    }
}
package edu.illinois.odim.dataclasses

import android.graphics.Bitmap

data class ScreenShotPreview (
    var screenShot : Bitmap,
    var event: String, // event
    var timestamp: String,  // time stamp
    var isComplete: Boolean,  // flag for null source events
    var isSelected: Boolean = false  // flag for multi-selection
)

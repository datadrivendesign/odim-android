package edu.illinois.odim

import android.graphics.Bitmap

class ScreenShotPreview (
    var screenShot : Bitmap,
    var event: String, // event
    var timestamp: String,  // time stamp
    var isComplete: Boolean  // flag for null source events
)

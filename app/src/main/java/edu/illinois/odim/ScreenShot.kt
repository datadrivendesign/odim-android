package edu.illinois.odim

import android.graphics.Bitmap
import android.graphics.Rect

class ScreenShot(
    var bitmap: Bitmap?,
    var rect: Rect?,
    action_type: Int,
    scroll_coords: Pair<Int, Int>?,
    var vh: ArrayList<Rect>?
) {
    companion object {
        const val TYPE_CLICK = 0
        const val TYPE_SCROLL = 1
        const val TYPE_LONG_CLICK = 2
        const val TYPE_SELECT = 3
    }

    var actionType : Int = action_type

    var scrollCoords : Pair<Int, Int>? = scroll_coords  // (x scroll delta, y scroll delta)

}
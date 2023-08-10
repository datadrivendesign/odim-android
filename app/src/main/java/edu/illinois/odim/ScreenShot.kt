package edu.illinois.odim

import android.graphics.Bitmap
import android.graphics.Rect

class ScreenShot {
    companion object {
        const val TYPE_CLICK = 0
        const val TYPE_SCROLL = 1
        const val TYPE_LONG_CLICK = 2
        const val TYPE_SELECT = 3
    }

    var bitmap: Bitmap?

    var rect: Rect?

    var actionType : Int

    var scrollCoords : Pair<Int, Int>?  // (x scroll delta, y scroll delta)

    var vh: ArrayList<Rect>?

    constructor(bitmap: Bitmap?, rect: Rect?, action_type: Int, scroll_coords: Pair<Int, Int>?, vh: ArrayList<Rect>?) {
        this.bitmap = bitmap
        this.rect = rect
        this.actionType = action_type
        this.scrollCoords = scroll_coords
        this.vh = vh
    }
}
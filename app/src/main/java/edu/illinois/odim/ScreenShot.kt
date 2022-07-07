package edu.illinois.odim

import android.graphics.Bitmap
import android.graphics.Rect

class ScreenShot {
    companion object {
        val TYPE_CLICK = 0
        val TYPE_SCROLL = 1
        val TYPE_LONG_CLICK = 2
        val TYPE_SELECT = 3
    }

    var bitmap: Bitmap?
        get() = this.bitmap
        set(bitmap : Bitmap?) {
            this.bitmap = bitmap
        }

    var rect: Rect?
        get() = this.rect
        set(rect : Rect?) {
            this.rect = rect
        }

    var action_type : Int
        get() = this.action_type
        set(action_type : Int) {
            this.action_type = action_type
        }

    var vh: ArrayList<Rect>?
        get() = this.vh
        set(vh : ArrayList<Rect>?) {
            this.vh = vh
        }

    constructor(bitmap: Bitmap?, rect: Rect?, action_type: Int, vh: ArrayList<Rect>?) {
        this.bitmap = bitmap
        this.rect = rect
        this.action_type = action_type
        this.vh = vh
    }
}
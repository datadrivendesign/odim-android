package edu.illinois.odim

import android.graphics.Bitmap
import android.graphics.Rect

class ScreenShot {
    val TYPE_CLICK = 0
    val TYPE_SCROLL = 1
    val TYPE_LONG_CLICK = 2
    val TYPE_SELECT = 3

    private var bitmap: Bitmap? = null
    private var rect: Rect? = null
    private var action_type = 0

    private var vh: ArrayList<Rect>? = null

    fun ScreenShot(bitmap: Bitmap?, rect: Rect?, action_type: Int, vh: ArrayList<Rect>?) {
        this.bitmap = bitmap
        this.rect = rect
        this.action_type = action_type
        this.vh = vh
    }

    fun getBitmap(): Bitmap? {
        return bitmap
    }

    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    fun getRect(): Rect? {
        return rect
    }

    fun setRect(rect: Rect?) {
        this.rect = rect
    }

    fun getAction_type(): Int {
        return action_type
    }

    fun setAction_type(action_type: Int) {
        this.action_type = action_type
    }

    fun getVh(): ArrayList<Rect>? {
        return vh
    }

    fun setVh(vh: ArrayList<Rect>?) {
        this.vh = vh
    }
}
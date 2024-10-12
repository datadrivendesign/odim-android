package edu.illinois.odim.utils

import android.graphics.Rect
import kotlin.math.roundToInt

object ScreenDimensionsOps {
    fun convertRectFromScaleScreenToBitmap(
        rect: Rect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        canvasHeight: Int
    ): Rect {
        return Rect(
            convertScaleScreenXToBitmapX(rect.left, bitmapWidth, bitmapHeight, canvasHeight),
            convertScaleScreenYToBitmapY(rect.top, bitmapHeight, canvasHeight),
            convertScaleScreenXToBitmapX(rect.right, bitmapWidth, bitmapHeight, canvasHeight),
            convertScaleScreenYToBitmapY(rect.bottom, bitmapHeight, canvasHeight)
        )
    }

    fun convertScaleBitmapXToScreenX(
        bitmapX: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        canvasHeight: Int
    ): Int {
        val canvasImageWidth = bitmapWidth * (canvasHeight.toDouble() / bitmapHeight)
        val canvasImageWidthOffset = (bitmapWidth - canvasImageWidth) / 2
        val screenX = (bitmapHeight.toDouble() / canvasHeight) * (bitmapX - canvasImageWidthOffset)
        // out of range of screen, return a -1. This can happen if users touch outside image
        if (screenX < 0 || screenX > bitmapWidth) {
            return -1
        }
        return screenX.roundToInt()
    }

    fun convertScaleBitmapYToScreenY(bitmapY: Int, bitmapHeight: Int, canvasHeight: Int): Int {
        val screenY = (bitmapHeight.toDouble() / canvasHeight) * bitmapY
        // out of range check, just in case int multiplication causes edge cases
        if (screenY < 0 || screenY > bitmapHeight) {
            return -1
        }
        return screenY.roundToInt()
    }

    fun convertScaleScreenXToBitmapX(
        screenX: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        canvasHeight: Int
    ): Int {
        val canvasImageWidth = bitmapWidth * (canvasHeight.toDouble() / bitmapHeight)
        val canvasImageWidthOffset = (bitmapWidth - canvasImageWidth) / 2
        val bitmapX = (screenX * canvasHeight.toDouble()) / bitmapHeight + canvasImageWidthOffset
        // out of range of screen, return a -1. This can happen if users touch outside image
        if (bitmapX < 0 || bitmapX > bitmapWidth) {
            return -1
        }
        return bitmapX.roundToInt()
    }

    fun convertScaleScreenYToBitmapY(screenY: Int, bitmapHeight: Int, canvasHeight: Int): Int {
        val bitmapY = (canvasHeight.toDouble() / bitmapHeight) * screenY
        // out of range check, just in case int multiplication causes edge cases
        if (bitmapY < 0 || bitmapY > bitmapHeight) {
            return -1
        }
        return bitmapY.roundToInt()
    }
}
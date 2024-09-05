package edu.illinois.odim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import kotlin.math.roundToInt

class IncompleteScreenCanvasOverlay(context: Context, attrs: AttributeSet): View(context, attrs) {
    private val tempPaint = Paint().apply{
        color = Color.CYAN
        style = Paint.Style.FILL
        alpha = 150
    }
    private val confirmPaint = Paint().apply {
        color = Color.BLUE
        alpha = 100
        style = Paint.Style.FILL
    }
    private var imageIntrinsicHeight = 0
    private var imageIntrinsicWidth = 0
    private var imageMeasuredHeight = 0
    private var candidateElements: List<Rect> = listOf()
    var currVHCandidate: Rect? = null
    private var newVHCandidate: Rect? = null
    private var rectsDrawn = false
    private var incompleteScreenSaveScreenButton: Button? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageIntrinsicHeight < 0 || imageIntrinsicWidth < 0) {
            return
        }
        for (candidate in candidateElements) {
            canvas.drawRect(
                convertScaleScreenXToBitmapX(candidate.left).toFloat(),
                convertScaleScreenYToBitmapY(candidate.top).toFloat(),
                convertScaleScreenXToBitmapX(candidate.right).toFloat(),
                convertScaleScreenYToBitmapY(candidate.bottom).toFloat(),
                tempPaint
            )
            rectsDrawn = true
        }
        if (newVHCandidate != null) {
            if (currVHCandidate == null) {
                canvas.drawRect(
                    convertScaleScreenXToBitmapX(newVHCandidate!!.left).toFloat(),
                    convertScaleScreenYToBitmapY(newVHCandidate!!.top).toFloat(),
                    convertScaleScreenXToBitmapX(newVHCandidate!!.right).toFloat(),
                    convertScaleScreenYToBitmapY(newVHCandidate!!.bottom).toFloat(),
                    confirmPaint
                )
                currVHCandidate = newVHCandidate
            } else if (currVHCandidate!!.top == newVHCandidate!!.top &&
                currVHCandidate!!.bottom == newVHCandidate!!.bottom &&
                currVHCandidate!!.left == newVHCandidate!!.left &&
                currVHCandidate!!.right == newVHCandidate!!.right
            ) {  // rects are equal, reset currVHCandidate to null
                currVHCandidate = null
            } else {
                canvas.drawRect(
                    convertScaleScreenXToBitmapX(newVHCandidate!!.left).toFloat(),
                    convertScaleScreenYToBitmapY(newVHCandidate!!.top).toFloat(),
                    convertScaleScreenXToBitmapX(newVHCandidate!!.right).toFloat(),
                    convertScaleScreenYToBitmapY(newVHCandidate!!.bottom).toFloat(),
                    confirmPaint
                )
                currVHCandidate = newVHCandidate
            }
            newVHCandidate = null
            incompleteScreenSaveScreenButton?.isEnabled = (currVHCandidate != null)
        }

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointX = event.x.roundToInt()
        val pointY = event.y.roundToInt()
        // convert coordinates to image size scale
        val convertedX = convertScaleBitmapXToScreenX(pointX)
        val convertedY = convertScaleBitmapYToScreenY(pointY)
        if (convertedX == -1 || convertedY == -1) {
            return false
        }
        var newCandidate = Rect()
        for (candidate in candidateElements) {
            if (candidate.contains(convertedX, convertedY)) {
                newCandidate = candidate
            }
        }
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                // if candidate is new, edit paint
                if (!newCandidate.isEmpty) {
                    // erase if already set
                    newVHCandidate = newCandidate
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun setIntrinsicDimensions(intrinsicWidth: Int, intrinsicHeight: Int, measuredHeight: Int) {
        imageIntrinsicWidth = intrinsicWidth
        imageIntrinsicHeight = intrinsicHeight
        imageMeasuredHeight = measuredHeight
        invalidate()
    }

    fun setIncompleteScreenSaveButton(button: Button) {
        incompleteScreenSaveScreenButton = button
    }

    fun setCandidateElements(elements: MutableList<Rect>) {
        candidateElements = elements.toList()
    }

    fun convertScaleBitmapXToScreenX(bitmapX: Int) : Int {
        val bitmapWidth = imageIntrinsicWidth  // original image width, height
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = imageMeasuredHeight  // canvas height space available
        val canvasImageWidth = bitmapWidth * (canvasImageHeight.toDouble() / bitmapHeight)
        val canvasImageWidthOffset = (bitmapWidth - canvasImageWidth) / 2
        val screenX = (bitmapHeight.toDouble() / canvasImageHeight) * (bitmapX - canvasImageWidthOffset)
        // out of range of screen, return a -1. This can happen if users touch outside image
        if (screenX < 0 || screenX > bitmapWidth) {
            return -1
        }
        return screenX.roundToInt()
    }

    fun convertScaleScreenXToBitmapX(screenX: Int): Int {
        val bitmapWidth = imageIntrinsicWidth  // original image width, height
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = imageMeasuredHeight  // canvas height space available
        val canvasImageWidth = bitmapWidth * (canvasImageHeight.toDouble() / bitmapHeight)
        val canvasImageWidthOffset = (bitmapWidth - canvasImageWidth) / 2
        val bitmapX = (screenX * canvasImageHeight.toDouble()) / bitmapHeight + canvasImageWidthOffset
        // out of range of screen, return a -1. This can happen if users touch outside image
        if (bitmapX < 0 || bitmapX > bitmapWidth) {
            return -1
        }
        return bitmapX.roundToInt()
    }

    fun convertScaleBitmapYToScreenY(bitmapY: Int) : Int {
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val screenY = (bitmapHeight.toDouble() / canvasImageHeight) * bitmapY
        // out of range check, just in case int multiplication causes edge cases
        if (screenY < 0 || screenY > bitmapHeight) {
            return -1
        }
        return screenY.roundToInt()
    }

    fun convertScaleScreenYToBitmapY(screenY: Int): Int {
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val bitmapY = (canvasImageHeight.toDouble() / bitmapHeight) * screenY
        // out of range check, just in case int multiplication causes edge cases
        if (bitmapY < 0 || bitmapY > bitmapHeight) {
            return -1
        }
        return bitmapY.roundToInt()
    }
}
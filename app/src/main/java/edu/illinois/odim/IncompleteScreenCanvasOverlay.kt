package edu.illinois.odim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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
    private var imageIntrinsicWeight = 0
    private var imageMeasuredHeight = 0
    private var candidateElements: List<Rect> = listOf()
    var currVHCandidate: Rect? = null
    private var newVHCandidate: Rect? = null
    private var rectsDrawn = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageIntrinsicHeight < 0 || imageIntrinsicWeight < 0) {
            return
        }
        for (candidate in candidateElements) {
            canvas.drawRect(candidate, tempPaint)
            rectsDrawn = true
        }
        if (newVHCandidate != null) {
            if (currVHCandidate == null) {
                canvas.drawRect(newVHCandidate!!, confirmPaint)
                currVHCandidate = Rect(newVHCandidate)
            } else if (currVHCandidate!!.top == newVHCandidate!!.top &&
                currVHCandidate!!.bottom == newVHCandidate!!.bottom &&
                currVHCandidate!!.left == newVHCandidate!!.left &&
                currVHCandidate!!.right == newVHCandidate!!.right
            ) {  // rects are equal, reset currVHCandidate to null
                currVHCandidate = null
            } else {
                canvas.drawRect(newVHCandidate!!, confirmPaint)
                currVHCandidate = Rect(newVHCandidate)
            }
            newVHCandidate = null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointX = event.x.roundToInt()
        val pointY = event.y.roundToInt()
        // convert coordinates to image size scale
        val convertedX = convertXToImageScale(pointX)
        val convertedY = convertYToImageScale(pointY)
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
        imageIntrinsicWeight = intrinsicWidth
        imageIntrinsicHeight = intrinsicHeight
        imageMeasuredHeight = measuredHeight
        invalidate()
    }

    fun setCandidateElements(elements: MutableList<Rect>) {
        candidateElements = elements.toList()
    }

    fun convertXToImageScale(x: Int) : Int {
        val bitmapWidth = imageIntrinsicWeight  // original image width, height
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val canvasImageWidth = bitmapWidth * (canvasImageHeight.toDouble() / bitmapHeight)
        val canvasImageWidthOffset = (bitmapWidth - canvasImageWidth) / 2
        val convertedX = (bitmapHeight.toDouble() / canvasImageHeight) * (x - canvasImageWidthOffset)
        // out of range of screen, return a -1. This can happen if users touch outside image
        if (convertedX < 0 || convertedX > bitmapWidth) {
            return -1
        }
        return convertedX.roundToInt()
    }

    fun convertYToImageScale(y: Int) : Int {
        val bitmapHeight = imageIntrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val convertedY = (bitmapHeight.toDouble() / canvasImageHeight) * y
        // out of range check, just in case int multiplication causes edge cases
        if (convertedY < 0 || convertedY > bitmapHeight) {
            return -1
        }
        return convertedY.roundToInt()
    }
}
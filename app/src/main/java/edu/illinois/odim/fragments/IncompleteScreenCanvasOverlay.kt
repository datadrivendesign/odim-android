package edu.illinois.odim.fragments

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import edu.illinois.odim.dataclasses.GestureCandidate
import edu.illinois.odim.utils.ScreenDimensionsOps.convertRectFromScaleScreenToBitmap
import edu.illinois.odim.utils.ScreenDimensionsOps.convertScaleBitmapXToScreenX
import edu.illinois.odim.utils.ScreenDimensionsOps.convertScaleBitmapYToScreenY
import kotlin.math.roundToInt

class IncompleteScreenCanvasOverlay(context: Context, attrs: AttributeSet): View(context, attrs) {
    private val tempPaint = Paint().apply{
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4F
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
    private var candidateElements: List<GestureCandidate> = listOf()
    var currVHCandidate: GestureCandidate? = null
    private var newVHCandidate: GestureCandidate? = null
    private var rectsDrawn = false
    private var incompleteScreenSaveScreenButton: MovableFloatingActionButton? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageIntrinsicHeight < 0 || imageIntrinsicWidth < 0) {
            return
        }
        for (candidate in candidateElements) {
            val scaledCandidate = convertRectFromScaleScreenToBitmap(
                candidate.rect,
                imageIntrinsicWidth,
                imageIntrinsicHeight,
                imageMeasuredHeight
            )
            canvas.drawRect(scaledCandidate, tempPaint)
            rectsDrawn = true
        }
        if (newVHCandidate != null) {
            val scaledCandidate = convertRectFromScaleScreenToBitmap(
                newVHCandidate!!.rect,
                imageIntrinsicWidth,
                imageIntrinsicHeight,
                imageMeasuredHeight
            )
            if (currVHCandidate == null) {
                canvas.drawRect(scaledCandidate, confirmPaint)
                currVHCandidate = newVHCandidate
            } else if (currVHCandidate!!.rect.top == newVHCandidate!!.rect.top &&
                currVHCandidate!!.rect.bottom == newVHCandidate!!.rect.bottom &&
                currVHCandidate!!.rect.left == newVHCandidate!!.rect.left &&
                currVHCandidate!!.rect.right == newVHCandidate!!.rect.right
            ) {  // rects are equal, reset currVHCandidate to null
                currVHCandidate = null
            } else {
                canvas.drawRect(scaledCandidate, confirmPaint)
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
        val convertedX = convertScaleBitmapXToScreenX(
            pointX,
            imageIntrinsicWidth,
            imageIntrinsicHeight,
            imageMeasuredHeight
        )
        val convertedY = convertScaleBitmapYToScreenY(pointY, imageIntrinsicHeight, imageMeasuredHeight)
        if (convertedX == -1 || convertedY == -1) {
            return false
        }
        var currCandidateArea = Int.MAX_VALUE
        var newCandidate = GestureCandidate(Rect(), "")
        for (candidate in candidateElements) {
            if (candidate.rect.contains(convertedX, convertedY)) {
                val candidateArea = candidate.rect.height() * candidate.rect.width()
                if (candidateArea < currCandidateArea) {
                    newCandidate = candidate
                    currCandidateArea = candidateArea
                }
            }
        }
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                // if candidate is new, edit paint
                if (!newCandidate.rect.isEmpty) {
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

    fun setIncompleteScreenSaveButton(button: MovableFloatingActionButton) {
        incompleteScreenSaveScreenButton = button
    }

    fun setCandidateElements(elements: MutableList<GestureCandidate>) {
        candidateElements = elements.toList()
    }
}
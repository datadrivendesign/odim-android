package edu.illinois.odim

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import java.security.KeyStore.Entry.Attribute
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScrubbingView : androidx.appcompat.widget.AppCompatImageView {
    var p1: Point? = null
    var p2: Point? = null
    val paint = Paint()
    var canvas: Canvas? = null
    var rectangles = mutableListOf<Rect>()
    var vhs: ArrayList<Rect>? = null

    constructor(ctx: Context) : super(ctx) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

    constructor(ctx: Context, attr: AttributeSet) : super(ctx, attr) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

    constructor(ctx: Context, attr: AttributeSet, defStyleAttr: Int) : super(
        ctx, attr, defStyleAttr
    ) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

    // Get coordinates on user touch
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.getAction()) {
            MotionEvent.ACTION_DOWN -> {
                val pointX = event!!.getX().roundToInt()
                val pointY = event!!.getY().roundToInt()
                p1 = Point(pointX, pointY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pointX = event!!.getX().roundToInt()
                val pointY = event!!.getY().roundToInt()
                p2 = Point(pointX, pointY)
                postInvalidate()
            }
            else -> {
                return false
            }
        }
        return true
    }

    // Draw rectangle according to coordinates
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (p1 != null && p2 != null) {
            val newrect = Rect(p1!!.x, p1!!.y, p2!!.x, p2!!.y)
            val rectmatch = getMatchingVH(vhs, newrect)
            this.canvas?.drawRect(rectmatch, paint)
            p1 = null
            p2 = null
        }
    }

    private fun getMatchingVH(vhs: ArrayList<Rect>?, rect: Rect): Rect {
        var maxOverlapRatio: Float = 0.0F
        var matched = Rect()
        if (vhs != null) {
            for (vh in vhs) {
                val overLapRatio = calculateOverLapRatio(vh, rect)
                if (overLapRatio > maxOverlapRatio) {
                    maxOverlapRatio = overLapRatio
                    matched = vh
                }
            }
        }
        return matched
    }

    private fun calculateOverLapRatio(baseRect: Rect, inputRect: Rect): Float {
        val overlapArea =
            max(0, min(baseRect.right, inputRect.right) - max(baseRect.left, inputRect.left)) * max(
                0, min(baseRect.bottom, inputRect.bottom) - max(baseRect.top, inputRect.top)
            )
        return overlapArea / getArea(baseRect)
    }

//    fun getAllMatchingVH(vhs: ArrayList<Rect>?): List<Rect> {
//        var matched = mutableListOf<Rect>()
//        for (rect in rectangles) {
//            matched.add(getMatchingVH(vhs, rect))
//        }
//        return matched
//    }

    fun getRects(): List<Rect> {
        return rectangles
    }

    fun getArea(rect: Rect): Float {
        return (rect.width() * rect.height()).toFloat()
    }
}
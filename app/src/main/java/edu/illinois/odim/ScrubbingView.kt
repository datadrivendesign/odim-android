package edu.illinois.odim

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import java.security.KeyStore.Entry.Attribute
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
            rectangles.add(Rect(p1!!.x, p1!!.y, p2!!.x, p2!!.y))
            for (rect in rectangles) {
                canvas.drawRect(rect, paint)
            }
            // Testing out VH mapping
            val vhRects = getAllMatchingVH(vhs)
            for (rect in vhRects) {
                canvas.drawRect(rect, paint)
            }
            p1 = null
            p2 = null
        }
    }

    fun getMatchingVH(vhs: ArrayList<Rect>?, rect: Rect): Rect {
        var matched = Rect()
        if (vhs != null) {
            for (vh in vhs) {
                if (vh.contains(rect)) {
                    if (getArea(matched) == 0 || getArea(vh) < getArea(matched)) {
                        matched = vh
                    }
                }
            }
        }
        return matched
    }

    fun getAllMatchingVH(vhs: ArrayList<Rect>?): List<Rect> {
        var matched = mutableListOf<Rect>()
        for (rect in rectangles) {
            matched.add(getMatchingVH(vhs, rect))
        }
        return matched
    }

    fun getRects(): List<Rect> {
        return rectangles
    }

    fun getArea(rect: Rect): Int {
        return rect.width() * rect.height()
    }
}
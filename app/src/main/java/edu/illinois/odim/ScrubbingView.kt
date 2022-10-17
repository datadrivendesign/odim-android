package edu.illinois.odim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
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
    var paint = Paint()
    var canvas: Canvas? = null

    constructor(ctx: Context) : super(ctx) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

    constructor(ctx: Context, attr: AttributeSet) : super(ctx, attr) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

    constructor(ctx: Context, attr: AttributeSet, defStyleAttr: Int) : super(
        ctx,
        attr,
        defStyleAttr
    ) {
        setWillNotDraw(false)
        visibility = View.VISIBLE
    }

//    fun setCanvas(cvs: Canvas?) : Void {
//        canvas = cvs
//    }

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
            canvas.drawRect(
                p1!!.x.toFloat(),
                p1!!.y.toFloat(),
                p2!!.x.toFloat(),
                p2!!.y.toFloat(),
                paint
            )
            p1 = null
            p2 = null
        }
    }

}
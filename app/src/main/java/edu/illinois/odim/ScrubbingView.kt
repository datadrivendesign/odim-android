package edu.illinois.odim

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class ScrubbingView : androidx.appcompat.widget.AppCompatImageView {
    var p1: Point? = null
    var p2: Point? = null
    val paint = Paint()
    var canvas: Canvas? = null
    var rectangles = mutableListOf<Rect>()
    var vhRects: ArrayList<Rect>? = null
    var vhs: HashMap<String, String> = HashMap()
    var drawMode: Boolean = true
    var originalBitMap: Bitmap? = null

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
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                val pointX = event.x.roundToInt()
                val pointY = event.y.roundToInt()

                // Delete rectangle if in Delete Mode
                if (!drawMode) {
                    for (rect in rectangles) {
                        if (rect.contains(pointX, pointY)) {
                            rectangles.remove(rect)
                            originalBitMap?.let { canvas?.drawBitmap(it, rect, rect, null) }
                            postInvalidate()
                            return true
                        }
                    }
                    return true
                }

                p1 = Point(pointX, pointY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pointX = event.x.roundToInt()
                val pointY = event.y.roundToInt()
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
            val rectmatch = getMatchingVH(vhRects, newrect)
            this.canvas?.drawRect(rectmatch, paint)
            traverse(vhs)
            p1 = null
            p2 = null
        }
    }


    private fun getMatchingVH(vhRects: ArrayList<Rect>?, rect: Rect): Rect {
        var maxOverlapRatio: Float = 0.0F
        var matched = Rect()
        if (vhRects != null) {
            for (vh in vhRects) {
                val overLapRatio = calculateOverLapRatio(vh, rect)
                if (overLapRatio > maxOverlapRatio) {
                    maxOverlapRatio = overLapRatio
                    matched = vh
                }
            }
        }
        rectangles.add(matched)
        return matched
    }

    private fun calculateOverLapRatio(baseRect: Rect, inputRect: Rect): Float {
        val overlapArea =
            max(0, min(baseRect.right, inputRect.right) - max(baseRect.left, inputRect.left)) * max(
                0, min(baseRect.bottom, inputRect.bottom) - max(baseRect.top, inputRect.top)
            )
        return overlapArea / getArea(baseRect)
    }

    private fun traverse(root: HashMap<String, String>?): Pair<Boolean, Boolean> {
        // Base Case
        if (nodeIsMatch(root)) {
            // matching child is found to the rectangle
            return Pair(true, false)  // return pair of isFound flag, isDeleted flag
        }
        // Recursive Case
        val gson = GsonBuilder().create()
        val children = root?.get("children") ?: return Pair(false, false)
        val jsonChildType = object : TypeToken<ArrayList<HashMap<String, String>>>() {}.type
        val childrenArr = gson.fromJson<ArrayList<HashMap<String,String>>>(children, jsonChildType)

        for (i in childrenArr.indices) {
            val isMatch = traverse(childrenArr[i])  //if true, delete child convert back to string
            if (isMatch.first) {
                Log.i("childarr before:", childrenArr.toString())
                val removedElem = childrenArr.removeAt(i)
                Log.i("removed", removedElem.entries.toString())
                root?.set("children", childrenArr.toString())
                Log.i("childarr after:", childrenArr.toString())  // TODO: there are nulls in the view hierarchy json, not sure if that's normal though...

                return Pair(false, true)
            }
            // if already deleted just return and move back up
            if (isMatch.second) {
                return Pair(false, true)
            }
        }
        // something went wrong?
        return Pair(false, false)
    }

    private fun nodeIsMatch(node: Map<String, String>?): Boolean {
        var rectStr = node?.get("bounds_in_screen")
        if (rectStr != null) {
            rectStr = rectStr.substring(5, rectStr.length - 1).trim()
            rectStr = rectStr.replace(", "," ")  // TODO: optimize
            rectStr = rectStr.replace(" - ", " ")
//            val intRectArr = rectArr.map { it.toInt() }.toTypedArray()
//            val currRect = Rect(intRectArr[0], intRectArr[1], intRectArr[2], intRectArr[3])
            val currRect = Rect.unflattenFromString(rectStr)
            for (userRect in rectangles) {
                Log.i("actual", userRect.toString())
                Log.i("test", userRect.flattenToString())
                Log.i("assume", currRect.toString())
                if (userRect.equals(currRect)) {
                    Log.i("status", "bounds found")
                    return true
                }
            }
        }
        return false
    }

//    fun getAllMatchingVH(vhRects: ArrayList<Rect>?): List<Rect> {
//        var matched = mutableListOf<Rect>()
//        for (rect in rectangles) {
//            matched.add(getMatchingVH(vhRects, rect))
//        }
//        return matched
//    }

    private fun getArea(rect: Rect): Float {
        return (rect.width() * rect.height()).toFloat()
    }
}
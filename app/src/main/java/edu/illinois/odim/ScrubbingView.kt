package edu.illinois.odim

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class ScrubbingView : androidx.appcompat.widget.AppCompatImageView {
    private var p1: Point? = null
    private var p2: Point? = null
    private val paint = Paint()
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
            p1 = null
            p2 = null
        }
    }


    private fun getMatchingVH(vhRects: ArrayList<Rect>?, rect: Rect): Rect {
        var maxOverlapRatio = 0.0F
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

    fun traverse(root: HashMap<String, String>?, newRect: Rect): Triple<Boolean, Boolean, HashMap<String, String>?> {
        // Base Case
        if (nodeIsMatch(root, newRect)) {
            // matching child is found to the rectangle
            return Triple(true, false, null)  // return pair of isFound flag, isDeleted flag
        }
        // Recursive Case
        val gson = GsonBuilder().create()
        var children = root?.get("children") ?: return Triple(false, false, null)
        // TODO: get rid of trailing comma at end of array, need to find fix with GSON fromJson doing this
        children = children.replace(",]", "]", true)

        val jsonChildType = object : TypeToken<ArrayList<HashMap<String, String>>>() {}.type
        val childrenArr = gson.fromJson<ArrayList<HashMap<String,String>>>(children, jsonChildType)
        for (i in childrenArr.indices) {
            val isMatch = traverse(childrenArr[i], newRect)  //if true, delete child convert back to string
            if (isMatch.first) {
                childrenArr[i] = hashMapOf("content" to "redacted")
                root["children"] = gson.toJson(childrenArr)

                return Triple(false, true, root)
            }
            // if already deleted just return and move back up
            if (isMatch.second) {
                root["children"] = "[${gson.toJson(isMatch.third)}]"
                return Triple(false, true, root)
            }
        }
        // something went wrong?
        return Triple(false, false, null)
    }

    private fun nodeIsMatch(node: Map<String, String>?, newRect: Rect): Boolean {
        var rectStr = node?.get("bounds_in_screen")
        if (rectStr != null) {
            rectStr = rectStr.substring(5, rectStr.length - 1).trim()
            rectStr = rectStr.replace(", "," ")  // TODO: optimize
            rectStr = rectStr.replace(" - ", " ")
            val currRect = Rect.unflattenFromString(rectStr)
            if (newRect == currRect){
                Log.i("status", "bounds found")
                return true
            }
        }
        return false
    }

    private fun getArea(rect: Rect): Float {
        return (rect.width() * rect.height()).toFloat()
    }
}
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
    private val tempPaint = Paint()
    private val confirmPaint = Paint()
    var canvas: Canvas? = null
    var rectangles = mutableListOf<Rect>()
    var vhRects: ArrayList<Rect>? = null
    var vhs: HashMap<String, String> = HashMap()
    var drawMode: Boolean = true
    var baseBitMap: Bitmap? = null

    init {
        tempPaint.color = Color.GRAY
        tempPaint.style = Paint.Style.FILL
        confirmPaint.color = Color.BLACK
        confirmPaint.style = Paint.Style.FILL
    }

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

    private fun convertXToImageScale(x: Int) : Int {
        val bitmapWidth = this.drawable.intrinsicWidth  // original image width, height
        val bitmapHeight = this.drawable.intrinsicHeight
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

    private fun convertYToImageScale(y: Int) : Int {
        val bitmapHeight = this.drawable.intrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val convertedY = (bitmapHeight.toDouble() / canvasImageHeight) * y
        // out of range check, just in case int multiplication causes edge cases
        if (convertedY < 0 || convertedY > bitmapHeight) {
            return -1
        }
        return convertedY.roundToInt()
    }

    // Get coordinates on user touch
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                val pointX = event.x.roundToInt()
                val pointY = event.y.roundToInt()

                val convertedX = convertXToImageScale(pointX)
                val convertedY = convertYToImageScale(pointY)
                if (convertedX == -1 || convertedY == -1) {
                    postInvalidate()
                    return false
                }

                // Delete rectangle if in Delete Mode
                if (!drawMode) {
                    for (rect in rectangles) {
                        if (rect.contains(convertedX, convertedY)) {
                            rectangles.remove(rect)
                            this.canvas?.drawBitmap(baseBitMap!!, rect, rect, null)
                            postInvalidate()
                            return true
                        }
                    }
                    return true
                }

                p1 = Point(convertedX, convertedY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pointX = event.x.roundToInt()
                val pointY = event.y.roundToInt()

                val convertedX = convertXToImageScale(pointX)
                val convertedY = convertYToImageScale(pointY)
                if (convertedX == -1 || convertedY == -1) {
                    postInvalidate()
                    return false
                }

                p2 = Point(convertedX, convertedY)
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
            val newRect = Rect(p1!!.x, p1!!.y, p2!!.x, p2!!.y)
            val rectMatch = getMatchingVH(vhRects, newRect)
            this.canvas?.drawRect(rectMatch, tempPaint)
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
            return Triple(true, false, root)  // return pair of isFound flag, isContentRemoved flag, currentVHTreeRoot in recursive case
        }
        // Recursive Case
        val gson = GsonBuilder().create()
        val children = root?.get("children") ?: return Triple(false, false, null)
        val jsonChildType = object : TypeToken<ArrayList<HashMap<String, String>>>() {}.type
        val childrenArr = gson.fromJson<ArrayList<HashMap<String,String>>>(children, jsonChildType)
        for (i in childrenArr.indices) {
            // skip children already redacted
            if (childrenArr[i]["content-desc"] != null && childrenArr[i]["content-desc"] == "description redacted.") {
                continue
            }
            val isMatch = traverse(childrenArr[i], newRect)  //if true, delete child convert back to string
            if (isMatch.first && !isMatch.second) {
                Log.i("toDelete", childrenArr[i].map { "${it.key}: ${it.value}" }.joinToString(", "))
                if ("text_field" in childrenArr[i]) {
                    childrenArr[i]["text_field"] = "text redacted."
                }
                childrenArr[i]["content-desc"] = "description redacted."
//                childrenArr[i] = hashMapOf("content" to "redacted")
                root["children"] = gson.toJson(childrenArr)
                this.canvas?.drawRect(newRect, confirmPaint)
                return Triple(true, true, root)
            } else if (isMatch.first && isMatch.second) { // if already deleted just return and move back up
                childrenArr[i] = isMatch.third!!
                root["children"] = gson.toJson(childrenArr)
                return Triple(true, true, root)
            }
        }
        // something went wrong?
        return Triple(false, false, null)
    }

    private fun nodeIsMatch(node: Map<String, String>?, newRect: Rect): Boolean {  // TODO: optimize by comparing strings instead of Rects
        val nodeRectString = node?.get("bounds_in_screen")
        val newRectString = newRect.toString()
        return nodeRectString.equals(newRectString)
    }

    private fun getArea(rect: Rect): Float {
        return (rect.width() * rect.height()).toFloat()
    }
}
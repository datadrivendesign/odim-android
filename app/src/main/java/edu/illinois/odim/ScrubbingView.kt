package edu.illinois.odim

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class ScrubbingView : androidx.appcompat.widget.AppCompatImageView {
    private var p1: Point? = null
    private var p2: Point? = null
    private val tempPaint = Paint()
    private val confirmPaint = Paint()
    var canvas: Canvas? = null
    var currentRedacts = mutableSetOf<Redaction>()
    var vhRects: MutableList<Rect>? = null
    lateinit var vhs: JsonNode
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

    fun convertXToImageScale(x: Int) : Int {
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

    fun convertYToImageScale(y: Int) : Int {
        val bitmapHeight = this.drawable.intrinsicHeight
        val canvasImageHeight = this.measuredHeight  // canvas height space available
        val convertedY = (bitmapHeight.toDouble() / canvasImageHeight) * y
        // out of range check, just in case int multiplication causes edge cases
        if (convertedY < 0 || convertedY > bitmapHeight) {
            return -1
        }
        return convertedY.roundToInt()
    }

    fun convertRedactToRect(redaction: Redaction): Rect {
        return Rect(redaction.startX, redaction.startY, redaction.endX, redaction.endY)
    }

    // Get coordinates on user touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> {
                val pointX = event.x.roundToInt()
                val pointY = event.y.roundToInt()

                val convertedX = convertXToImageScale(pointX)
                val convertedY = convertYToImageScale(pointY)
                if (convertedX == -1 || convertedY == -1) {
                    return false
                }
                // Delete rectangle if in Delete Mode and touch rectangle
                if (!drawMode) {
                    for (redaction in currentRedacts) {
                        val redactRect = convertRedactToRect(redaction)
                        if (redactRect.contains(convertedX, convertedY)) {
                            currentRedacts.remove(redaction)
                            this.canvas?.drawBitmap(baseBitMap!!, redactRect, redactRect, null)
                            return true
                        }
                    }
                    return true
                } else { // edit redaction label if in Draw mode and touch rectangle
                    for (redaction in currentRedacts) {
                        val redactRect = convertRedactToRect(redaction)
                        if (redactRect.contains(convertedX, convertedY)) {
                            // start creating the label form
                            val inputForm = inflate(context, R.layout.layout_redaction_label, null)
                            val redactInputLabel = inputForm.findViewById<EditText>(R.id.redact_label_input)
                            redactInputLabel.setText(redaction.label)
                            // create the popup
                            AlertDialog.Builder(context)
                                .setTitle("Set Label")
                                .setView(inputForm)
                                .setPositiveButton("DONE") { _, _ ->
                                    val label = redactInputLabel.text.toString()
                                    redaction.label = label
                                }
                                .setNegativeButton("EXIT") { dialogInterface, _ ->
                                    dialogInterface.cancel()
                                }
                                .show()
                        }
                    }
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
                    return false
                }

                p2 = Point(convertedX, convertedY)
            }
            else -> {
                return false
            }
        }
        postInvalidate()
        return true
    }

    // Draw rectangle according to coordinates
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (p1 != null && p2 != null) {
            val newRect = Rect(p1!!.x, p1!!.y, p2!!.x, p2!!.y)
            val rectMatch = getMatchingVH(vhRects, newRect)
            // quit early if rectangle isn't visible
            if (rectMatch.height() == 0 || rectMatch.width() == 0) {
                return
            }
            // start creating the label form
            val inputForm = inflate(context, R.layout.layout_redaction_label, null)
            val redactInputLabel = inputForm.findViewById<EditText>(R.id.redact_label_input)
            // draw the rectangle
            this.canvas?.drawRect(rectMatch, tempPaint)
            // create the popup
            val labelDialog: AlertDialog = AlertDialog.Builder(context)
                .setTitle("Set Label")
                .setView(inputForm)
                .setPositiveButton("DONE") { _, _ ->
                    val label = redactInputLabel.text.toString()
                    val redaction = Redaction(rectMatch, label)
                    this.currentRedacts.add(redaction)
                }
                .show()
            labelDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            redactInputLabel?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun afterTextChanged(str: Editable?) {
                    labelDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
                }
            })
            labelDialog.setCancelable(false)
            labelDialog.setCanceledOnTouchOutside(false)
            p1 = null
            p2 = null
        }
    }

    private fun getMatchingVH(vhRects: MutableList<Rect>?, rect: Rect): Rect {
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
        return matched
    }

    private fun calculateOverLapRatio(baseRect: Rect, inputRect: Rect): Float {
        val overlapArea =
            max(0, min(baseRect.right, inputRect.right) - max(baseRect.left, inputRect.left)) * max(
                0, min(baseRect.bottom, inputRect.bottom) - max(baseRect.top, inputRect.top)
            )
        return overlapArea / getArea(baseRect)
    }

    fun traverse(root: JsonNode?, newRect: Rect, mapper: ObjectMapper): Triple<Boolean, Boolean, JsonNode?> {
        // Base Case
        if (nodeIsMatch(root, newRect)) {
            // matching child is found to the rectangle
            return Triple(true, false, root)  // return pair of isFound flag, isContentRemoved flag, currentVHTreeRoot in recursive case
        }
        // Recursive Case
        val children = root?.get("children") ?: return Triple(false, false, null)
        val childrenArr = children as ArrayNode
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i] as ObjectNode//.asJsonObject
            // skip children already redacted
            if (child.has("content-desc") && child["content-desc"].asText() == "description redacted.") {
                continue
            }
            val isMatch = traverse(child, newRect, mapper)
            if (isMatch.first && !isMatch.second) {
                postInvalidate()
                if (child.has("text_field")) {
                    child.remove("text_field")
                    child.put("text_field", "text redacted.")
                }
                child.remove("content-desc")
                child.put("content-desc", "description redacted.")
                this.canvas?.drawRect(newRect, confirmPaint)
                return Triple(true, true, root)
            } else if (isMatch.first && isMatch.second) { // if already deleted just return and move back up
                postInvalidate()
                childrenArr[i] = isMatch.third!!
                return Triple(true, true, root)
            }
        }
        // something went wrong?
        return Triple(false, false, null)
    }

    private fun nodeIsMatch(node: JsonNode?, newRect: Rect): Boolean {
        val nodeRect = Rect.unflattenFromString(node?.get("bounds_in_screen")?.asText())
        return nodeRect == newRect
    }

    private fun getArea(rect: Rect): Float {
        return (rect.width() * rect.height()).toFloat()
    }
}
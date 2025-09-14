package edu.illinois.odim.fragments

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.icu.text.Normalizer2
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import edu.illinois.odim.R
import edu.illinois.odim.dataclasses.Redaction
import edu.illinois.odim.utils.ScreenDimensionsOps.convertRectFromScaleScreenToBitmap
import edu.illinois.odim.utils.ScreenDimensionsOps.convertScaleBitmapXToScreenX
import edu.illinois.odim.utils.ScreenDimensionsOps.convertScaleBitmapYToScreenY
import kotlin.math.roundToInt

class ScrubbingScreenshotOverlay(context: Context, attrs: AttributeSet): View(context, attrs) {
    private val tempPaint = Paint().apply{
        color = Color.GRAY
        style = Paint.Style.FILL
    }
    private val boundingBoxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2F
        color = Color.rgb(255, 0, 0)
    }
    private var imageIntrinsicHeight = 0
    private var imageIntrinsicWidth = 0
    private var imageMeasuredHeight = 0
    var currentRedacts = mutableSetOf<Redaction>()
    var drawMode: Boolean = true
    private var vhRects: MutableList<Rect> = mutableListOf()
    private lateinit var screenVHRoot: JsonNode

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawVHBoundingBoxes(canvas)
        drawCurrentRedacts(canvas)
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
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!drawMode) {  // Delete rectangle if in Delete Mode and touch rectangle
                for (redaction in currentRedacts) {
                    val redactRect = convertRedactToRect(redaction)
                    if (redactRect.contains(convertedX, convertedY)) {
                        currentRedacts.remove(redaction)
                        invalidate()
                        return true
                    }
                }
                return true
            } else {  // draw mode, redact or edit redaction by tapping on VH rectangle
                var selectOwnRedact = false
                for (redaction in currentRedacts) {
                    val redactRect = convertRedactToRect(redaction)
                    if (redactRect.contains(convertedX, convertedY)) {
                        selectOwnRedact = true
                        // start creating the label form
                        val inputForm = inflate(context, R.layout.dialog_label_redaction, null)
                        val redactInputLabel = inputForm.findViewById<EditText>(R.id.redact_label_input)
                        redactInputLabel.setText(redaction.label)
                        // create the popup
                        editRedactLabelAlertDialog(inputForm, redactInputLabel, redaction)
                    }
                }
                if (!selectOwnRedact) {  // check if touched a VH to redact
                    val rectMatch = getMatchingVHFromTap(convertedX, convertedY)
                    // start creating the label form
                    val inputForm = inflate(context, R.layout.dialog_label_redaction, null)
                    val redactInputLabel = inputForm.findViewById<EditText>(R.id.redact_label_input)
                    val redactCheckbox = inputForm.findViewById<CheckBox>(R.id.redact_label_checkbox)
                    val redactKeywordInput = inputForm.findViewById<EditText>(R.id.redact_label_keyword_input)
                    // create the popup
                    createRedactLabelAlertDialog(inputForm, redactInputLabel, redactCheckbox, redactKeywordInput, rectMatch)
                }
            }
        }
        postInvalidate()
        return true
    }

    private fun createRedactLabelAlertDialog(
        inputForm: View,
        labelEditText: EditText,
        redactCheckbox: CheckBox,
        redactKeywordInput: EditText,
        redactRect: Rect
    ) {
        // try to assume the keyword from redaction
        val guessedKeyword = serializeToASCII(getKeywordFromRedact(screenVHRoot, redactRect))
        redactKeywordInput.setText(guessedKeyword)
        val labelDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_label_redact_title))
            .setView(inputForm)
            .setPositiveButton(context.getString(R.string.dialog_done)) { _, _ ->
                val label = labelEditText.text.toString()
                val redaction = Redaction(redactRect, label)
                this.currentRedacts.add(redaction)
                // if keyword box is checked and keyword is not null, recursively find matching rectangles
                if (redactCheckbox.isChecked && redactKeywordInput.text != null) {
                    // get elements of VH that match the keyword
                    val keyword = redactKeywordInput.text.toString()
                    // get the matching elements
                    val matchedRects = getElementsByKeyword(keyword, screenVHRoot)
                    // draw the redacted rectangles
                    for (rect in matchedRects) {
                        val matchedRedact = Redaction(rect, label)
                        currentRedacts.add(matchedRedact)
                    }
                    // reset the keyword input
                    redactKeywordInput.text.clear()
                }
                invalidate()
            }
            .setNegativeButton(context.getString(R.string.dialog_close)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }.create()
        labelDialog.show()
        labelDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        labelEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(str: Editable?) {
                labelDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
            }
        })
        labelDialog.setCancelable(false)
        labelDialog.setCanceledOnTouchOutside(false)
    }

    /**
     * Automatically get the relevant keyword from redacted phrase
     */
    private fun getKeywordFromRedact(root: JsonNode, redactRect: Rect): String {
        // base case
        val nodeRect = Rect.unflattenFromString(root.get("bounds_in_screen")?.asText())
        if (nodeRect == redactRect) {
            return if (root.has("text_field")) root["text_field"].asText() else ""
            //return if (root.has("text_field")) root["text_field"].asText() else root["content-desc"].asText()
        }
        // recursive case
        if (root.has("children")) {
            val children = root["children"] as ArrayNode
            for (i in 0 until children.size()) {
                val keyword = getKeywordFromRedact(children[i], redactRect)
                if (keyword.isNotEmpty()) {
                    return keyword
                }
            }
        }
        return ""
    }

    /**
     * Cleaning strings that we get for keywords...Apparently devs like hairline spaces...
     */
    private fun serializeToASCII(text: String): String {
        // Get the NFKD Normalizer using the recommended method
        val normalizer = Normalizer2.getNFKDInstance()
        // Normalize the text using NFKD (decomposition normalization)
        val normalizedText = normalizer.normalize(text)
        // Remove non-ASCII characters by keeping only characters with code <= 127 (ASCII range)
        val asciiText = normalizedText.filter { it.code <= 127 }
        return asciiText
    }

    /**
     * Return the list of rectangles that recursively match the keyword in the given json node
     */
    private fun getElementsByKeyword(keyword: String, root: JsonNode): MutableList<Rect> {
        val matchedRects = mutableListOf<Rect>()
        if (root.has("text_field") && root["text_field"].asText().contains(keyword)) {
            Log.d("keyword", root["text_field"].asText())
            Rect.unflattenFromString(root["bounds_in_screen"].asText())?.let { matchedRects.add(it) }
        }
//        if (root.has("content-desc") && root["content-desc"].asText().contains(keyword)) {
//            Log.d("keyword", root["content-desc"].asText())
//            Rect.unflattenFromString(root["bounds_in_screen"].asText())
//                ?.let { matchedRects.add(it) }
//        }
        if (root.has("children")) {
            val children = root["children"] as ArrayNode
            for (i in 0 until children.size()) {
                matchedRects.addAll(getElementsByKeyword(keyword, children[i]))
            }
        }
        return matchedRects
    }

    private fun editRedactLabelAlertDialog(inputForm: View, labelEditText: EditText, redaction: Redaction) {
        val labelDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_edit_redact_title))
            .setView(inputForm)
            .setPositiveButton(context.getString(R.string.dialog_done)) { _, _ ->
                val label = labelEditText.text.toString()
                redaction.label = label
            }
            .setNegativeButton(context.getString(R.string.dialog_close)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }.create()
        labelDialog.show()
    }

    /**
     * Draw red bounding boxes where VH elements are, based on screenshot view hierarchy
     * input takes boxes as an array of rectangles representing locations of VH elements
     * red box borders are drawn on the canvas.
     */
    private fun drawVHBoundingBoxes(canvas: Canvas) {
        for (element in vhRects) {
            // convert vh elements boxes from screen to bitmap coordinates
            val scaledElement = convertRectFromScaleScreenToBitmap(
                element,
                imageIntrinsicWidth,
                imageIntrinsicHeight,
                imageMeasuredHeight
            )
            canvas.drawRect(scaledElement, boundingBoxPaint)
        }
    }

    private fun drawCurrentRedacts(canvas: Canvas) {
        for (redact in currentRedacts) {
            val redactRect: Rect = convertRedactToRect(redact)
            val scaledRedactRect = convertRectFromScaleScreenToBitmap(
                redactRect,
                imageIntrinsicWidth,
                imageIntrinsicHeight,
                imageMeasuredHeight
            )
            canvas.drawRect(scaledRedactRect, tempPaint)
        }
    }

    private fun getMatchingVHFromTap(tapX: Int, tapY: Int): Rect {
        var currCandidateArea = Int.MAX_VALUE
        var newCandidate = Rect()
        for (candidateRect in vhRects) {
            if (candidateRect.contains(tapX, tapY)) {
                val candidateArea = candidateRect.height() * candidateRect.width()
                if (candidateArea < currCandidateArea) {
                    newCandidate = candidateRect
                    currCandidateArea = candidateArea
                }
            }
        }
        return newCandidate
    }

    fun convertRedactToRect(redaction: Redaction): Rect {
        return Rect(redaction.startX, redaction.startY, redaction.endX, redaction.endY)
    }

    fun setIntrinsicDimensions(intrinsicWidth: Int, intrinsicHeight: Int, measuredHeight: Int) {
        imageIntrinsicWidth = intrinsicWidth
        imageIntrinsicHeight = intrinsicHeight
        imageMeasuredHeight = measuredHeight
        invalidate()
    }

    fun setVHRects(screenVHRects: MutableList<Rect>) {
        vhRects = screenVHRects
    }

    fun setScreenVHRoot(vHRoot: JsonNode) {
        screenVHRoot = vHRoot
    }
}
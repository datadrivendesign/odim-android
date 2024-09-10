package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import edu.illinois.odim.LocalStorageOps.loadScreenshot
import edu.illinois.odim.LocalStorageOps.loadVH
import edu.illinois.odim.LocalStorageOps.saveRedactions
import edu.illinois.odim.LocalStorageOps.saveScreenshot
import edu.illinois.odim.LocalStorageOps.saveVH

class ScreenShotActivity : AppCompatActivity() {
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var chosenEventLabel: String? = null
    private lateinit var canvas: Canvas
    private val confirmPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private lateinit var canvasBitmap: Bitmap
    private var vhBoxes: MutableList<Rect> = arrayListOf()
    private val mapper = ObjectMapper()
    private lateinit var screenVHRoot: JsonNode
    private lateinit var scrubbingImageView: ImageView
    private lateinit var scrubbingOverlayView: ScrubbingScreenshotOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_screenshot)
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        chosenEventLabel = intent.extras!!.getString("event_label")
        title = "ScreenShot (Click Image for VH)"
        // populate UI elements
        val screenshot: Bitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        canvasBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        scrubbingImageView = findViewById(R.id.scrubbing_image)
        scrubbingImageView.setImageBitmap(canvasBitmap)
        // once imageview is rendered, get image heights and widths for scaling conversion calculations
        scrubbingImageView.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val intrinsicWidth = scrubbingImageView.drawable.intrinsicWidth
                val intrinsicHeight = scrubbingImageView.drawable.intrinsicHeight
                val measuredHeight = scrubbingImageView.measuredHeight
                scrubbingOverlayView.setIntrinsicDimensions(intrinsicWidth, intrinsicHeight, measuredHeight)
                scrubbingImageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        scrubbingOverlayView = findViewById(R.id.scrubbing_overlay)
        screenshot.recycle()
        // extract view hierarchy boxes from VH
        val vhJsonString = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        screenVHRoot = mapper.readTree(vhJsonString.trim())
        scrubbingOverlayView.setScreenVHRoot(screenVHRoot)
        extractVHBoxes(screenVHRoot, vhBoxes)
        scrubbingOverlayView.setVHRects(vhBoxes)
        canvas = Canvas(canvasBitmap)
        // Save Listener
        setUpSaveRedactFloatingActionButton()
        // Delete Listener
        setUpDeleteRedactFloatingActionButton()
    }

    private fun setUpSaveRedactFloatingActionButton() {
        val saveFAB: MovableFloatingActionButton = findViewById(R.id.save_redact_fab)
        saveFAB.setOnClickListener {
            // don't save if no redactions have been drawn
            if (scrubbingOverlayView.currentRedacts.isEmpty()) {
                return@setOnClickListener
            }
            // loop through imageView.rectangles
            for (drawnRedaction: Redaction in scrubbingOverlayView.currentRedacts) {
                // traverse each rectangle
                val redactRect = scrubbingOverlayView.convertRedactToRect(drawnRedaction)
                traverse(screenVHRoot, redactRect)
                saveVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, mapper.writeValueAsString(screenVHRoot))
                saveRedactions(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, drawnRedaction)
            }
            // update screenshot bitmap in the map (no need to set in map, just set bitmap property)
            saveScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, canvasBitmap)
            // clear all temporary redactions
            scrubbingOverlayView.currentRedacts.clear()
            scrubbingOverlayView.invalidate()
            notifyEventAdapter()
        }
    }


    private fun setUpDeleteRedactFloatingActionButton() {
        val deleteFAB: FloatingActionButton = findViewById(R.id.delete_redact_fab)
        deleteFAB.setOnClickListener { view ->
            scrubbingOverlayView.drawMode = !scrubbingOverlayView.drawMode
            if (scrubbingOverlayView.drawMode) {
                val drawColor = Color.argb(250, 181, 202, 215)
                view.backgroundTintList = ColorStateList.valueOf(drawColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_edit)
            } else {
                val deleteColor = Color.argb(250, 255, 100, 150)
                view.backgroundTintList = ColorStateList.valueOf(deleteColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_delete)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // setup image bitmaps for scrubbing view
        chosenEventLabel = intent.extras!!.getString("event_label")
        val screenshot: Bitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        canvasBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        scrubbingImageView = findViewById(R.id.scrubbing_image)
        scrubbingImageView.setImageBitmap(canvasBitmap)
        scrubbingImageView.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val intrinsicWidth = scrubbingImageView.drawable.intrinsicWidth
                val intrinsicHeight = scrubbingImageView.drawable.intrinsicHeight
                val measuredHeight = scrubbingImageView.measuredHeight
                scrubbingOverlayView.setIntrinsicDimensions(intrinsicWidth, intrinsicHeight, measuredHeight)
                scrubbingImageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        scrubbingOverlayView = findViewById(R.id.scrubbing_overlay)
        screenshot.recycle()
        // extract view hierarchy boxes from VH
        val vhJsonString = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        screenVHRoot = mapper.readTree(vhJsonString.trim())
        extractVHBoxes(screenVHRoot, vhBoxes)
        scrubbingOverlayView.setVHRects(vhBoxes)
        // re-set up canvas
        canvas = Canvas(canvasBitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        canvasBitmap.recycle()
        vhBoxes.clear()
    }

    private fun extractVHBoxes(root: JsonNode, vhBoxes: MutableList<Rect>) {
        if (root.get("visibility").asBoolean()) {
            val vhBoxString = root.get("bounds_in_screen").asText()
            val vhBoxRect = Rect.unflattenFromString(vhBoxString)
            if (vhBoxRect != null){
                vhBoxes.add(vhBoxRect)
            }
        }
        // Base Case
        val children = root.get("children") ?: return
        val childrenArr = children as ArrayNode
        if (childrenArr.isEmpty) {
            return
        }
        // Recursive Case
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i]
            extractVHBoxes(child, vhBoxes)
        }
    }

    private fun traverse(root: JsonNode?, newRect: Rect): Triple<Boolean, Boolean, JsonNode?> {
        // Base Case
        if (nodeIsMatch(root, newRect)) {
            // matching child is found to the rectangle
            return Triple(true, false, root)  // return pair of isFound flag, isContentRemoved flag, currentVHTreeRoot in recursive case
        }
        // Recursive Case
        val children = root?.get("children") ?: return Triple(false, false, null)
        val childrenArr = children as ArrayNode
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i] as ObjectNode
            // skip children already redacted
            if (child.has("content-desc") && child["content-desc"].asText() == "description redacted.") {
                continue
            }
            val isMatch = traverse(child, newRect)
            if (isMatch.first && !isMatch.second) {
                if (child.has("text_field")) {
                    child.remove("text_field")
                    child.put("text_field", "text redacted.")
                }
                child.remove("content-desc")
                child.put("content-desc", "description redacted.")
                canvas.drawRect(newRect, confirmPaint)
                scrubbingImageView.invalidate()
                return Triple(true, true, root)
            } else if (isMatch.first && isMatch.second) { // if already deleted just return and move back up
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
}
package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import edu.illinois.odim.LocalStorageOps.loadScreenshot
import edu.illinois.odim.LocalStorageOps.loadVH
import edu.illinois.odim.LocalStorageOps.saveRedaction
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
        // VH Item listener
        setUpVHTextRedactFloatingActionButton()
        // Save Listener
        setUpSaveRedactFloatingActionButton()
        // Delete Listener
        setUpDeleteRedactFloatingActionButton()
    }

    private fun setUpVHTextRedactFloatingActionButton() {
        val vhItemFAB: MovableFloatingActionButton = findViewById(R.id.vh_list_redact_fab)
        vhItemFAB.setOnClickListener {
            val vhListView = View.inflate(this, R.layout.vh_item_dialog_list, null)
            val vhRecyclerView: RecyclerView = vhListView.findViewById(R.id.vhItemList)
            vhRecyclerView.layoutManager = LinearLayoutManager(this)
            val vhTextList: ArrayList<VHItem> = arrayListOf()
            extractVHText(screenVHRoot, vhTextList)
            val vhAdapter = VHAdapter(vhTextList)
            vhAdapter.setOnItemClickListener(object: VHAdapter.OnItemClickListener {
                override fun onItemClick(position: Int, vhItem: VHItem): Boolean {
                    val redactDialog = AlertDialog.Builder(this@ScreenShotActivity)
                        .setMessage("Are you sure you want to redact the text? This will " +
                                "only redact metadata, not the visual screen.\n" +
                            "text: ${vhItem.text}\n" +
                            "content description: ${vhItem.contentDesc}")
                        .setPositiveButton("Redact") { _, _  ->
                            redactVHMetadataByText(screenVHRoot, vhItem)
                            saveVH(  // update VH metadata only
                                chosenPackageName!!,
                                chosenTraceLabel!!,
                                chosenEventLabel!!,
                                mapper.writeValueAsString(screenVHRoot)
                            )
                            vhTextList.removeAt(position)
                            vhAdapter.notifyItemRemoved(position)
                            val itemChangeCount = vhTextList.size - position
                            vhAdapter.notifyItemRangeChanged(position, itemChangeCount)
                        }
                        .setNegativeButton("Close") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                    redactDialog.show()
                    return true
                }
            })
            vhRecyclerView.adapter = vhAdapter
            val vhTextListDialog = AlertDialog.Builder(this)
                .setView(vhListView)
                .setNeutralButton("Close") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            vhTextListDialog.show()
        }
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
                redactVHElemByRect(screenVHRoot, redactRect)
                saveVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, mapper.writeValueAsString(screenVHRoot))
                saveRedaction(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, drawnRedaction)
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

    private fun extractVHText(root: JsonNode, vhTextList: ArrayList<VHItem>) {
        val textField = if (root.has("text_field")) root.get("text_field").asText() else ""
        val contentDesc = if (root.has("content-desc")) root.get("content-desc").asText() else ""
        // do not add elements where both text and contentDesc are empty or redacted
        if ((textField.isNotEmpty() && textField != "text redacted.") ||
            (contentDesc != "none" && contentDesc.isNotEmpty() && contentDesc != "description redacted.")) {
            vhTextList.add(VHItem(textField, contentDesc))
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
            extractVHText(child, vhTextList)
        }
    }

    private fun redactVHMetadataByText(root: JsonNode?, vhItem: VHItem): Triple<Boolean, Boolean, JsonNode?> {
        // Base Case
        if (nodeIsMatchText(root, vhItem)) {
            // return pair of isFound flag, isContentRemoved flag, currentVHTreeRoot in recursive case
            return Triple(true, false, root)
        }
        // Recursive Case
        val children = root?.get("children") ?: return Triple(false, false, null)
        val childrenArr = children as ArrayNode
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i] as ObjectNode
            val isMatch = redactVHMetadataByText(child, vhItem)
            if (isMatch.first && !isMatch.second) {
                if (child.has("text_field")) {
                    child.remove("text_field")
                    child.put("text_field", "text redacted.")
                }
                child.remove("content-desc")
                child.put("content-desc", "description redacted.")
                return Triple(true, true, root)
            } else if (isMatch.first && isMatch.second) { // if already deleted just return and move back up
                childrenArr[i] = isMatch.third!!  // update parent with updated child
                return Triple(true, true, root)
            }
        }
        // something went wrong?
        return Triple(false, false, null)
    }

    private fun nodeIsMatchText(node: JsonNode?, vhItem: VHItem): Boolean {
        if (node == null) {
            return false
        }
        var isMatch = false
        if (node.has("text_field")) {
            isMatch = (node.get("text_field").asText() == vhItem.text)
            if (!isMatch) {
                return false
            }
        }
        if (node.has("content-desc")) {
            isMatch = (node.get("content-desc").asText() == vhItem.contentDesc)
        }
        return isMatch
    }

    private fun redactVHElemByRect(root: JsonNode?, newRect: Rect): Triple<Boolean, Boolean, JsonNode?> {
        // Base Case
        if (nodeIsMatchRect(root, newRect)) {
            // return pair of isFound flag, isContentRemoved flag, currentVHTreeRoot in recursive case
            return Triple(true, false, root)
        }
        // Recursive Case
        val children = root?.get("children") ?: return Triple(false, false, null)
        val childrenArr = children as ArrayNode
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i] as ObjectNode
            val isMatch = redactVHElemByRect(child, newRect)
            if (isMatch.first && !isMatch.second) {
                if (child.has("text_field") && child["text_field"].asText() != "text redacted.") {
                    child.remove("text_field")
                    child.put("text_field", "text redacted.")
                }
                if (child["content-desc"].asText() != "description redacted.") {
                    child.remove("content-desc")
                    child.put("content-desc", "description redacted.")
                }
                canvas.drawRect(newRect, confirmPaint)
                scrubbingImageView.invalidate()
                return Triple(true, true, root)
            } else if (isMatch.first && isMatch.second) { // if already deleted just return and move back up
                childrenArr[i] = isMatch.third!!  // need to update child state since we made changes to properties
                return Triple(true, true, root)
            }
        }
        // something went wrong?
        return Triple(false, false, null)
    }

    private fun nodeIsMatchRect(node: JsonNode?, newRect: Rect): Boolean {
        val nodeRect = Rect.unflattenFromString(node?.get("bounds_in_screen")?.asText())
        return nodeRect == newRect
    }
}
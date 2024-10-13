package edu.illinois.odim.activities

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.DisplayMetrics
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
import edu.illinois.odim.R
import edu.illinois.odim.adapters.VHAdapter
import edu.illinois.odim.dataclasses.Redaction
import edu.illinois.odim.dataclasses.VHItem
import edu.illinois.odim.fragments.MovableFloatingActionButton
import edu.illinois.odim.fragments.ScrubbingScreenshotOverlay
import edu.illinois.odim.utils.LocalStorageOps.loadScreenshot
import edu.illinois.odim.utils.LocalStorageOps.loadVH
import edu.illinois.odim.utils.LocalStorageOps.saveRedaction
import edu.illinois.odim.utils.LocalStorageOps.saveScreenshot
import edu.illinois.odim.utils.LocalStorageOps.saveVH


class ScreenShotActivity: AppCompatActivity(), MovableFloatingActionButton.OnPositionChangeListener {
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var chosenEventLabel: String? = null
    private val confirmPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private var isAllFABsVisible = false
    private val mapper = ObjectMapper()
    private var vhBoxes: MutableList<Rect> = arrayListOf()

    private lateinit var canvasBitmap: Bitmap
    private lateinit var screenVHRoot: JsonNode
    private lateinit var scrubbingImageView: ImageView
    private lateinit var scrubbingOverlayView: ScrubbingScreenshotOverlay
    private lateinit var canvas: Canvas
    // FAB UI components
    private lateinit var primaryRedactFAB: MovableFloatingActionButton
    private lateinit var secondaryRedactFAB: FloatingActionButton
    private lateinit var vhItemFAB: FloatingActionButton
    private lateinit var saveFAB: FloatingActionButton
    // Define dynamic spacing and offset variables
    private var offsetFABSpacing: Int = 0 // Spacing between FABs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_screenshot)
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        chosenEventLabel = intent.extras!!.getString("event_label")
        title = getString(R.string.activity_screenshot_title)
        val screenshot: Bitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        // populate UI elements
        setUpUIComponents(screenshot)
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
        // Primary Listener
        setUpPrimaryRedactFloatingActionButton()
        // Secondary (Toggle) Listener
        setUpSecondaryRedactFloatingActionButton()
    }

    private fun setUpUIComponents(screenshot: Bitmap) {
        canvasBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        scrubbingImageView = findViewById(R.id.scrubbing_image)
        scrubbingImageView.setImageBitmap(canvasBitmap)
        // FAB set up
        primaryRedactFAB = findViewById(R.id.primary_redact_fab)
        vhItemFAB = findViewById(R.id.vh_list_redact_fab)
        secondaryRedactFAB = findViewById(R.id.secondary_redact_fab)
        saveFAB = findViewById(R.id.save_redact_fab)
        primaryRedactFAB.setOnPositionChangeListener(this)
        offsetFABSpacing = dpToPx(64) // Spacing between FABs
    }

    override fun onPositionChanged(newX: Float, newY: Float) {
        // Calculate offsets dynamically based on primary FAB's position
        val secondaryFabX = newX + offsetFABSpacing
        val secondaryFabY = newY - offsetFABSpacing
        val vhItemFabY = newY - offsetFABSpacing
        val saveFabX = newX + offsetFABSpacing
        // Update positions of the secondary FABs relative to the primary FAB
        if (secondaryRedactFAB.visibility == View.VISIBLE) {
            secondaryRedactFAB.animate().x(secondaryFabX) // Adjust X based on your layout requirements
                .y(secondaryFabY) // Adjust Y based on your layout requirements
                .setDuration(0).start()
        }
        if (vhItemFAB.visibility == View.VISIBLE) {
            vhItemFAB.animate().x(newX)
                .y(vhItemFabY)
                .setDuration(0).start()
        }
        if (saveFAB.visibility == View.VISIBLE) {
            saveFAB.animate().x(saveFabX)
                .y(newY)
                .setDuration(0).start()
        }
    }

    private fun setUpVHTextRedactFloatingActionButton() {
        vhItemFAB.setOnClickListener {
            val vhListView = View.inflate(this, R.layout.dialog_vh_list, null)
            val vhRecyclerView: RecyclerView = vhListView.findViewById(R.id.vhItemList)
            vhRecyclerView.layoutManager = LinearLayoutManager(this)
            val vhTextList: ArrayList<VHItem> = arrayListOf()
            extractVHText(screenVHRoot, vhTextList)
            val vhAdapter = VHAdapter(vhTextList)
            vhAdapter.setOnItemClickListener(object: VHAdapter.OnItemClickListener {
                override fun onItemClick(position: Int, vhItem: VHItem): Boolean {
                    val redactDialog = AlertDialog.Builder(this@ScreenShotActivity)
                        .setMessage(getString(R.string.dialog_text_redact_message, vhItem.text, vhItem.contentDesc))
                        .setPositiveButton(getString(R.string.dialog_text_redact_positive)) { _, _  ->
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
                        .setNegativeButton(getString(R.string.dialog_close)) { dialog, _ ->
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
                .setNeutralButton(getString(R.string.dialog_close)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            vhTextListDialog.show()
        }
    }

    private fun setUpPrimaryRedactFloatingActionButton() {
        primaryRedactFAB.setOnClickListener { view ->
            isAllFABsVisible = !isAllFABsVisible
            if (isAllFABsVisible) {
                // set up for FABs to appear relative to moveableFAB
                val location = IntArray(2)
                view.getLocationOnScreen(location)  // returns top left corner of view
                val offset = dpToPx(29)
                val locationX = location[0].toFloat()// - offset  // offset to bottom, right
                val locationY = location[1].toFloat() - offset
                // Calculate offsets dynamically based on primary FAB position
                val secondaryFabX = locationX + offsetFABSpacing
                val secondaryFabY = locationY - offsetFABSpacing
                val vhItemFabY = locationY - offsetFABSpacing
                val saveFabX = locationX + offsetFABSpacing
                secondaryRedactFAB.visibility = View.VISIBLE
                secondaryRedactFAB.animate()
                    .x(secondaryFabX)
                    .y(secondaryFabY)
                    .setDuration(0).start()
                vhItemFAB.visibility = View.VISIBLE
                vhItemFAB.animate()
                    .x(locationX)
                    .y(vhItemFabY)
                    .setDuration(0).start()
                saveFAB.visibility = View.VISIBLE
                saveFAB.animate()
                    .x(saveFabX)
                    .y(locationY)
                    .setDuration(0).start()
            } else {
                secondaryRedactFAB.visibility = View.GONE
                vhItemFAB.visibility = View.GONE
                saveFAB.visibility = View.GONE
            }
        }
    }

    private fun setUpSecondaryRedactFloatingActionButton() {
        secondaryRedactFAB.setOnClickListener { view ->
            val primaryFAB: MovableFloatingActionButton = findViewById(R.id.primary_redact_fab)
            scrubbingOverlayView.drawMode = !scrubbingOverlayView.drawMode
            val drawColor = Color.argb(250, 181, 202, 215)
            val deleteColor = Color.argb(250, 255, 100, 150)
            if (scrubbingOverlayView.drawMode) {
                primaryFAB.backgroundTintList = ColorStateList.valueOf(drawColor)
                (primaryFAB as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_edit)
                view.backgroundTintList = ColorStateList.valueOf(deleteColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_delete)
            } else {
                primaryFAB.backgroundTintList = ColorStateList.valueOf(deleteColor)
                (primaryFAB as FloatingActionButton).setImageResource(android.R.drawable.ic_delete)
                view.backgroundTintList = ColorStateList.valueOf(drawColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_edit)
            }
            // hide additional FABs
            secondaryRedactFAB.visibility = View.GONE
            vhItemFAB.visibility = View.GONE
            saveFAB.visibility = View.GONE
            isAllFABsVisible = false
        }
    }

    private fun setUpSaveRedactFloatingActionButton() {
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

    private fun dpToPx(dp: Int): Int {
        val displayMetrics: DisplayMetrics = applicationContext.resources.displayMetrics
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
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
        if ((textField.isNotEmpty() && textField != getString(R.string.redact_text_val)) ||
            (contentDesc != "none" && contentDesc.isNotEmpty() && contentDesc != getString(R.string.redact_desc_val))) {
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
                    child.put("text_field", getString(R.string.redact_text_val))
                }
                child.remove("content-desc")
                child.put("content-desc", getString(R.string.redact_desc_val))
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
        if (node.has("content-desc") && node.get("content-desc").asText() != "none") {
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
                if (child.has("text_field") && child["text_field"].asText() != getString(R.string.redact_text_val)) {
                    child.remove("text_field")
                    child.put("text_field", getString(R.string.redact_text_val))
                }
                if (child["content-desc"].asText() != getString(R.string.redact_desc_val)) {
                    child.remove("content-desc")
                    child.put("content-desc", getString(R.string.redact_desc_val))
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
package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ScreenShotActivity : AppCompatActivity() {

    private var imageView: ScrubbingView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var chosenEventLabel: String? = null
    private var canvas: Canvas? = null
    private lateinit var canvasBitmap: Bitmap
    private var originalBitmap: Bitmap? = null
    private var vhBoxes: MutableList<Rect> = arrayListOf()
    private val mapper = ObjectMapper()


    private fun extractVHBoxes(root: JsonNode, vhBoxes: MutableList<Rect>, mapper: ObjectMapper) {
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
            val child = childrenArr[i]//.asJsonObject
            extractVHBoxes(child, vhBoxes, mapper)
        }
    }
    /**
     * Draw red bounding boxes where VH elements are, based on screenshot view hierarchy
     * input takes boxes as an array of rectangles representing locations of VH elements
     * red box borders are drawn on the canvas.
     */
    private fun drawVHBoxes(boxes: List<Rect>?) {
        if (boxes != null) {
            for (element in boxes) {
                val paint = Paint()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2F
                paint.color = Color.rgb(255, 0, 0)
                canvas!!.drawRect(element, paint)
            }
        }
    }

    /**
     * Remove the red bounding boxes when uploading to AWS. We simply erase the
     * areas drawn by drawing over them with the original bitmap (copied before
     * red boxes were drawn).
     */
    private fun removeVHBoxes(boxes: List<Rect>?, originalBitmap: Bitmap?) {
        if (boxes != null) {
            for (i in boxes.indices) {
                canvas!!.drawBitmap(originalBitmap!!, boxes[i], boxes[i], null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_screenshot)
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        chosenEventLabel = intent.extras!!.getString("event_label")
        title = "ScreenShot (Click Image for VH)"
        imageView = findViewById<View>(R.id.screenshot) as ScrubbingView

        // setup image bitmaps for scrubbing view
        val screenshot: Bitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val vhJsonString = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        // extract view hierarchy boxes from VH
        val vhRootJson = mapper.readTree(vhJsonString.trim())
        extractVHBoxes(vhRootJson, vhBoxes, mapper)
        imageView!!.vhRects = vhBoxes

        canvasBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        canvas = Canvas(canvasBitmap)
        imageView!!.canvas = canvas
        imageView!!.vhs = vhRootJson

        // Save the original bitmap with gesture so we can remove mistake redactions
        originalBitmap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)





        // FIXME: for debug
        val gesture = loadGestures(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val windowHeight = windowManager.currentWindowMetrics.bounds.height().toFloat()
        val windowWidth =  windowManager.currentWindowMetrics.bounds.width().toFloat()
        val centerX = gesture.centerX * windowWidth
        val centerY = gesture.centerY * windowHeight
        val scrollDX = gesture.scrollDX * windowWidth
        val scrollDY = gesture.scrollDY * windowHeight
        var rectLeft = (if(centerX-50 > 0) centerX-50 else 0).toInt()
        var rectTop = (if(centerY-50 >0) centerY-50 else 0).toInt()
        var rectRight = (centerX+50).toInt()
        var rectBottom = (centerY+50).toInt()
        if (scrollDX > 0) {
            rectLeft = (centerX - scrollDX).toInt()
            rectRight = (centerX + scrollDX).toInt()
        }
        if (scrollDY > 0) {
            rectTop = (centerY - scrollDY).toInt()
            rectBottom = (centerY + scrollDY).toInt()
        }
        val rect = Rect(rectLeft, rectTop, rectRight, rectBottom)
        if (scrollDX.toInt() == 0 && scrollDY.toInt() == 0) {
            val paint = Paint()
            paint.color = Color.rgb(255, 165, 0)
            paint.alpha = 100
            canvas!!.drawCircle(
                rect.centerX().toFloat(),
                rect.centerY().toFloat(),
                (((rect.height() + rect.width()) * 0.25).toFloat()),
                paint
            )
        } else {
            val paint = Paint()
            paint.color = Color.rgb(165, 0, 255)
            paint.alpha = 100
            canvas!!.drawOval(
                (rect.centerX() - 50).toFloat(),
                (rect.centerY() - 100).toFloat(),
                (rect.centerX() + 50).toFloat(),
                (rect.centerY() + 100).toFloat(),
                paint
            )
        }







        drawVHBoxes(vhBoxes)
        imageView!!.baseBitMap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)
        // set the full drawings as primary bitmap for scrubbingView
        imageView!!.setImageBitmap(canvasBitmap)

        // Save Listener
        val saveFAB: MovableFloatingActionButton = findViewById(R.id.save_fab)
        saveFAB.setOnClickListener {
            // don't save if no redactions have been drawn
            if (imageView!!.currentRedacts.isEmpty()) {
                return@setOnClickListener
            }
            // loop through imageView.rectangles
            removeVHBoxes(vhBoxes, this.originalBitmap)
            for (drawnRedaction: Redaction in imageView!!.currentRedacts) {
                // traverse each rectangle
                val redactRect = imageView!!.convertRedactToRect(drawnRedaction)
                imageView!!.traverse(imageView!!.vhs, redactRect, mapper)
                saveVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, mapper.writeValueAsString(imageView!!.vhs))
                saveRedactions(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, drawnRedaction)
            }
            // update screenshot bitmap in the map (no need to set in map, just set bitmap property)
            saveScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, canvasBitmap)
            this.originalBitmap?.recycle()
            this.originalBitmap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)

            this.imageView!!.baseBitMap?.recycle()
            this.imageView!!.baseBitMap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)

            drawVHBoxes(vhBoxes)
            // clear imageView.rectangles
            imageView!!.currentRedacts.clear()
            notifyEventAdapter()
        }

        // Delete Listener
        val deleteFAB: FloatingActionButton = findViewById(R.id.draw_delete_fab)
        deleteFAB.setOnClickListener { view ->
            imageView!!.drawMode = !imageView!!.drawMode
            if (imageView!!.drawMode) {
                val drawColor = Color.argb(250, 181, 202, 215)
                view.backgroundTintList = ColorStateList.valueOf(drawColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_edit)
            } else {
                val deleteColor = Color.argb(250, 255, 100, 150)
                view.backgroundTintList = ColorStateList.valueOf(deleteColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_delete)
            }
        }

//        This took an embarrassing amount of time to figure out dimensions of the screen, image and canvas
//        Further calculations are done in the scrubbing view:

//        val size = Point()
//        val display = getDisplay()?.getSize(size)
//        Log.i("display size:", "x: ${size.x}  y: ${size.y}")
//
//        val realSize = Point()
//        val realDisplay = getDisplay()?.getRealSize(realSize)
//        Log.i("real display size:", "x: ${realSize.x} y: ${realSize.y}")
//
//        val statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android")
//        val statusBarHeight = resources.getDimensionPixelSize(statusBarHeightId)
//        Log.i("status bar h:", statusBarHeight.toString())
//
//        val navBarHeightId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
//        val navBarHeight = resources.getDimensionPixelSize(navBarHeightId)
//        Log.i("nav bar h:", navBarHeight.toString())
    }

    override fun onStop() {
        super.onStop()
        removeVHBoxes(vhBoxes, this.originalBitmap)
        canvasBitmap.recycle()
        originalBitmap?.recycle()
        imageView!!.baseBitMap?.recycle()
        super.onBackPressedDispatcher.onBackPressed()
    }

    override fun onRestart() {
        super.onRestart()
        // setup image bitmaps for scrubbing view
        chosenEventLabel = intent.extras!!.getString("event_label")
        val screenshot: Bitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val vhJsonString = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        // extract view hierarchy boxes from VH
        val vhRootJson = mapper.readTree(vhJsonString.trim())
        extractVHBoxes(vhRootJson, vhBoxes, mapper)

        imageView!!.vhRects = vhBoxes

        canvasBitmap = screenshot.copy(Bitmap.Config.ARGB_8888, true)
        canvas = Canvas(canvasBitmap)
        imageView!!.canvas = canvas
        imageView!!.vhs = vhRootJson

        // Save the original bitmap with gesture so we can remove mistake redactions
        originalBitmap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)
        drawVHBoxes(vhBoxes)
        imageView!!.baseBitMap = canvasBitmap.copy(Bitmap.Config.ARGB_8888, true)
        // set the full drawings as primary bitmap for scrubbingView
        imageView!!.setImageBitmap(canvasBitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        canvasBitmap.recycle()
        originalBitmap?.recycle()
        imageView!!.baseBitMap?.recycle()
    }
}
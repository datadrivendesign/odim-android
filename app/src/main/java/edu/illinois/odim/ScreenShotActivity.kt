package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import edu.illinois.odim.MyAccessibilityService.Companion.redactionMap
import org.json.JSONArray

class ScreenShotActivity : AppCompatActivity() {

    private var imageView: ScrubbingView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceName: String? = null
    private var chosenEventName: String? = null
    private var canvas: Canvas? = null
    private var originalBitmap: Bitmap? = null

    /**
     * Draw red bounding boxes where VH elements are, based on screenshot view hierarchy
     * input takes boxes as an array of rectangles representing locations of VH elements
     * red box borders are drawn on the canvas.
     */
    private fun drawVHBoxes(boxes: ArrayList<Rect>?) {
        if (boxes != null) {
            for (i in 0 until boxes.size) {
                val paint = Paint()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2F
                paint.color = Color.rgb(255, 0, 0)
                canvas!!.drawRect(boxes[i], paint)
            }
        }
    }

    /**
     * Remove the red bounding boxes when uploading to AWS. We simply erase the
     * areas drawn by drawing over them with the original bitmap (copied before
     * red boxes were drawn).
     */
    private fun removeVHBoxes(boxes: ArrayList<Rect>?, originalBitmap: Bitmap?) {
        if (boxes != null) {
            for (i in 0 until boxes.size) {
                canvas!!.drawBitmap(originalBitmap!!, boxes[i], boxes[i], null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_screenshot)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceName = intent.extras!!["trace_name"].toString()
        chosenEventName = intent.extras!!["event_name"].toString()
        title = "ScreenShot (Click Image for VH)"
        imageView = findViewById<View>(R.id.screenshot) as ScrubbingView
        val screenshot: ScreenShot =
            getScreenshot(chosenPackageName, chosenTraceName, chosenEventName)

        val jsonArray = JSONArray(getVh(chosenPackageName, chosenTraceName, chosenEventName))
        val jsonString: String = jsonArray[0] as String
        var vhMap: HashMap<String, String> = HashMap()
        vhMap = Gson().fromJson(jsonString.trim(), vhMap.javaClass)

        imageView!!.vhRects = screenshot.vh
        val myBit: Bitmap = screenshot.bitmap!!.copy(Bitmap.Config.ARGB_8888, true)  //tempBit
        canvas = Canvas(myBit)
        imageView!!.canvas = canvas
        imageView!!.vhs = vhMap

        // Save the original bitmap with gesture so we can remove mistake redactions
        this.originalBitmap = myBit.copy(Bitmap.Config.ARGB_8888, true)

        drawVHBoxes(screenshot.vh)

        imageView!!.baseBitMap = myBit.copy(Bitmap.Config.ARGB_8888, true)
        // set the full drawings as primary bitmap for scrubbingView
        imageView!!.setImageBitmap(myBit)
        // Save Listener
        val saveFAB: MovableFloatingActionButton = findViewById(R.id.save_fab)
        val gson = GsonBuilder().create()
        saveFAB.setOnClickListener {
            // don't save if no redactions have been drawn
            if (imageView!!.currentRedacts.isEmpty()) {
                return@setOnClickListener
            }
            // remove red paint strokes from bitmap
            removeVHBoxes(screenshot.vh, this.originalBitmap)
            // loop through imageView.rectangles
            for (drawnRedaction: Redaction in imageView!!.currentRedacts) {
                // traverse each rectangle
                imageView!!.traverse(imageView!!.vhs, drawnRedaction.rect!!)
                imageView!!.postInvalidate()
                setVh(chosenPackageName, chosenTraceName, chosenEventName, gson.toJson(imageView!!.vhs))
                if (redactionMap.containsKey(chosenTraceName)) {
                    if (redactionMap[chosenTraceName]!!.containsKey(chosenEventName)) {
                        redactionMap[chosenTraceName]!![chosenEventName!!]?.add(drawnRedaction)
                    } else {
                        redactionMap[chosenTraceName]!![chosenEventName!!]?.add(drawnRedaction)
                    }

                } else {
                    redactionMap[chosenTraceName!!] = HashMap()
                    redactionMap[chosenTraceName]!![chosenEventName!!]?.add(drawnRedaction)
                }

            }
            // update screenshot bitmap in the map (no need to set in map, just set bitmap property)
            screenshot.bitmap = myBit
            // clear imageView.rectangles
            imageView!!.currentRedacts.clear()
            notifyEventAdapter()
        }

        // Delete Listener
        val deletefab: FloatingActionButton = findViewById(R.id.draw_delete_fab)
        deletefab.setOnClickListener { view ->
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
//        Further calculations are done in the scrubbing view

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
}
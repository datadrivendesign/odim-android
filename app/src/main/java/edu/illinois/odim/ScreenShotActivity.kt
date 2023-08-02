package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.json.JSONArray
import kotlin.math.min
import kotlin.math.pow

class ScreenShotActivity : AppCompatActivity() {

    private var imageView: ScrubbingView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceName: String? = null
    private var chosenEventName: String? = null
    private var canvas: Canvas? = null
    private var originalBitmap: Bitmap? = null

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
        val rect: Rect? = screenshot.rect
        // TODO: find a way to fix gestures
//        Log.i("gesture rect:", rect.toString())
        if (screenshot.actionType == ScreenShot.TYPE_CLICK) {
            val paint = Paint()
            paint.color = Color.rgb(255, 165, 0)
            paint.alpha = 100
            if (rect != null) {
                canvas!!.drawCircle(
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                    (min(rect.height(), rect.width()) * 0.5).toFloat(), //(((rect.height() + rect.width()) * 0.25).toFloat()),
                    paint
                )
            }
        } else if (screenshot.actionType == ScreenShot.TYPE_SCROLL) {
            val paint = Paint()
            paint.color = Color.rgb(255, 165, 0)
            paint.alpha = 100
            val scrollCoords = screenshot.scrollCoords
            if (rect != null) {
                if (scrollCoords != null) {
                    val deltaXIncr = scrollCoords.first / 4
                    val deltaYIncr = scrollCoords.second / 4
                    val centerX = rect.centerX() + 0.5*deltaXIncr
                    val centerY = rect.centerY() + 0.5*deltaYIncr
                    val multiplier = 0.8
                    val radius = 100
                    for (i in 0..4) {
                        canvas!!.drawCircle(
                            (centerX - i*deltaXIncr).toFloat(),
                            (centerY - i*deltaYIncr).toFloat(),
                            ((radius * multiplier.pow(i)).toFloat()),
                            paint
                        )
                    }
                } else {
                    canvas!!.drawOval(
                        (rect.centerX() - 50).toFloat(),
                        (rect.centerY() - 100).toFloat(),
                        (rect.centerX() + 50).toFloat(),
                        (rect.centerY() + 100).toFloat(),
                        paint
                    )
                }

            }
        } else if (screenshot.actionType == ScreenShot.TYPE_LONG_CLICK) {
            val paint = Paint()
            paint.color = Color.rgb(0, 165, 255)
            paint.alpha = 100
            if (rect != null) {
                canvas!!.drawCircle(
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                    (min(rect.height(), rect.width()) * 0.5).toFloat(), // ((rect.height() + rect.width()) * 0.25).toFloat(),
                    paint
                )
            }
        } else if (screenshot.actionType == ScreenShot.TYPE_SELECT) {
            val paint = Paint()
            paint.color = Color.rgb(165, 0, 255)
            paint.alpha = 100
            if (rect != null) {
                canvas!!.drawCircle(
                    rect.centerX().toFloat(),
                    rect.centerY().toFloat(),
                    (min(rect.height(), rect.width()) * 0.5).toFloat(), //((rect.height() + rect.width()) * 0.25).toFloat(),
                    paint
                )
            }
        }
        this.originalBitmap = myBit.copy(Bitmap.Config.ARGB_8888, true)

        val boxes: ArrayList<Rect>? = screenshot.vh
        if (boxes != null) {
            for (i in 0 until boxes.size) {
                val paint = Paint()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2F
                paint.color = Color.rgb(255, 0, 0)
                canvas!!.drawRect(boxes[i], paint)
            }
        }
        imageView!!.baseBitMap = myBit.copy(Bitmap.Config.ARGB_8888, true)
        // set the full drawings as primary bitmap for scrubbingView
        imageView!!.setImageBitmap(myBit)
        // Save Listener
        val saveFAB: MovableFloatingActionButton = findViewById(R.id.fab)
        val gson = GsonBuilder().create()
        saveFAB.setOnClickListener { fabView ->
            // remove red paint strokes from bitmap
            if (boxes != null) {
                for (i in 0 until boxes.size) {
                    canvas!!.drawBitmap(this.originalBitmap!!, boxes[i], boxes[i], null)
                }
            }
            // loop through imageView.rectangles
            for (drawnRect: Rect in imageView!!.rectangles) {
                // traverse each rectangle
                imageView!!.traverse(imageView!!.vhs, drawnRect)
                setVh(chosenPackageName, chosenTraceName, chosenEventName, gson.toJson(imageView!!.vhs))
                if (redactionMap.containsKey(chosenTraceName)) {
                    if (redactionMap[chosenTraceName]!!.containsKey(chosenEventName)) {
                        redactionMap[chosenTraceName]!![chosenEventName!!] += ";${drawnRect.toShortString()}"
                    } else {
                        redactionMap[chosenTraceName]!![chosenEventName!!] = drawnRect.toShortString()
                    }

                } else {
                    redactionMap[chosenTraceName!!] = HashMap()
                    redactionMap[chosenTraceName]!![chosenEventName!!] = drawnRect.toShortString()
                }

            }
            // update screenshot bitmap in the map (no need to set in map, just set bitmap property)
            screenshot.bitmap = myBit
            // clear imageView.rectangles
            imageView!!.rectangles.clear()

            val uploadSuccess = uploadFile(
                chosenPackageName!!,
                chosenTraceName!!,
                chosenEventName!!,
                getVh(chosenPackageName, chosenTraceName, chosenEventName)[0],
                getScreenshot(chosenPackageName, chosenTraceName, chosenEventName).bitmap
            )
            if (!uploadSuccess) {
                val errSnackbar = Snackbar.make(fabView, R.string.upload_fail, Snackbar.LENGTH_LONG)
                errSnackbar.view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                errSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    .setTextColor(ContextCompat.getColor(this, R.color.white))
                errSnackbar.show()
            } else {
                val successSnackbar = Snackbar.make(fabView, R.string.upload_all_toast_success, Snackbar.LENGTH_SHORT)
                successSnackbar.view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                successSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    .setTextColor(ContextCompat.getColor(this, R.color.white))
                successSnackbar.show()
            }
        }

        // Delete Listener
        val deletefab: FloatingActionButton = findViewById(R.id.fabdelete)
        deletefab.setOnClickListener { view ->
            val deleteColor = Color.argb(250, 255, 100, 150)
            val drawColor = Color.argb(250, 181, 202, 215)
            imageView!!.drawMode = !imageView!!.drawMode
            if (imageView!!.drawMode) {
                view.backgroundTintList = ColorStateList.valueOf(drawColor)
                (view as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_edit)
            } else {
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
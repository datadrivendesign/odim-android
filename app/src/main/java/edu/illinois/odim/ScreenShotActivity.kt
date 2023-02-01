package edu.illinois.odim

import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONArray

class ScreenShotActivity : AppCompatActivity() {

    private var imageView: ScrubbingView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceName: String? = null
    private var chosenEventName: String? = null
    private var canvas: Canvas? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide();
        setContentView(R.layout.activity_screenshot)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceName = intent.extras!!["trace_name"].toString()
        chosenEventName = intent.extras!!["event_name"].toString()
        title = "ScreenShot (Click Image for VH)"
        imageView = findViewById<View>(R.id.screenshot) as ScrubbingView
        val screenshot: ScreenShot? =
            getScreenshot(chosenPackageName, chosenTraceName, chosenEventName)

        val jsonArray = JSONArray(getVh(chosenPackageName, chosenTraceName, chosenEventName))
        val jsonString: String = jsonArray[0] as String
        var vhMap: Map<String, String> = HashMap()
        vhMap = Gson().fromJson(jsonString.trim(), vhMap.javaClass)

        if (screenshot != null) {
            imageView!!.vhRects = screenshot.vh
//            imageView!!.vhs = chosenVH
            val tempBit: Bitmap = screenshot.bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            val myBit: Bitmap = tempBit
            canvas = Canvas(myBit)
            imageView!!.canvas = canvas
//            imageView!!.originalBitMap = screenshot.bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            imageView!!.vhs = vhMap
            val rect: Rect? = screenshot.rect
            if (screenshot.actionType == ScreenShot.TYPE_CLICK) {
                val paint = Paint()
                paint.color = Color.rgb(255, 165, 0)
                paint.alpha = 100
                if (rect != null) {
                    canvas!!.drawCircle(
                        rect.centerX().toFloat(),
                        rect.centerY().toFloat(),
                        (((rect.height() + rect.width()) * 0.25).toFloat()),
                        paint
                    )
                }
            } else if (screenshot.actionType == ScreenShot.TYPE_SCROLL) {
                val paint = Paint()
                paint.color = Color.rgb(255, 165, 0)
                paint.alpha = 100
                if (rect != null) {
                    canvas!!.drawOval(
                        (rect.centerX() - 50).toFloat(),
                        (rect.centerY() - 100).toFloat(),
                        (rect.centerX() + 50).toFloat(),
                        (rect.centerY() + 100).toFloat(),
                        paint
                    )
                }
            } else if (screenshot.actionType == ScreenShot.TYPE_LONG_CLICK) {
                val paint = Paint()
                paint.color = Color.rgb(0, 165, 255)
                paint.alpha = 100
                if (rect != null) {
                    canvas!!.drawCircle(
                        rect.centerX().toFloat(),
                        rect.centerY().toFloat(),
                        ((rect.height() + rect.width()) * 0.25).toFloat(),
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
                        ((rect.height() + rect.width()) * 0.25).toFloat(),
                        paint
                    )
                }
            }

            val boxes: ArrayList<Rect>? = screenshot.vh
            if (boxes != null) {
                for (i in 0 until boxes!!.size) {
                    val paint = Paint()
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(255, 0, 0)
                    canvas!!.drawRect(boxes.get(i), paint)
                }
            }
            imageView!!.originalBitMap = myBit.copy(Bitmap.Config.ARGB_8888, true)

            imageView!!.setImageBitmap(myBit)

            // Save Listener
            val savefab: FloatingActionButton = findViewById(R.id.fab)
            savefab.setOnClickListener { view ->
                screenshot.bitmap = tempBit
                CoroutineScope(Dispatchers.Main + Job()).launch() {
                    withContext(Dispatchers.IO) {
                        uploadFile(
                            chosenPackageName!!,
                            chosenTraceName!!,
                            chosenEventName!!,
                            getVh(chosenPackageName, chosenTraceName, chosenEventName)[0],
                            getScreenshot(
                                chosenPackageName,
                                chosenTraceName,
                                chosenEventName
                            ).bitmap,
                            null,
                            applicationContext
                        )
                    }
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
        }

    }

}
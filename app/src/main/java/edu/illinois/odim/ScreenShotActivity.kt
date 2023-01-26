package edu.illinois.odim

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

            imageView!!.setImageBitmap(myBit)
            screenshot.bitmap = tempBit

//            imageView!!.setOnClickListener {
//                val tempPaint = Paint()
//                canvas!!.drawRect(Rect(0, 66, 154, 220), tempPaint)
////                val intent = Intent(applicationContext, ViewHierarchyActivity::class.java)
////                intent.putExtra("package_name", chosenPackageName)
////                intent.putExtra("trace_name", chosenTraceName)
////                intent.putExtra("event_name", chosenEventName)
////                startActivity(intent)
//                Log.i("OnClick", "Clicked")
//            }

        }

    }

}
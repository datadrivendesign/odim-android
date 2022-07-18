package edu.illinois.odim

import android.graphics.*
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class ScreenShotActivity : AppCompatActivity() {

    private var imageView: ImageView? = null
    private var package_name: String? = null
    private var trace_name: String? = null
    private var event_name: String? = null
    private var canvas: Canvas? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot)
        package_name = intent.extras!!["package_name"].toString()
        trace_name = intent.extras!!["trace_name"].toString()
        event_name = intent.extras!!["event_name"].toString()
        title = "ScreenShot (Click Image for VH)"
        imageView = findViewById<View>(R.id.screenshot) as ImageView
        val screenshot: ScreenShot? =
            getScreenshot(package_name, trace_name, event_name)
        if (screenshot != null) {
            val myBit: Bitmap = screenshot.bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            canvas = Canvas(myBit)
            val rect: Rect? = screenshot.rect
            if (screenshot.actionType == ScreenShot.TYPE_CLICK) {
                val paint = Paint()
                paint.setColor(Color.rgb(255, 165, 0))
                paint.setAlpha(100)
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
                paint.setColor(Color.rgb(255, 165, 0))
                paint.setAlpha(100)
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
                paint.setColor(Color.rgb(0, 165, 255))
                paint.setAlpha(100)
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
                paint.setColor(Color.rgb(165, 0, 255))
                paint.setAlpha(100)
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
            Log.i("Box size", java.lang.String.valueOf(boxes!!.size))
            for (i in 0 until boxes.size) {
                val paint = Paint()
                paint.setStyle(Paint.Style.STROKE)
                paint.setColor(Color.rgb(255, 0, 0))
                canvas!!.drawRect(boxes[i], paint)
            }
            imageView!!.setImageBitmap(myBit)

            imageView!!.setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View?) {
                    val intent = Intent(applicationContext, ViewHierarchyActivity::class.java)
                    intent.putExtra("package_name", package_name)
                    intent.putExtra("trace_name", trace_name)
                    intent.putExtra("event_name", event_name)
                    startActivity(intent)
                }
            })
        }
    }
}
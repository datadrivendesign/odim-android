package edu.illinois.odim

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode

class IncompleteScreenActivity: AppCompatActivity() {
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var chosenEventLabel: String? = null
    private lateinit var saveScreenButton: Button
    private var screenBitmap: Bitmap? = null
    private val mapper = ObjectMapper()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_incomplete_screen)
        // get intent labels
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        chosenEventLabel = intent.extras!!.getString("event_label")
        // load screen
        screenBitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
//        screenBitmap = incompleteScreen.copy(Bitmap.Config.ARGB_8888, true)
        val incompleteVH = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val vhRootJson = mapper.readTree(incompleteVH.trim())
        val incompleteGesture = loadGesture(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val className = incompleteGesture.className!!
        // set up imageView
        val incompleteImageView: ImageView = findViewById(R.id.incomplete_image)
        incompleteImageView.setImageBitmap(screenBitmap)
        // set up transparent overlay
        val incompleteOverlayView: IncompleteScreenCanvasOverlay = findViewById(R.id.incomplete_overlay)
        val candidateElements: MutableList<Rect> = arrayListOf()
        retrieveVHElems(className, vhRootJson, candidateElements)
        incompleteOverlayView.setCandidateElements(candidateElements)
        // set intrinsic height and width once imageView is set up
        incompleteImageView.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val intrinsicWidth = incompleteImageView.drawable.intrinsicWidth
                val intrinsicHeight = incompleteImageView.drawable.intrinsicHeight
                val measuredHeight = incompleteImageView.measuredHeight
                incompleteOverlayView.setIntrinsicDimensions(intrinsicWidth, intrinsicHeight, measuredHeight)
                incompleteImageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        // connect properties to UI layout
        saveScreenButton = findViewById(R.id.confirm_gesture_button)
        saveScreenButton.isEnabled = (incompleteOverlayView.currVHCandidate != null)
        saveScreenButton.setOnClickListener { buttonView ->
            // TODO: save gesture + navigate to ScreenShotActivity
        }
    }

    private fun retrieveVHElems(className: String, vhRoot: JsonNode, candidateElems: MutableList<Rect>) {
        if (vhRoot.get("class_name").asText() == className) {
            val vhBoxString = vhRoot.get("bounds_in_screen").asText()
            val vhBoxRect = Rect.unflattenFromString(vhBoxString)
            if (vhBoxRect != null){
                candidateElems.add(vhBoxRect)
            }
        }
        // Base Case
        val children = vhRoot.get("children") ?: return
        val childrenArr = children as ArrayNode
        if (childrenArr.isEmpty) {
            return
        }
        // Recursive Case
        for (i in 0 until childrenArr.size()) {
            val child = childrenArr[i]//.asJsonObject
            retrieveVHElems(className, child, candidateElems)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenBitmap?.recycle()
    }

    override fun onRestart() {
        super.onRestart()
    }
}
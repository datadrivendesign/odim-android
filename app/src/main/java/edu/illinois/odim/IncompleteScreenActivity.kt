package edu.illinois.odim

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
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
        incompleteOverlayView.setIncompleteScreenSaveButton(saveScreenButton)
        saveScreenButton.setOnClickListener { _ ->
            val confirmGestureInput = View.inflate(this, R.layout.confirm_gesture_dialog, null)
            var scrollDx = 0F
            var scrollDy = 0F
            // show scroll radio button option only if event is a scroll
            if (chosenEventLabel!!.contains(getString(R.string.type_view_scroll))) {
                val scrollRadioGroup: RadioGroup = confirmGestureInput.findViewById(R.id.scroll_radio_group)
                scrollRadioGroup.visibility = View.VISIBLE
                scrollRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                    when(checkedId) {
                        R.id.scroll_radio_button_vertical -> {
                            scrollDx = 0F
                            scrollDy = 80F
                        }
                        R.id.scroll_radio_button_horizontal -> {
                            scrollDx = 80F
                            scrollDy = 0F
                        }
                    }
                }
            }
            // create alert dialog
            AlertDialog.Builder(this)
                .setTitle("Confirm Gesture")
                .setView(confirmGestureInput)
                .setPositiveButton("SAVE") { _, _ ->
                    // gesture needs to be in percentage form
                    val newGesture = Gesture(
                        incompleteOverlayView.currVHCandidate!!.exactCenterX() / incompleteImageView.drawable.intrinsicWidth,
                        incompleteOverlayView.currVHCandidate!!.exactCenterY() / incompleteImageView.drawable.intrinsicHeight,
                        scrollDx / incompleteImageView.drawable.intrinsicWidth,
                        scrollDy / incompleteImageView.drawable.intrinsicHeight
                    )
                    val result = saveGesture(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newGesture)
                    // transfer directly to ScreenShotActivity
                    val intent = Intent(
                        applicationContext,
                        ScreenShotActivity::class.java
                    )
                    intent.putExtra("package_name", chosenPackageName)
                    intent.putExtra("trace_label", chosenTraceLabel)
                    intent.putExtra("event_label", chosenEventLabel)
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("CANCEL") { dialogInterface, _ ->
                    dialogInterface.cancel()
                }
                .show()
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
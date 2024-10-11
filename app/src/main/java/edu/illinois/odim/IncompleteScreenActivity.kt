package edu.illinois.odim

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import edu.illinois.odim.LocalStorageOps.loadGesture
import edu.illinois.odim.LocalStorageOps.loadScreenshot
import edu.illinois.odim.LocalStorageOps.loadVH
import edu.illinois.odim.LocalStorageOps.renameEvent
import edu.illinois.odim.LocalStorageOps.renameGesture
import edu.illinois.odim.LocalStorageOps.renameScreenshot
import edu.illinois.odim.LocalStorageOps.renameVH
import edu.illinois.odim.LocalStorageOps.saveGesture
import java.io.FileNotFoundException

class IncompleteScreenActivity: AppCompatActivity() {
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var chosenEventLabel: String? = null
    private lateinit var saveScreenButton: MovableFloatingActionButton
    private var screenBitmap: Bitmap? = null
    private val mapper = ObjectMapper()
    private var isEditGesture: Boolean = false
    private enum class CandidateId {
        CLASS,
        VIEW,
        NONE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_incomplete_screen)
        // get intent labels
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        chosenEventLabel = intent.extras!!.getString("event_label")
        isEditGesture = intent.extras!!.getBoolean("edit_gesture")
        // load screen
        screenBitmap = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val incompleteVH = loadVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
        val vhRootJson = mapper.readTree(incompleteVH.trim())
        val incompleteGesture: Gesture? = try {
            if (isEditGesture) {
                null
            } else {
                loadGesture(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!)
            }
        } catch (e: FileNotFoundException) {
            null
        }
        // set up imageView
        val incompleteImageView: ImageView = findViewById(R.id.incomplete_image)
        incompleteImageView.setImageBitmap(screenBitmap)
        // set up transparent overlay
        val incompleteOverlayView: IncompleteScreenCanvasOverlay = findViewById(R.id.incomplete_overlay)
        val candidateElements: MutableList<GestureCandidate> = arrayListOf()
        // check if should use viewId or className as search key
        lateinit var searchVal: String
        var searchIdType = CandidateId.CLASS
        if (incompleteGesture != null) {
            if (incompleteGesture.className != null) {
                searchVal = incompleteGesture.className!!
            } else {
                searchVal = incompleteGesture.viewId!!
                searchIdType = CandidateId.VIEW
            }
        } else {
            searchVal = ""
            searchIdType = CandidateId.NONE
        }
        retrieveVHElems(searchIdType, searchVal, vhRootJson, candidateElements)
        incompleteOverlayView.setCandidateElements(candidateElements)
        // set intrinsic height and width for scaling coordinate calculations once imageView is fully rendered
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
        setUpSaveGestureFloatingActionButton(incompleteOverlayView, incompleteImageView)
    }

    private fun setUpSaveGestureFloatingActionButton(
        incompleteOverlayView: IncompleteScreenCanvasOverlay,
        incompleteImageView: ImageView
    ) {
        saveScreenButton = findViewById(R.id.save_gesture_fab)
        incompleteOverlayView.setIncompleteScreenSaveButton(saveScreenButton)
        saveScreenButton.setOnClickListener { _ ->
            val confirmGestureView = View.inflate(this, R.layout.dialog_confirm_gesture, null)
            var updateGesture: Triple<Float, Float, Boolean> = Triple(0F, 0F, false)
            if (chosenEventLabel!!.contains(getString(R.string.type_unknown)) || isEditGesture) {
                updateGesture = setUpDefineGestureTypeRadioGroup(confirmGestureView)
            } else if (chosenEventLabel!!.contains(getString(R.string.type_view_scroll))) {
                updateGesture = setUpScrollGestureRadioGroup(confirmGestureView)
            } else if (chosenEventLabel!!.contains(getString(R.string.type_view_long_click))) {
                updateGesture = Triple(0F, 0F, true)  // set long click flag true
            }
            createSaveUserGestureAlertDialog(confirmGestureView, incompleteImageView, incompleteOverlayView, updateGesture)
        }
    }

    private fun setUpDefineGestureTypeRadioGroup(confirmGestureView: View): Triple<Float, Float, Boolean> {
        var scrollDx = 0F
        var scrollDy = 0F
        var isLongClick = false
        // make define gesture radio group visible if type is unknown
        val defineRadioGroup: RadioGroup = confirmGestureView.findViewById(R.id.define_gesture_type_group)
        defineRadioGroup.visibility = View.VISIBLE
        defineRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId) {
                R.id.define_click_type, R.id.define_long_click_type -> {
                    val scrollRadioGroup: RadioGroup = confirmGestureView.findViewById(R.id.scroll_radio_group)
                    scrollRadioGroup.visibility = View.GONE
                    scrollDx = 0F
                    scrollDy = 0F
                    if (checkedId == R.id.define_long_click_type) {
                        isLongClick = true
                    }
                }
                R.id.define_scroll_type -> {
                    val (dX, dY) = setUpScrollGestureRadioGroup(confirmGestureView)
                    scrollDx = dX
                    scrollDy = dY
                }
            }
        }
        return Triple(scrollDx, scrollDy, isLongClick)
    }
    
    private fun setUpScrollGestureRadioGroup(confirmGestureView: View): Triple<Float, Float, Boolean> {
        var scrollDx = 0F
        var scrollDy = 0F
        // show scroll radio group option if event is a scroll
        val scrollRadioGroup: RadioGroup = confirmGestureView.findViewById(R.id.scroll_radio_group)
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
        return Triple(scrollDx, scrollDy, false)
    }

    private fun renameEventFromNewGesture(newEventName: String): Boolean {
        return renameGesture(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newEventName) &&
        renameScreenshot(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newEventName) &&
        renameVH(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newEventName) &&
        renameEvent(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newEventName)
    }
    
    private fun createSaveUserGestureAlertDialog(
        confirmGestureView: View,
        incompleteImageView: ImageView,
        incompleteOverlayView: IncompleteScreenCanvasOverlay,
        updateGesture: Triple<Float, Float, Boolean>
    ) {
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_save_gesture_title))
            .setView(confirmGestureView)
            .setPositiveButton(getString(R.string.dialog_save_gesture_positive)) { _, _ ->
                // gesture needs to be in percentage form
                val screenWidth = incompleteImageView.drawable.intrinsicWidth
                val screenHeight = incompleteImageView.drawable.intrinsicHeight
                val vhCandidateRect = incompleteOverlayView.currVHCandidate!!.rect
                val scrollDx = updateGesture.first
                val scrollDy = updateGesture.second
                val isLongClick = updateGesture.third
                val newGesture = Gesture(
                    vhCandidateRect.exactCenterX() / screenWidth,
                    vhCandidateRect.exactCenterY() / screenHeight,
                    scrollDx / screenWidth,
                    scrollDy / screenHeight,
                    incompleteOverlayView.currVHCandidate!!.viewId
                )
                newGesture.verified = true
                saveGesture(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel!!, newGesture)
                var eventNameDest: String = chosenEventLabel!!
                if (chosenEventLabel!!.contains(getString(R.string.type_unknown)) || isEditGesture) {
                    // get gesture to replace
                    var replaceGesture = R.string.type_view_scroll
                    if (scrollDx == 0F && scrollDy == 0F) {
                        replaceGesture = if (isLongClick) {
                            R.string.type_view_long_click
                        } else {
                            R.string.type_view_click
                        }
                    }
                    // replace the gesture name from unknown to click or scroll
                    val oldEventType = chosenEventLabel!!.substringAfter(DELIM)
                    eventNameDest = chosenEventLabel!!.replace(
                        oldEventType,
                        getString(replaceGesture),
                        ignoreCase=false
                    )
                    renameEventFromNewGesture(eventNameDest)
                }
                // transfer directly to ScreenShotActivity
                val intent = Intent(
                    applicationContext,
                    ScreenShotActivity::class.java
                )
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_label", chosenTraceLabel)
                intent.putExtra("event_label", eventNameDest)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_close)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
        val saveDialog = builder.create()
        saveDialog.show()
    }

    private fun retrieveVHElems(
        idType: CandidateId,
        searchVal: String,
        vhRoot: JsonNode,
        candidates: MutableList<GestureCandidate>
    ) {
        var rootSearchVal: String = vhRoot.get("class_name").asText()
        if (idType == CandidateId.VIEW) {  // use view id instead
            rootSearchVal = vhRoot.get("id").asText()
        }
        if (idType == CandidateId.NONE || rootSearchVal == searchVal) {
            val vhBoxString = vhRoot.get("bounds_in_screen").asText()
            val viewId = vhRoot.get("id").asText()
            val vhBoxRect = Rect.unflattenFromString(vhBoxString)
            if (vhBoxRect != null){
                candidates.add(GestureCandidate(vhBoxRect, viewId))
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
            val child = childrenArr[i]
            retrieveVHElems(idType, searchVal, child, candidates)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenBitmap?.recycle()
    }
}
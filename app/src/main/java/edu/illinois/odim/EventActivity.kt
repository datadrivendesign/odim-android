package edu.illinois.odim

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import edu.illinois.odim.LocalStorageOps.deleteEvent
import edu.illinois.odim.LocalStorageOps.listEvents
import edu.illinois.odim.LocalStorageOps.loadGesture
import edu.illinois.odim.LocalStorageOps.loadScreenshot
import edu.illinois.odim.LocalStorageOps.loadVH
import edu.illinois.odim.LocalStorageOps.saveGesture
import edu.illinois.odim.UploadDataOps.uploadFullTraceContent
import edu.illinois.odim.databinding.CardCellBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

// these were static in java
private var recyclerAdapter: EventAdapter? = null
fun notifyEventAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class EventActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceLabel: String? = null
    private var uploadTraceButton: Button? = null
    private val screenPreview: ArrayList<ScreenShotPreview> = ArrayList()
    private var isTraceComplete: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)
        chosenPackageName = intent.extras!!.getString("package_name")
        chosenTraceLabel = intent.extras!!.getString("trace_label")
        title = "$chosenPackageName: $chosenTraceLabel"
        // change the way we do this with recyclerview
        recyclerView = findViewById(R.id.event_recycler_view)
        // use gridview instead of linear
        recyclerView?.layoutManager = GridLayoutManager(this, 2, RecyclerView.VERTICAL, false)
        // want both screenshot and event information for trace
        val eventsInTrace : List<String> = listEvents(chosenPackageName!!, chosenTraceLabel!!)
        populateScreensFromEvents(eventsInTrace)
        recyclerAdapter = EventAdapter(screenPreview)
        recyclerAdapter!!.setOnItemLongClickListener(object: EventAdapter.OnItemLongClickListener {
            override fun onItemLongClick(cardView: CardCellBinding): Boolean {
                if (!recyclerAdapter!!.isMultiSelectMode) {  // enable multi-select by long click
                    recyclerAdapter!!.isMultiSelectMode = true
                }
                if (recyclerAdapter!!.isMultiSelectMode) {
                    val ind = cardView.index.text.toString().toInt()
                    screenPreview[ind].isSelected = !screenPreview[ind].isSelected
                    cardView.root.setBackgroundColor(if (screenPreview[ind].isSelected) Color.LTGRAY else Color.TRANSPARENT)
                    recyclerAdapter!!.notifyItemChanged(ind)
                }
                return true// createDeleteScreenAlertDialog(cardView)
            }
        })
        recyclerAdapter!!.setOnItemClickListener(object : EventAdapter.OnItemClickListener {
            override fun onItemClick(cardView: CardCellBinding) {
                if (recyclerAdapter!!.isMultiSelectMode) {
                    val ind = cardView.index.text.toString().toInt()
                    screenPreview[ind].isSelected = !screenPreview[ind].isSelected
                    cardView.root.setBackgroundColor(if (screenPreview[ind].isSelected) Color.LTGRAY else Color.TRANSPARENT)
                    recyclerAdapter!!.notifyItemChanged(ind)
                } else {
                    navigateToNextActivity(cardView)
                }
            }
        })
        recyclerView?.adapter = recyclerAdapter
        // instantiate upload button
        uploadTraceButton = findViewById(R.id.upload_trace_button)
        uploadTraceButton?.setOnClickListener { buttonView ->
            createUploadTraceAlertDialog(buttonView)
        }
        uploadTraceButton?.isEnabled = isTraceComplete
    }

    override fun onStop() {
        super.onStop()
        for (screen in screenPreview) {
            screen.screenShot.recycle()
        }
        screenPreview.clear()
        notifyEventAdapter()
    }

    override fun onRestart() {
        super.onRestart()
        isTraceComplete = true
        screenPreview.clear()
        val eventsInTrace : List<String> = listEvents(chosenPackageName!!, chosenTraceLabel!!)
        populateScreensFromEvents(eventsInTrace)
        uploadTraceButton?.isEnabled = isTraceComplete
        // redraw views
        recyclerView?.adapter = recyclerAdapter
        notifyEventAdapter()
    }

    private fun populateScreensFromEvents(eventsInTrace: List<String>) {
        val mapper = ObjectMapper()
        for (event in eventsInTrace) {
            val screenshot = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, event)
            val mutableScreenshot = screenshot.copy(Bitmap.Config.ARGB_8888, true)
            screenshot.recycle()
            val eventDelimiter = "; "
            val eventInfo = event.split(eventDelimiter)
            val eventTime = eventInfo[0]
            val eventType = eventInfo[1]
            try {  // check if source was null and gesture was not found
                var eventGesture = loadGesture(chosenPackageName!!, chosenTraceLabel!!, event)
                var isComplete = (eventGesture.className == null) // we do not capture classname if source isn't null
                if (!isComplete) {
                    isTraceComplete = false
                } else {
                    if (eventGesture.viewId != null && !eventGesture.verified) {
                        val matches = verifyGestureCoords(event, mapper, eventGesture)
                        if (matches.size > 1) { // use IncompleteScreenActivity to find correct gesture
                            isComplete = false
                        } else {
                            if (matches.size == 1) {
                                val gestureCandidate = matches[0]
                                // check if gestures match, if not then overwrite
                                val candidateRect = gestureCandidate.rect
                                if (candidateRect.centerX().toFloat() != eventGesture.centerX ||
                                    candidateRect.centerY().toFloat() != eventGesture.centerY) {
                                    eventGesture = replaceGestureWithCandidate(event, gestureCandidate, eventGesture)
                                }
                            }
                        }
                    }
                }
                if (isComplete) {
                    addDrawnGesture(eventType, eventGesture, mutableScreenshot)
                }
                val screenshotPreview = ScreenShotPreview(mutableScreenshot, eventType, eventTime, isComplete)
                screenPreview.add(screenshotPreview)
            } catch (e: FileNotFoundException) {
                val screenshotPreview = ScreenShotPreview(mutableScreenshot, eventType, eventTime, false)
                screenPreview.add(screenshotPreview)
            }
        }
    }

    private fun verifyGestureCoords(event:String, mapper: ObjectMapper, gesture: Gesture): List<GestureCandidate> {
        val vhString = loadVH(chosenPackageName!!, chosenTraceLabel!!, event)
        val vhRoot: JsonNode = mapper.readTree(vhString)
        val elemMatches: MutableList<GestureCandidate> = mutableListOf()
        findVHElemsById(gesture.viewId!!, vhRoot, elemMatches)
        return elemMatches
    }

    private fun findVHElemsById(viewId: String, vhRoot: JsonNode, elemMatches: MutableList<GestureCandidate>) {
        if (vhRoot.get("id").asText() == viewId) {
            val vhBoxString = vhRoot.get("bounds_in_screen").asText()
            val vhBoxRect = Rect.unflattenFromString(vhBoxString)
            if (vhBoxRect != null){
                elemMatches.add(GestureCandidate(vhBoxRect, viewId))
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
            findVHElemsById(viewId, child, elemMatches)
        }
    }

    private fun replaceGestureWithCandidate(event: String, candidate: GestureCandidate, gesture: Gesture): Gesture {
        var newGesture = gesture
        // if coordinates are negative, ignore because we can't render that
        if (candidate.rect.centerX() > 0 && candidate.rect.centerY() > 0) {
            val windowWidth =  windowManager.currentWindowMetrics.bounds.width().toFloat()
            val windowHeight = windowManager.currentWindowMetrics.bounds.height().toFloat()
            newGesture = Gesture(
                candidate.rect.centerX().toFloat() / windowWidth,
                candidate.rect.centerY().toFloat() / windowHeight,
                gesture.scrollDX / windowWidth,
                gesture.scrollDY / windowHeight,
                gesture.viewId
            )
        }
        newGesture.verified = true
        saveGesture(chosenPackageName!!, chosenTraceLabel!!, event, newGesture)
        return newGesture
    }

    private fun addDrawnGesture(eventType: String, gesture: Gesture, bitmap: Bitmap) {
        // calculate gesture dimensions
        val gestureOffsetSize = 50
        val windowWidth =  windowManager.currentWindowMetrics.bounds.width().toFloat()
        val windowHeight = windowManager.currentWindowMetrics.bounds.height().toFloat()
        val centerX = gesture.centerX * windowWidth
        val centerY = gesture.centerY * windowHeight
        val scrollDXPixel = gesture.scrollDX * windowWidth
        val scrollDYPixel = gesture.scrollDY * windowHeight
        var rectLeft = (if(centerX-gestureOffsetSize > 0) centerX-gestureOffsetSize else 0).toInt()
        var rectTop = (if(centerY-gestureOffsetSize > 0) centerY-gestureOffsetSize else 0).toInt()
        var rectRight = (if(centerX+gestureOffsetSize < windowWidth) centerX+gestureOffsetSize else windowWidth).toInt()
        var rectBottom = (if(centerY+gestureOffsetSize < windowHeight) centerY+gestureOffsetSize else windowHeight).toInt()
        if (scrollDXPixel > 0) {
            rectLeft = (centerX - scrollDXPixel).toInt()
            rectRight = (centerX + scrollDXPixel).toInt()
        }
        if (scrollDYPixel > 0) {
            rectTop = (centerY - scrollDYPixel).toInt()
            rectBottom = (centerY + scrollDYPixel).toInt()
        }
        // set up canvas and paint
        val canvas = Canvas(bitmap)
        val clickPaint = Paint().apply {
            color = Color.rgb(255, 165, 0)
            alpha = 100
        }
        val scrollPaint = Paint().apply {
            color = Color.rgb(165, 0, 255)
            alpha = 100
        }
        // start drawing gestures
        val rect = Rect(rectLeft, rectTop, rectRight, rectBottom)

        if (eventType == "TYPE_VIEW_SCROLLED") {
            val scrollGestureOffsetSize = 40
            canvas.drawOval(
                (rect.centerX() - scrollDXPixel - scrollGestureOffsetSize),
                (rect.centerY() - scrollDYPixel - scrollGestureOffsetSize),
                (rect.centerX() + scrollDXPixel + scrollGestureOffsetSize),
                (rect.centerY() + scrollDYPixel + scrollGestureOffsetSize),
                scrollPaint
            )
        } else {
            val radiusFactor = 0.25
            canvas.drawCircle(
                rect.centerX().toFloat(),
                rect.centerY().toFloat(),
                (((rect.height() + rect.width()) * radiusFactor).toFloat()),
                clickPaint
            )
        }
    }

    private fun createDeleteScreenAlertDialog(cardView: CardCellBinding): Boolean {
        var result = true
        val builder = AlertDialog.Builder(this@EventActivity)
            .setTitle("Delete trace item")
            .setMessage("Are you sure you want to delete this item from the trace? " +
                    "You will remove the screen, view hierarchy, and gesture data from this item.")
            .setPositiveButton("Yes") { dialog, _ ->
                val chosenEventLabel = "${cardView.time.text}; ${cardView.event.text}"
                result = deleteEvent(chosenPackageName!!, chosenTraceLabel!!, chosenEventLabel)
                // notify recycler view deletion happened
                val newScreens = ArrayList(screenPreview)
                val ind = screenPreview.indexOfFirst {
                        s -> s.timestamp == cardView.time.text.toString()
                }
                if (ind < 0) {  // should theoretically never happen
                    Log.e("EVENT", "could not find screenshot preview to delete")
                    dialog.dismiss()
                    return@setPositiveButton
                }
                newScreens.removeAt(ind)
                screenPreview.clear()
                screenPreview.addAll(newScreens)
                recyclerAdapter!!.notifyItemRemoved(ind)
                val itemChangeCount = newScreens.size - ind
                recyclerAdapter!!.notifyItemRangeChanged(ind, itemChangeCount)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
        val deleteAlertDialog = builder.create()
        deleteAlertDialog.show()
        return result
    }

    private fun createUploadTraceAlertDialog(uploadButtonView: View) {
        val traceDescInput = View.inflate(this, R.layout.upload_trace_dialog, null)
        val uploadDialog = AlertDialog.Builder(this)
            .setTitle("Upload Trace")
            .setView(traceDescInput)
            .setPositiveButton("UPLOAD") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val traceDescription = traceDescInput.findViewById<TextInputEditText>(R.id.upload_trace_input)
                    val uploadSuccess = uploadFullTraceContent(
                        chosenPackageName!!,
                        chosenTraceLabel!!,
                        traceDescription.text.toString())
                    if (!uploadSuccess) {
                        val errSnackbar = Snackbar.make(uploadButtonView, R.string.upload_fail, Snackbar.LENGTH_LONG)
                        errSnackbar.view.setBackgroundColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_light))
                        errSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            .setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                        errSnackbar.show()
                    } else {
                        val successSnackbar = Snackbar.make(uploadButtonView, R.string.upload_all_toast_success, Snackbar.LENGTH_SHORT)
                        successSnackbar.view.setBackgroundColor(ContextCompat.getColor(applicationContext, android.R.color.holo_green_light))
                        successSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            .setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                        successSnackbar.show()
                    }
                }
            }
            .setNegativeButton("CANCEL") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
        uploadDialog.show()
    }

    private fun navigateToNextActivity(cardView: CardCellBinding) {
        val nextClass = if (cardView.incompleteIndicator.visibility != View.VISIBLE) {
            ScreenShotActivity::class.java
        } else {
            IncompleteScreenActivity::class.java
        }
        val intent = Intent(
            applicationContext,
            nextClass
        )
        intent.putExtra("package_name", chosenPackageName)
        intent.putExtra("trace_label", chosenTraceLabel)
        val chosenEventLabel = "${cardView.time.text}; ${cardView.event.text}"
        intent.putExtra("event_label", chosenEventLabel)
        startActivity(intent)
    }
}
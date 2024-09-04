package edu.illinois.odim

import android.content.Intent
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import edu.illinois.odim.databinding.CardCellBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        for (event in eventsInTrace) {
            val screenshot = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, event)
            val eventDelimiter = "; "
            val eventInfo = event.split(eventDelimiter)
            val eventTime = eventInfo[0]
            val eventType = eventInfo[1]
            // check if source was null and gesture was not found
            val eventGesture = loadGesture(chosenPackageName!!, chosenTraceLabel!!, event)
            val isComplete = (eventGesture.className == null) // we do not capture classname if source isn't null
            if (!isComplete) {
                isTraceComplete = false
            }
            val screenshotPreview = ScreenShotPreview(screenshot, eventType, eventTime, isComplete)
            screenPreview.add(screenshotPreview)
        }
        recyclerAdapter = EventAdapter(screenPreview)
        recyclerAdapter!!.setOnItemLongClickListener(object: EventAdapter.OnItemLongClickListener {
            override fun onItemLongClick(cardView: CardCellBinding): Boolean {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this@EventActivity)
                var result = true
                builder
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
                val dialog: AlertDialog = builder.create()
                // check if any screens have null sources
                dialog.show()
                return result
            }
        })

        recyclerAdapter!!.setOnItemClickListener(object : EventAdapter.OnItemClickListener {
            override fun onItemClick(cardView: CardCellBinding) {
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
        })
        recyclerView?.adapter = recyclerAdapter

        // instantiate upload button
        uploadTraceButton = findViewById(R.id.upload_trace_button)
        uploadTraceButton?.setOnClickListener { buttonView ->
            val traceDescInput = View.inflate(this, R.layout.upload_trace_dialog, null)
            AlertDialog.Builder(this)
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
                            val errSnackbar = Snackbar.make(buttonView, R.string.upload_fail, Snackbar.LENGTH_LONG)
                            errSnackbar.view.setBackgroundColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_light))
                            errSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                                .setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                            errSnackbar.show()
                        } else {
                            val successSnackbar = Snackbar.make(buttonView, R.string.upload_all_toast_success, Snackbar.LENGTH_SHORT)
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
                .show()
        }
        uploadTraceButton?.isEnabled = isTraceComplete
    }

    override fun onStop() {
        super.onStop()
        for (screen in screenPreview) {
            screen.screenShot.recycle()
        }
        screenPreview.clear()
    }

    override fun onRestart() {
        super.onRestart()
        isTraceComplete = true
        screenPreview.clear()
        val eventsInTrace : List<String> = listEvents(chosenPackageName!!, chosenTraceLabel!!)
        for (event in eventsInTrace) {
            val screenshot = loadScreenshot(chosenPackageName!!, chosenTraceLabel!!, event)
            val eventDelimiter = "; "
            val eventInfo = event.split(eventDelimiter)
            val eventTime = eventInfo[0]
            val eventType = eventInfo[1]
            // check if source was null and gesture was not found
            val eventGesture = loadGesture(chosenPackageName!!, chosenTraceLabel!!, event)
            val isComplete = (eventGesture.className == null) // we do not capture classname if source isn't null
            if (!isComplete) {
                isTraceComplete = false
            }
            val screenshotPreview = ScreenShotPreview(screenshot, eventType, eventTime, isComplete)
            screenPreview.add(screenshotPreview)
        }
        uploadTraceButton?.isEnabled = isTraceComplete
        notifyEventAdapter()
    }
}
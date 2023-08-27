package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
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
    private val screenPreview : ArrayList<ScreenShotPreview> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceLabel = intent.extras!!["trace_label"].toString()
        title = "$chosenPackageName: $chosenTraceLabel"
        // change the way we do this with recyclerview
        recyclerView = findViewById(R.id.event_recycler_view)
        // use gridview instead of linear
        recyclerView?.layoutManager = GridLayoutManager(this, 2, RecyclerView.VERTICAL, false)
        // want both screenshot and event information for trace
        val eventsInTrace : ArrayList<String> = getEvents(chosenPackageName, chosenTraceLabel)
        for (event in eventsInTrace) {
            val screenshot = getScreenshot(chosenPackageName, chosenTraceLabel, event)
            val eventDelimiter = "; "
            val eventInfo = event.split(eventDelimiter)
            val eventTime = eventInfo[0]
            val eventType = eventInfo[1]
            val screenshotPreview = ScreenShotPreview(screenshot.bitmap!!, eventType, eventTime)
            screenPreview.add(screenshotPreview)
        }
        recyclerAdapter = EventAdapter(screenPreview)
        recyclerAdapter!!.setOnItemClickListener(object : EventAdapter.OnItemClickListener {
            override fun onItemClick(cardView: CardCellBinding) {
                val intent = Intent(
                    applicationContext,
                    ScreenShotActivity::class.java
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
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                .setTitle("Upload Trace")
                .setView(traceDescInput)
                .setPositiveButton("UPLOAD") { _, _ ->
                    val uploadScope = CoroutineScope(Dispatchers.IO)
                    uploadScope.launch {
                        val traceDescription = traceDescInput.findViewById<TextInputEditText>(R.id.upload_trace_input)
                        val uploadSuccess = uploadFullTraceContent(chosenPackageName!!,
                            chosenTraceLabel!!,
                            traceDescription.text.toString())
                        if (!uploadSuccess) {
                            val errSnackbar = Snackbar.make(buttonView, R.string.upload_fail, Snackbar.LENGTH_LONG)
                            errSnackbar.view.setBackgroundColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_light))
                            errSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                                .setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                            errSnackbar.show()
                        }
                        val successSnackbar = Snackbar.make(buttonView, R.string.upload_all_toast_success, Snackbar.LENGTH_SHORT)
                        successSnackbar.view.setBackgroundColor(ContextCompat.getColor(applicationContext, android.R.color.holo_green_light))
                        successSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            .setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                        successSnackbar.show()
                    }
                }
                .setNegativeButton("CANCEL") { dialogInterface, _ ->
                    dialogInterface.cancel()
                }
            builder.show()
        }
    }

    override fun onRestart() {
        super.onRestart()
        screenPreview.clear()
        val eventsInTrace : ArrayList<String> = getEvents(chosenPackageName, chosenTraceLabel)
        for (event in eventsInTrace) {
            val screenshot = getScreenshot(chosenPackageName, chosenTraceLabel, event)
            val eventDelimiter = "; "
            val eventInfo = event.split(eventDelimiter)
            val eventTime = eventInfo[0]
            val eventType = eventInfo[1]
            val screenshotPreview = ScreenShotPreview(screenshot.bitmap!!, eventType, eventTime)
            screenPreview.add(screenshotPreview)
        }
        notifyEventAdapter()
    }
}
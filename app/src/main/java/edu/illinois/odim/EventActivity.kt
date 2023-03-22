package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import edu.illinois.odim.databinding.CardCellBinding

// these were static in java
private var recyclerAdapter: EventAdapter? = null
fun notifyEventAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}
class EventActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceName: String? = null
    private var uploadTraceButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceName = intent.extras!!["trace_name"].toString()
        title = "$chosenPackageName: $chosenTraceName"
        // change the way we do this with recyclerview
        recyclerView = findViewById(R.id.eventRecyclerView)
        // use gridview instead of linear
        recyclerView?.layoutManager = GridLayoutManager(this, 2, RecyclerView.VERTICAL, false)
        // want both screenshot and event information for trace
        val screenPreview : ArrayList<ScreenShotPreview> = ArrayList()
        val eventsInTrace : ArrayList<String> = getEvents(chosenPackageName, chosenTraceName)
        for (event in eventsInTrace) {
            val screenshot = getScreenshot(chosenPackageName, chosenTraceName, event)
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
                intent.putExtra("trace_name", chosenTraceName)
                val chosenEventName: String = cardView.time.text.toString() + "; " + cardView.event.text.toString()
                intent.putExtra("event_name", chosenEventName)
                startActivity(intent)
            }
        })
        recyclerView?.adapter = recyclerAdapter
        // instantiate upload button
        uploadTraceButton = findViewById(R.id.uploadTraceButton)
        uploadTraceButton?.setOnClickListener { buttonView ->
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("WARNING")
            builder.setMessage(R.string.event_upload_warning)
            builder.setPositiveButton("UPLOAD") { _, _ ->
                for ((i, event) in eventsInTrace.withIndex()) {
                    val vhStringArr = getVh(chosenPackageName, chosenTraceName, event)
                    val uploadSuccess = uploadFile(
                        chosenPackageName!!,
                        chosenTraceName!!,
                        event,
                        vhStringArr[0],
                        getScreenshot(chosenPackageName, chosenTraceName, event).bitmap
                    )
                    if (!uploadSuccess) {
                        val errSnackbar = Snackbar.make(buttonView, R.string.upload_fail, Snackbar.LENGTH_LONG)
                        errSnackbar.view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                        errSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            .setTextColor(ContextCompat.getColor(this, R.color.white))
                        errSnackbar.show()
                        break
                    }
                }
                val successSnackbar = Snackbar.make(buttonView, R.string.upload_all_toast_success, Snackbar.LENGTH_SHORT)
                successSnackbar.view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                successSnackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    .setTextColor(ContextCompat.getColor(this, R.color.white))
                successSnackbar.show()
            }
            builder.setNegativeButton("CANCEL") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            builder.show()

        }
    }
}
package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.CardCellBinding
import kotlinx.coroutines.*

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
        // create new object SSP contains both screenshot bitmap and event string stuff
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
        uploadTraceButton?.setOnClickListener { view ->
            CoroutineScope(Dispatchers.Main + Job()).launch() {
                withContext(Dispatchers.IO) {
                    for (event: String in eventsInTrace) {
                        Log.i("TraceEvent", event)
                        val vhStringArr = getVh(chosenPackageName, chosenTraceName, event)
                        uploadFile(
                            chosenPackageName!!,
                            chosenTraceName!!,
                            event,
                            vhStringArr[0],
                            getScreenshot(chosenPackageName, chosenTraceName, event).bitmap,
                            view as Button,
                            applicationContext
                        )
                    }
                }
            }
        }
    }
}
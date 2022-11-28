package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceName = intent.extras!!["trace_name"].toString()
        title = "$chosenPackageName: $chosenTraceName"
        // change the way we do this with recyclerview
        recyclerView = findViewById(R.id.eventRecyclerView)
        // use gridview instead of linear
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        // want both screenshot and event information for trace
        // create new object SSP contains both screenshot bitmap and event string stuff
        val screenPreview : ArrayList<ScreenShotPreview> = ArrayList()
        val eventsInTrace : ArrayList<String> = getEvents(chosenPackageName, chosenTraceName)
        for (event in eventsInTrace) {
            val screenshot = getScreenshot(chosenPackageName, chosenTraceName, event)
            val screenshotPreview = ScreenShotPreview(screenshot.bitmap!!, event, event)
            screenPreview.add(screenshotPreview)
        }
        recyclerAdapter = EventAdapter(this, screenPreview)
        recyclerView?.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : EventAdapter.OnItemClickListener {
            // TODO: change to
            override fun onItemClick(cardView: CardCellBinding) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(
                    applicationContext,
                    ScreenShotActivity::class.java
                )
                val chosenEventName: String = cardView.event.toString() // ((view as LinearLayout).getChildAt(0) as TextView).text.toString()
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_name", chosenTraceName)
                intent.putExtra("event_name", chosenEventName)
                startActivity(intent)
            }
        })
    }
}
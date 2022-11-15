package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

// these were static in java
private var recyclerAdapter: CustomAdapter? = null
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
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        val eventList: ArrayList<String> = getEvents(chosenPackageName, chosenTraceName)
        recyclerAdapter = CustomAdapter(this, eventList)
        recyclerView?.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(rowText: TextView) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(
                    applicationContext,
                    ScreenShotActivity::class.java
                )
                val chosenEventName: String = rowText.text.toString() // ((view as LinearLayout).getChildAt(0) as TextView).text.toString()
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_name", chosenTraceName)
                intent.putExtra("event_name", chosenEventName)
                startActivity(intent)
            }
        })

        // instantiate upload button
        uploadTraceButton = findViewById(R.id.uploadTraceButton)
        uploadTraceButton?.setOnClickListener { view ->
            CoroutineScope(Dispatchers.Main + Job()).launch() {
                withContext(Dispatchers.IO) {
                    for (event: String in eventList) {
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
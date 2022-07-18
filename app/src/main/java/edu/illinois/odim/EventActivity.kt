package edu.illinois.odim

import CustomAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

// these were static in java
private var recyclerAdapter: CustomAdapter? = null
fun notifyEventAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}
class EventActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var package_name: String? = null
    private var trace_name: String? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)
        package_name = getIntent().extras!!["package_name"].toString()
        trace_name = getIntent().extras!!["trace_name"].toString()
        setTitle("$package_name: $trace_name")
        // change the way we do this with recyclerview
        recyclerView = findViewById(R.id.eventRecyclerView) as RecyclerView?
        recyclerAdapter = CustomAdapter(this, get_events(package_name, trace_name))
        recyclerView?.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(view: View) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(
                    getApplicationContext(),
                    ScreenShotActivity::class.java
                )
                val event_name: String = (view as TextView).getText().toString()
                intent.putExtra("package_name", package_name)
                intent.putExtra("trace_name", trace_name)
                intent.putExtra("event_name", event_name)
                startActivity(intent)
            }
        })
    }
}
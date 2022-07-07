package edu.illinois.odim

import CustomAdapter
import android.content.Intent
import android.content.Intent.getIntent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ApplicationProvider.getApplicationContext


class EventActivity : AppCompatActivity() {
    // TODO: change listView to recyclerView?
    private var recyclerView: RecyclerView? = null
    private var package_name: String? = null
    private var trace_name: String? = null
    // TODO: this var should be static
    private var recyclerAdapter: CustomAdapter? = null

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

        // TODO: add onItemClickListener to recyclerView
        listView?.setOnItemClickListener(object : AdapterView.OnItemClickListener {
            override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
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

    // TODO: this should be static
    fun notifyEventAdapter() {
        recyclerAdapter?.notifyDataSetChanged()
    }
}
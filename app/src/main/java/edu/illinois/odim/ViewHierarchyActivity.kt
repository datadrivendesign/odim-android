package edu.illinois.odim

import android.os.Bundle
import CustomAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

// These were static in java
private var recyclerAdapter: CustomAdapter? = null
fun notifyVHAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class ViewHierarchyActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var package_name: String? = null
    private var trace_name: String? = null
    private var event_name: String? = null


    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_hierarchy)
        package_name = getIntent().extras!!["package_name"].toString()
        trace_name = getIntent().extras!!["trace_name"].toString()
        event_name = getIntent().extras!!["event_name"].toString()
        setTitle("View Hierarchy")
        recyclerView = findViewById(R.id.vhRecyclerView) as RecyclerView?
        // TODO: Should view hierarchy be a string? It should be a tree right?
        recyclerAdapter = CustomAdapter(
            this,
            get_vh(package_name, trace_name, event_name)
        )
        recyclerView?.adapter = recyclerAdapter
    }
}
package edu.illinois.odim

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// These were static in java
private var recyclerAdapter: CustomAdapter? = null
fun notifyVHAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class ViewHierarchyActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null
    private var chosenTraceName: String? = null
    private var chosenEventName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_hierarchy)
        chosenPackageName = intent.extras!!["package_name"].toString()
        chosenTraceName = intent.extras!!["trace_name"].toString()
        chosenEventName = intent.extras!!["event_name"].toString()
        title = "View Hierarchy"
        recyclerView = findViewById(R.id.vhRecyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        // TODO: Should view hierarchy be a string? It should be a tree right?
        recyclerAdapter = CustomAdapter(
            this,
            getVh(chosenPackageName, chosenTraceName, chosenEventName)
        )
        recyclerView?.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(rowText: TextView) {
                Log.i("View Size", recyclerAdapter!!.itemCount.toString())
                Log.i("View Element", rowText.text.toString()) //((view as LinearLayout).getChildAt(0) as TextView).text.toString())
            }
        })
    }
}
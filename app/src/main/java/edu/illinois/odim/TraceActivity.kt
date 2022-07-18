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
fun notifyTraceAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class TraceActivity : AppCompatActivity(){
    private var recyclerView: RecyclerView? = null
    private var package_name: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        package_name = intent.extras!!["package_name"].toString()
        title = package_name
        recyclerView = findViewById<View>(R.id.traceRecyclerView) as RecyclerView
        recyclerAdapter = CustomAdapter(
            this,
            get_traces(package_name)
        )
        recyclerView!!.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(view: View) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                val trace_name: String = (view as TextView).getText().toString()
                intent.putExtra("package_name", package_name)
                intent.putExtra("trace_name", trace_name)
                startActivity(intent)
            }
        })
    }
}
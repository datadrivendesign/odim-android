package edu.illinois.odim

import CustomAdapter
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class TraceActivity : AppCompatActivity(){

    private var recyclerView: RecyclerView? = null
    private var package_name: String? = null
    // TODO: this var should be static
    private var recyclerAdapter: CustomAdapter? = null

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
        recyclerView.setOnItemClickListener(object : OnItemClickListener() {
            fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                val trace_name: String = (view as TextView).getText().toString()
                intent.putExtra("package_name", package_name)
                intent.putExtra("trace_name", trace_name)
                startActivity(intent)
            }
        })
    }

    // TODO: This should be static
    fun notifyTraceAdapter() {
        recyclerAdapter?.notifyDataSetChanged()
    }


}
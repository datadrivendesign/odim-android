package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// these were static in java
private var recyclerAdapter: CustomAdapter? = null
fun notifyTraceAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class TraceActivity : AppCompatActivity(){
    private var recyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        chosenPackageName = intent.extras!!["package_name"].toString()
        title = chosenPackageName
        recyclerView = findViewById<View>(R.id.traceRecyclerView) as RecyclerView
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerAdapter = CustomAdapter(
            this,
            getTraces(chosenPackageName)
        )
        // Ethar added
        recyclerView?.addItemDecoration(RecyclerViewItemDecoration(this, R.drawable.divider))
        recyclerView!!.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(rowText: TextView) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                val chosenTraceName: String = rowText.text.toString() // ((view as LinearLayout).getChildAt(0) as TextView).text.toString()
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_name", chosenTraceName)

                startActivity(intent)
            }
        })
    }
}
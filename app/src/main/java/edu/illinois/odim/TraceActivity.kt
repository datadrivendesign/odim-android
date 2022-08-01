package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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
    private var chosenPackageNAme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        chosenPackageNAme = intent.extras!!["package_name"].toString()
        title = chosenPackageNAme
        recyclerView = findViewById<View>(R.id.traceRecyclerView) as RecyclerView
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerAdapter = CustomAdapter(
            this,
            getTraces(chosenPackageNAme)
        )
        recyclerView!!.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(view: View) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                // TODO: this is a dumb way to get string, please fix later
                val chosenTraceName: String = ((view as LinearLayout).getChildAt(0) as TextView).text.toString()
                intent.putExtra("package_name", chosenPackageNAme)
                intent.putExtra("trace_name", chosenTraceName)
                startActivity(intent)
            }
        })
    }
}
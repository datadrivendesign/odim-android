package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// these were static in java
private var recyclerAdapter: TraceAdapter? = null
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
        recyclerView = findViewById<View>(R.id.trace_recycler_view) as RecyclerView
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerAdapter = TraceAdapter(this, chosenPackageName!!, getTraces(chosenPackageName))

        val packageManager = this.packageManager
        val traceAppNameView = findViewById<TextView>(R.id.trace_app_name)
        val appInfo = packageManager.getApplicationInfo(chosenPackageName!!, 0)
        traceAppNameView.text = packageManager.getApplicationLabel(appInfo)
        val traceAppIconView = findViewById<ImageView>(R.id.trace_app_image)
        val icon = packageManager.getApplicationIcon(chosenPackageName!!)
        traceAppIconView.setImageDrawable(icon)

        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        recyclerView?.addItemDecoration(decoratorVertical)

        recyclerView!!.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : TraceAdapter.OnItemClickListener {
            override fun onItemClick(traceName: String) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_name", traceName)

                startActivity(intent)
            }
        })
    }
}
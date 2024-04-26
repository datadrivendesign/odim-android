package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private lateinit var traceList: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        chosenPackageName = intent.extras!!.getString("package_name")
        recyclerView = findViewById<View>(R.id.trace_recycler_view) as RecyclerView
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        traceList = listTraces(chosenPackageName!!)
        recyclerAdapter = TraceAdapter(this, chosenPackageName!!, traceList)// getTraces(chosenPackageName))

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

        recyclerAdapter!!.setOnItemLongClickListener(object : TraceAdapter.OnItemLongClickListener {
            override fun onItemLongClick(traceLabel: String): Boolean {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this@TraceActivity)
                var result = true
                builder
                    .setTitle("Delete trace")
                    .setMessage("Are you sure you want to delete this trace recording? " +
                            "You will remove the entire trace, including all screens, view hierarchies, and gestures.")
                    .setPositiveButton("Yes") { dialog, _ ->
                        result = deleteTrace(chosenPackageName!!, traceLabel)
                        // notify recycler view deletion happened
                        val newTraces = ArrayList(traceList)
                        val ind = traceList.indexOfFirst {
                            trace -> trace == traceLabel
                        }
                        if (ind < 0) {  // should theoretically never happen
                            Log.e("TRACE", "could not find trace to delete")
                            dialog.dismiss()
                            return@setPositiveButton
                        }
                        newTraces.removeAt(ind)
                        traceList.clear()
                        traceList.addAll(newTraces)
                        recyclerAdapter!!.notifyItemRemoved(ind)
                        val itemChangeCount = newTraces.size - ind
                        recyclerAdapter!!.notifyItemRangeChanged(ind, itemChangeCount)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                val dialog: AlertDialog = builder.create()
                dialog.show()
                return result
            }
        })

        recyclerAdapter!!.setOnItemClickListener(object : TraceAdapter.OnItemClickListener {
            override fun onItemClick(traceLabel: String) {
                val intent = Intent(applicationContext, EventActivity::class.java)
                intent.putExtra("package_name", chosenPackageName)
                intent.putExtra("trace_label", traceLabel)
                startActivity(intent)
            }
        })
    }

    override fun onRestart() {
        super.onRestart()
        traceList.clear()
        traceList.addAll(listTraces(chosenPackageName!!))
        notifyTraceAdapter()
    }
}
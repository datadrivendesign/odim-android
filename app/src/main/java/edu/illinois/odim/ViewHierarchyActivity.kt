package edu.illinois.odim

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

// These were static in java
private var recyclerAdapter: CustomAdapter? = null
fun notifyVHAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class ViewHierarchyActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var uploadButton: Button? = null
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
        // populate recycler view with VH content
        val vhStringArr: ArrayList<String> = getVh(chosenPackageName, chosenTraceName, chosenEventName)
        recyclerAdapter = CustomAdapter(
            this,
            vhStringArr
        )
        recyclerView?.adapter = recyclerAdapter
        recyclerAdapter!!.setOnItemClickListener(object : CustomAdapter.OnItemClickListener {
            override fun onItemClick(rowText: TextView) {
                Log.i("View Size", recyclerAdapter!!.itemCount.toString())
                Log.i("View Element", rowText.text.toString()) //((view as LinearLayout).getChildAt(0) as TextView).text.toString())
            }
        })
        // instantiate upload button
        uploadButton = findViewById(R.id.uploadEventButton)
        uploadButton?.setOnClickListener { view ->
            CoroutineScope(Dispatchers.Main + Job()).launch() {
                withContext(Dispatchers.IO) {
                    uploadFile(
                        chosenPackageName!!,
                        chosenTraceName!!,
                        chosenEventName!!,
                        vhStringArr[0],
                        getScreenshot(chosenPackageName, chosenTraceName, chosenEventName).bitmap,
                        view as Button,
                        applicationContext
                    )
                }

            }
        }
    }
}
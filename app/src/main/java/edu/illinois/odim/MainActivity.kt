package edu.illinois.odim

import CustomAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private var recyclerView : RecyclerView? = null
    // TODO: this var should be static
    private var recyclerAdapter : CustomAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = " Packages"

        recyclerView = findViewById<RecyclerView>(R.id.packageRecyclerView)

        recyclerAdapter = CustomAdapter(this, get_packages()!!)

        recyclerView?.adapter = recyclerAdapter

        // TODO: need to create an onitemclicklistener in RecyclerView
        recyclerView.setOnItemClickListener(object : OnItemClickListener() {
            fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                val package_name: String = (view as TextView).getText().toString()
                intent.putExtra("package_name", package_name)
                startActivity(intent)
            }
        })
    }

    // TODO: this needs to be static
    fun notifyPackageAdapter() {
        recyclerAdapter?.notifyDataSetChanged();
    }
}


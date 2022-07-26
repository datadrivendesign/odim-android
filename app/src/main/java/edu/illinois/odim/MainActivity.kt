package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// this should be static
private var recyclerAdapter : CustomAdapter? = null
fun notifyPackageAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class MainActivity : AppCompatActivity() {
    private var recyclerView : RecyclerView? = null
    // TODO: this var should be static

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = " Packages"

        recyclerView = findViewById(R.id.packageRecyclerView)

        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        val packages = getPackages()
        System.out.println(packages)

        recyclerAdapter = CustomAdapter(this, packages)

        recyclerView?.adapter = recyclerAdapter

        recyclerAdapter!!.setOnItemClickListener(object: CustomAdapter.OnItemClickListener {
            override fun onItemClick(view: View) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                val clickedPackageName: String = (view as TextView).text.toString()
                intent.putExtra("package_name", clickedPackageName)
                startActivity(intent)
            }
        })
    }


}


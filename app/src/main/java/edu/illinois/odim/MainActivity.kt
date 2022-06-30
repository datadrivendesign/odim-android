package edu.illinois.odim

import CustomAdapter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        var recyclerAdapter : CustomAdapter? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = " Packages"

        val recyclerView = findViewById<RecyclerView>(R.id.packageRecyclerView)

        recyclerAdapter = CustomAdapter(this, get_packages()!!)

        recyclerView.adapter = recyclerAdapter

        // TODO: need to create an onitemclicklistener in RecyclerView
    }
}

fun notifyPackageAdapter() {
    if (MainActivity.recyclerAdapter != null) {
        // TODO: Find out why we do this
        MainActivity.recyclerAdapter!!.notifyDataSetChanged();
    }
}
package edu.illinois.odim

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = " Packages"

        val recyclerView = findViewById<RecyclerView>(R.id.packageRecyclerView)

        val arrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, )
    }
}
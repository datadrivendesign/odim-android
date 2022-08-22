package edu.illinois.odim

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// this should be static
private var recyclerAdapter : CustomAdapter? = null
internal var userId = "test_user15"

fun notifyPackageAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class MainActivity : AppCompatActivity() {
    private var recyclerView : RecyclerView? = null
    // TODO: this var should be static

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // found code from: https://handyopinion.com/show-alert-dialog-with-an-input-field-edittext-in-android-kotlin/
        // set up user id
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Input user id")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("DONE", { dialogInterface, which ->
            userId = input.text.toString()
            Log.i("userId", userId)
        })
        builder.setNegativeButton("CANCEL", { dialogInterface, which ->
            dialogInterface.cancel()
        })
        builder.show()
        // set up package view
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
                // TODO: this is a dumb way to get string, please fix later
                val clickedPackageName: String = ((view as LinearLayout).getChildAt(0) as TextView).text.toString()
                intent.putExtra("package_name", clickedPackageName)
                startActivity(intent)
            }
        })
    }
}


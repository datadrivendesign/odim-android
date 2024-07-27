package edu.illinois.odim

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


// this should be static
private var recyclerAdapter : MainAdapter? = null


fun notifyPackageAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class MainActivity : AppCompatActivity() {
    private var recyclerView : RecyclerView? = null
    private var workerIdInput : EditText? = null
    private lateinit var appPackageList: MutableList<String>

    private fun createWorkerInputForm() {
        // found code from: https://handyopinion.com/show-alert-dialog-with-an-input-field-edittext-in-android-kotlin/
        val workerForm = View.inflate(this, R.layout.worker_input_dialog, null)
        workerIdInput = workerForm.findViewById(R.id.dialog_worker_id_input)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            .setTitle("Worker Credentials")
            .setCancelable(false)
            .setView(workerForm)
            .setPositiveButton("DONE") { _, _ ->
                workerId = workerIdInput?.text.toString()
            }
        val alertDialog = builder.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        workerIdInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(str: Editable?) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.odim_app_header))
        createWorkerInputForm()

        recyclerView = findViewById(R.id.package_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        appPackageList = listPackages()
        recyclerAdapter = MainAdapter(this, appPackageList) // getPackages())
        recyclerView?.adapter = recyclerAdapter

        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        recyclerView?.addItemDecoration(decoratorVertical)
        recyclerAdapter!!.setOnItemLongClickListener(object: MainAdapter.OnItemLongClickListener {
            override fun onItemLongClick(appPackage: String): Boolean {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
                var result = true
                builder
                    .setTitle("Delete app")
                    .setMessage("Are you sure you want to delete this item from the trace? " +
                            "You will remove the every trace recorded by this app.")
                    .setPositiveButton("Yes") { dialog, _ ->
                        result = deleteApp(appPackage)
                        // notify recycler view deletion happened
                        val newPackages = ArrayList(appPackageList)
                        val ind = appPackageList.indexOfFirst {
                                app -> app == appPackage
                        }
                        if (ind < 0) {  // should theoretically never happen
                            Log.e("APP", "could not find trace to delete")
                            dialog.dismiss()
                            return@setPositiveButton
                        }
                        newPackages.removeAt(ind)
                        appPackageList.clear()
                        appPackageList.addAll(newPackages)
                        recyclerAdapter!!.notifyItemRemoved(ind)
                        val itemChangeCount = newPackages.size - ind
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

        recyclerAdapter!!.setOnItemClickListener(object: MainAdapter.OnItemClickListener {
            override fun onItemClick(appPackage: String) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                intent.putExtra("package_name", appPackage)
                startActivity(intent)
            }
        })
    }

    override fun onRestart() {
        super.onRestart()
        appPackageList.clear()
        appPackageList.addAll(listPackages())
        notifyPackageAdapter()
    }
}


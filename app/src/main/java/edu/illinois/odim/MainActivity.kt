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
import edu.illinois.odim.LocalStorageOps.deleteApp
import edu.illinois.odim.LocalStorageOps.listPackages

// this should be static
private var mainAdapter : MainAdapter? = null

fun notifyPackageAdapter() {
    mainAdapter?.notifyDataSetChanged()
}

class MainActivity : AppCompatActivity() {
    private var recyclerView : RecyclerView? = null
    private lateinit var appPackageList: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.odim_app_header))
        createWorkerInputForm()
        // set up recycler view
        recyclerView = findViewById(R.id.package_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        appPackageList = listPackages()
        mainAdapter = MainAdapter(this, appPackageList) // getPackages())
        recyclerView?.adapter = mainAdapter
        // set up recycler view listeners
        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        recyclerView?.addItemDecoration(decoratorVertical)
        mainAdapter!!.setOnItemLongClickListener(object: MainAdapter.OnItemLongClickListener {
            override fun onItemLongClick(appPackage: String): Boolean {
                return createDeleteAppAlertDialog(appPackage)
            }
        })
        mainAdapter!!.setOnItemClickListener(object: MainAdapter.OnItemClickListener {
            override fun onItemClick(appPackage: String) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                intent.putExtra("package_name", appPackage)
                startActivity(intent)
            }
        })
    }

    private fun createWorkerInputForm() {
        // found code from: https://handyopinion.com/show-alert-dialog-with-an-input-field-edittext-in-android-kotlin/
        val workerForm = View.inflate(this, R.layout.dialog_worker_input, null)
        val workerIdInput: EditText = workerForm.findViewById(R.id.dialog_worker_id_input)
        workerIdInput.setText(workerId)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            .setTitle("Worker Credentials")
            .setCancelable(false)
            .setView(workerForm)
            .setPositiveButton("DONE") { _, _ ->
                workerId = workerIdInput.text.toString()
            }
        val alertDialog = builder.create()
        alertDialog.show()
        workerIdInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(str: Editable?) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
            }
        })
    }

    private fun createDeleteAppAlertDialog(appPackage: String): Boolean {
        var result = true
        val builder = AlertDialog.Builder(this@MainActivity)
            .setTitle("Delete app")
            .setMessage("Are you sure you want to delete this item from the trace? " +
                    "You will remove the every trace recorded by this app.")
            .setPositiveButton("Yes") { dialog, _ ->
                result = deleteApp(appPackage)
                // notify recycler view deletion happened
                val newPackages = ArrayList(appPackageList)
                val ind = appPackageList.indexOfFirst { app -> app == appPackage }
                if (ind < 0) {  // should theoretically never happen
                    Log.e("APP", "could not find trace to delete")
                    dialog.dismiss()
                    return@setPositiveButton
                }
                newPackages.removeAt(ind)
                appPackageList.clear()
                appPackageList.addAll(newPackages)
                mainAdapter!!.notifyItemRemoved(ind)
                val itemChangeCount = newPackages.size - ind
                mainAdapter!!.notifyItemRangeChanged(ind, itemChangeCount)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
        val deleteAlertDialog = builder.create()
        deleteAlertDialog.show()
        return result
    }

    override fun onRestart() {
        super.onRestart()
        appPackageList.clear()
        appPackageList.addAll(listPackages())
        notifyPackageAdapter()
    }
}


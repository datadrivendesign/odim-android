package edu.illinois.odim

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private var projectCodeInput : EditText? = null

    private fun createWorkerInputForm() {
        // found code from: https://handyopinion.com/show-alert-dialog-with-an-input-field-edittext-in-android-kotlin/
        val viewForm = layoutInflater.inflate(R.layout.layout_dialog, null)
        workerIdInput = viewForm.findViewById(R.id.dialog_worker_id_input)
        projectCodeInput = viewForm.findViewById(R.id.dialog_project_code_input)
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Worker Credentials")
        builder.setCancelable(false)
        builder.setView(viewForm)
        builder.setPositiveButton("DONE") { _, _ ->
            workerId = workerIdInput?.text.toString()
            projectCode = projectCodeInput?.text.toString()
            // TODO: check if trace and project exist
            // TODO: get the cloud storage location
        }
        val alertDialog = builder.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        workerIdInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(str: Editable?) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
                                                                                && !TextUtils.isEmpty(projectCodeInput?.text)
            }
        })
        projectCodeInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(str: Editable?) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !TextUtils.isEmpty(str)
                                                                                && !TextUtils.isEmpty(workerIdInput?.text)
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.odim_app_header))
        createWorkerInputForm()
        // set up package view
//        title = " Packages"

        recyclerView = findViewById(R.id.package_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerAdapter = MainAdapter(this, getPackages())
        recyclerView?.adapter = recyclerAdapter

        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        recyclerView?.addItemDecoration(decoratorVertical)


        recyclerAdapter!!.setOnItemClickListener(object: MainAdapter.OnItemClickListener {
            override fun onItemClick(appPackage: String) {//parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                intent.putExtra("package_name", appPackage)
                startActivity(intent)
            }
        })
    }
}


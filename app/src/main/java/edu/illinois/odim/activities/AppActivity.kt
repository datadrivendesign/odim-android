package edu.illinois.odim.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.utils.LocalStorageOps.deleteApp
import edu.illinois.odim.utils.LocalStorageOps.listPackages
import edu.illinois.odim.R
import edu.illinois.odim.adapters.AppAdapter
import edu.illinois.odim.dataclasses.AppItem
import edu.illinois.odim.workerId

private var appAdapter : AppAdapter? = null

fun notifyPackageAdapter() {
    appAdapter?.notifyDataSetChanged()
}

class AppActivity : AppCompatActivity() {
    private var mainRecyclerView : RecyclerView? = null
    private lateinit var appList: MutableList<AppItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_app)
        setSupportActionBar(findViewById(R.id.odim_app_header))
        createWorkerInputForm()
        // set up recycler view
        mainRecyclerView = findViewById(R.id.app_package_recycler_view)
        mainRecyclerView?.layoutManager = LinearLayoutManager(
            this,
            RecyclerView.VERTICAL,
            false
        )
        val appPackageList = listPackages()
        appList = populateAppList(appPackageList)
        appAdapter = AppAdapter(appList)
        mainRecyclerView?.adapter = appAdapter
        // set up recycler view listeners
        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        mainRecyclerView?.addItemDecoration(decoratorVertical)
        appAdapter!!.setOnItemLongClickListener(object: AppAdapter.OnItemLongClickListener {
            override fun onItemLongClick(position: Int): Boolean {
                return createDeleteAppAlertDialog(position)
            }
        })
        appAdapter!!.setOnItemClickListener(object: AppAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val intent = Intent(applicationContext, TraceActivity::class.java)
                intent.putExtra("package_name", appList[position].appPackage)
                startActivity(intent)
            }
        })
    }

    private fun populateAppList(appPackageList: List<String>): MutableList<AppItem> {
        val mainList = mutableListOf<AppItem>()
        for (appPackage in appPackageList) {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(appPackage, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val appIcon = packageManager.getApplicationIcon(appPackage)
            mainList.add(AppItem(appPackage, appName, appIcon))
        }
        return mainList
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

    private fun createDeleteAppAlertDialog(position: Int): Boolean {
        var result = true
        val builder = AlertDialog.Builder(this@AppActivity)
            .setTitle(getString(R.string.delete_app_dialog_title))
            .setMessage(getString(R.string.delete_app_dialog_message))
            .setPositiveButton(getString(R.string.dialog_positive)) { dialog, _ ->
                result = deleteApp(appList[position].appPackage)
                appList.removeAt(position)
                appAdapter!!.notifyItemRemoved(position)
                val itemChangeCount = appList.size - position
                appAdapter!!.notifyItemRangeChanged(position, itemChangeCount)
            }
            .setNegativeButton(getString(R.string.dialog_negative)) { dialog, _ ->
                dialog.dismiss()
            }
        val deleteAlertDialog = builder.create()
        deleteAlertDialog.show()
        return result
    }

    override fun onRestart() {
        super.onRestart()
        appList.clear()
        val appPackages = listPackages()
        appList.addAll(populateAppList(appPackages))
        notifyPackageAdapter()
    }
}


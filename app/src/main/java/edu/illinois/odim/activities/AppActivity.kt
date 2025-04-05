package edu.illinois.odim.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.R
import edu.illinois.odim.adapters.AppAdapter
import edu.illinois.odim.dataclasses.AppItem
import edu.illinois.odim.utils.LocalStorageOps.deleteApp
import edu.illinois.odim.utils.LocalStorageOps.listPackages
import edu.illinois.odim.workerId

private var appAdapter : AppAdapter? = null

fun notifyAppAdapter() {
    appAdapter?.notifyDataSetChanged()
}

class AppActivity : AppCompatActivity() {
    private var mainRecyclerView : RecyclerView? = null
    private lateinit var appList: MutableList<AppItem>
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_app)
        setSupportActionBar(findViewById(R.id.odim_app_header))
        createWorkerInputForm()
        // set up scan qr button
        val scanQRBtn = findViewById<Button>(R.id.button_navigate_qr)
        scanQRBtn.setOnClickListener {
            startActivity(Intent(applicationContext, CaptureActivity::class.java))

        }
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
                if (actionMode == null) {
                    actionMode = startActionMode(multiSelectActionModeCallback)
                }
                toggleSelection(position)
                return true
            }
        })
        appAdapter!!.setOnItemClickListener(object: AppAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (actionMode != null) {
                    toggleSelection(position)
                } else {
                    val intent = Intent(applicationContext, TraceActivity::class.java)
                    intent.putExtra("package_name", appList[position].appPackage)
                    startActivity(intent)
                }
            }
        })
    }

    private val multiSelectActionModeCallback = object : ActionMode.Callback {
        /** Called when the action mode is created. startActionMode() is called. */
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val inflater: MenuInflater = mode.menuInflater
            inflater.inflate(R.menu.multi_select_row_menu, menu)
            findViewById<CoordinatorLayout>(R.id.main_app_header).visibility = View.GONE
            return true
        }
        /** Called each time the action mode is shown. Always called after onCreateActionMode,
         * and might be called multiple times if the mode is invalidated.
         **/
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }
        /** Called when the user selects a contextual menu item. */
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_row_traces -> {
                    createDeleteAppAlertDialog(mode)
                    true
                }
                else -> false
            }
        }
        /** Called when the user exits the action mode. */
        override fun onDestroyActionMode(mode: ActionMode) {
            for (app in appList) {
                app.isSelected = false
            }
            notifyTraceAdapter()
            findViewById<CoordinatorLayout>(R.id.main_app_header).visibility = View.VISIBLE
            actionMode = null
        }
    }

    private fun toggleSelection(position: Int) {
        appList[position].isSelected = !appList[position].isSelected
        appAdapter!!.notifyItemChanged(position)
        // edit menu title
        val total = appList.count { it.isSelected }
        actionMode?.title = getString(R.string.options_bar_text_num, total)
    }

    private fun populateAppList(appPackageList: List<String>): MutableList<AppItem> {
        val mainList = mutableListOf<AppItem>()
        for (appPackage in appPackageList) {
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

    private fun deleteSelectedApps(): Boolean {
        val appIterator = appList.iterator()
        while (appIterator.hasNext()) {
            val appItem = appIterator.next()
            if (appItem.isSelected) {
                val result = deleteApp(appItem.appPackage)
                if (!result) {
                    return false
                }
                appIterator.remove()
            }
        }
        return true
    }

    private fun createDeleteAppAlertDialog(mode: ActionMode): Boolean {
        var result = true
        val builder = AlertDialog.Builder(this@AppActivity)
            .setTitle(getString(R.string.delete_app_dialog_title))
            .setMessage(getString(R.string.delete_app_dialog_message))
            .setPositiveButton(getString(R.string.dialog_positive)) { dialog, _ ->
                result = deleteSelectedApps()
                notifyAppAdapter()
                mode.finish()
                dialog.dismiss()
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
        notifyAppAdapter()
    }
}


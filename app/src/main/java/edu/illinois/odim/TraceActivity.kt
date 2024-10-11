package edu.illinois.odim

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.LocalStorageOps.deleteTrace
import edu.illinois.odim.LocalStorageOps.listEvents
import edu.illinois.odim.LocalStorageOps.listTraces
import edu.illinois.odim.LocalStorageOps.renameTrace

private var traceAdapter: TraceAdapter? = null

fun notifyTraceAdapter() {
    traceAdapter?.notifyDataSetChanged()
}

class TraceActivity : AppCompatActivity(){
    private var traceRecyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null
    private lateinit var traceList: MutableList<TraceItem>
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        chosenPackageName = intent.extras!!.getString("package_name")
        traceRecyclerView = findViewById<View>(R.id.trace_recycler_view) as RecyclerView
        traceRecyclerView?.layoutManager = LinearLayoutManager(
            this,
            RecyclerView.VERTICAL,
            false
        )
        val traceLabels = listTraces(chosenPackageName!!)
        traceList = populateTraceList(traceLabels)
        traceAdapter = TraceAdapter(traceList)
        // set up UI icons
        setUpUIComponents()
        // set up recycler view
        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        traceRecyclerView?.addItemDecoration(decoratorVertical)
        traceRecyclerView!!.adapter = traceAdapter
        // set up recycler view listeners
        traceAdapter!!.setOnItemLongClickListener(object : TraceAdapter.OnItemLongClickListener {
            override fun onItemLongClick(position: Int): Boolean {
                if (actionMode == null) {
                    actionMode = startActionMode(multiSelectActionModeCallback)
                }
                toggleSelection(position)
                return true
            }
        })
        traceAdapter!!.setOnItemClickListener(object : TraceAdapter.OnItemClickListener {
            override fun onItemClick(position: Int): Boolean {
                if (actionMode != null) {
                    toggleSelection(position)
                } else {
                    navigateToNextActivity(traceList[position].traceLabel)
                }
                return true
            }
        })
    }

    override fun onRestart() {
        super.onRestart()
        traceList.clear()
        val traceLabels = listTraces(chosenPackageName!!)
        traceList.addAll(populateTraceList(traceLabels))
        notifyTraceAdapter()
    }

    private fun populateTraceList(traceLabels: List<String>): MutableList<TraceItem> {
        val traceItemList = mutableListOf<TraceItem>()
        for (traceLabel in traceLabels) {
            val numEvents = listEvents(chosenPackageName!!, traceLabel).size
            Log.i("TRACE", "$numEvents")
            val traceItem = TraceItem(traceLabel, numEvents)
            traceItemList.add(traceItem)
        }
        return traceItemList
    }

    private fun setUpUIComponents() {
        val packageManager = this.packageManager
        val traceAppNameView = findViewById<TextView>(R.id.trace_app_name)
        val appInfo = packageManager.getApplicationInfo(chosenPackageName!!, 0)
        traceAppNameView.text = packageManager.getApplicationLabel(appInfo)
        val traceAppIconView = findViewById<ImageView>(R.id.trace_app_image)
        val icon = packageManager.getApplicationIcon(chosenPackageName!!)
        traceAppIconView.setImageDrawable(icon)
    }

    private val multiSelectActionModeCallback = object : ActionMode.Callback {
        /** Called when the action mode is created. startActionMode() is called. */
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val inflater: MenuInflater = mode.menuInflater
            inflater.inflate(R.menu.multi_select_trace_menu, menu)
            findViewById<CoordinatorLayout>(R.id.trace_app_header).visibility = View.GONE
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
                R.id.menu_rename_trace -> {
                    createRenameTraceAlertDialog(mode)
                    true
                }
                R.id.menu_delete_traces -> {
                    createDeleteTraceAlertDialog(mode)
                    true
                }
                else -> false
            }
        }
        /** Called when the user exits the action mode. */
        override fun onDestroyActionMode(mode: ActionMode) {
            for (trace in traceList) {
                trace.isSelected = false
            }
            notifyTraceAdapter()
            findViewById<CoordinatorLayout>(R.id.trace_app_header).visibility = View.VISIBLE
            actionMode = null
        }
    }

    private fun toggleSelection(position: Int) {
        traceList[position].isSelected = !traceList[position].isSelected
        traceAdapter!!.notifyItemChanged(position)
        // edit menu title
        val total = traceList.count { it.isSelected }
        actionMode?.menu?.findItem(R.id.menu_rename_trace)?.setVisible(total == 1)
        actionMode?.title = getString(R.string.options_bar_text_num, total)
    }

    private fun createRenameTraceAlertDialog(mode: ActionMode): Boolean {
        var result = true
        val newTraceForm = View.inflate(this, R.layout.dialog_rename_trace, null)
        val newTraceTitle: TextView = newTraceForm.findViewById(R.id.rename_trace_label)
        newTraceTitle.text = getString(R.string.split_trace_label)
        val newTraceInput: EditText = newTraceForm.findViewById(R.id.rename_trace_input)
        newTraceInput.setText(getString(R.string.rename_trace_autofill_hint))
        val builder = AlertDialog.Builder(this@TraceActivity)
            .setTitle(getString(R.string.dialog_rename_trace_title))
            .setView(newTraceForm)
            .setPositiveButton(getString(R.string.dialog_positive)) { dialog, _ ->
                val renameTraceInd = traceList.indexOfFirst { it.isSelected }
                val oldTraceName = traceList[renameTraceInd].traceLabel
                val newTraceName = newTraceInput.text.toString()
                result = renameTrace(chosenPackageName!!, oldTraceName, newTraceName)
                traceList[renameTraceInd].traceLabel = newTraceName
                traceAdapter?.notifyItemChanged(renameTraceInd)
                mode.finish()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_negative)) { dialog, _ ->
                mode.finish()
                dialog.dismiss()
            }
        val deleteAlertDialog = builder.create()
        deleteAlertDialog.show()
        val positiveButton = deleteAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val oldTraceName = traceList.first { it.isSelected }.traceLabel
        positiveButton.isEnabled = !((positiveButton.text).contentEquals(oldTraceName))
        newTraceInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(str: CharSequence?, p1: Int, p2: Int, p3: Int) {
                positiveButton.isEnabled = !TextUtils.isEmpty(str) && !str.contentEquals(oldTraceName)
            }
            override fun afterTextChanged(str: Editable?) {}
        })
        return result
    }

    private fun deleteSelectedTraces(): Boolean {
        val traceIterator = traceList.iterator()
        while (traceIterator.hasNext()) {
            val traceItem = traceIterator.next()
            if (traceItem.isSelected) {
                val result = deleteTrace(chosenPackageName!!, traceItem.traceLabel)
                if (!result) {
                    return false
                }
                traceIterator.remove()
            }
        }
        return true
    }

    fun createDeleteTraceAlertDialog(mode: ActionMode): Boolean {
        var result = true
        val builder = AlertDialog.Builder(this@TraceActivity)
            .setTitle(getString(R.string.dialog_delete_trace_title))
            .setMessage(getString(R.string.dialog_delete_trace_message))
            .setPositiveButton(getString(R.string.dialog_positive)) { dialog, _ ->
                result = deleteSelectedTraces()
                notifyTraceAdapter()
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

    private fun navigateToNextActivity(traceLabel: String) {
        val intent = Intent(applicationContext, EventActivity::class.java)
        intent.putExtra("package_name", chosenPackageName)
        intent.putExtra("trace_label", traceLabel)
        startActivity(intent)
    }
}
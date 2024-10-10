package edu.illinois.odim

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
import edu.illinois.odim.LocalStorageOps.listTraces
import edu.illinois.odim.LocalStorageOps.renameTrace

// these were static in java
private var recyclerAdapter: TraceAdapter? = null
fun notifyTraceAdapter() {
    recyclerAdapter?.notifyDataSetChanged()
}

class TraceActivity : AppCompatActivity(){
    private var recyclerView: RecyclerView? = null
    private var chosenPackageName: String? = null
    private lateinit var traceList: MutableList<TraceItem>
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)
        chosenPackageName = intent.extras!!.getString("package_name")
        recyclerView = findViewById<View>(R.id.trace_recycler_view) as RecyclerView
        recyclerView?.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        traceList = listTraces(chosenPackageName!!)
        recyclerAdapter = TraceAdapter(this, chosenPackageName!!, traceList)
        // set up UI icons
        val packageManager = this.packageManager
        val traceAppNameView = findViewById<TextView>(R.id.trace_app_name)
        val appInfo = packageManager.getApplicationInfo(chosenPackageName!!, 0)
        traceAppNameView.text = packageManager.getApplicationLabel(appInfo)
        val traceAppIconView = findViewById<ImageView>(R.id.trace_app_image)
        val icon = packageManager.getApplicationIcon(chosenPackageName!!)
        traceAppIconView.setImageDrawable(icon)
        // set up recycler view
        val decoratorVertical = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        decoratorVertical.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider)!!)
        recyclerView?.addItemDecoration(decoratorVertical)
        recyclerView!!.adapter = recyclerAdapter
        // set up recycler view listeners
        recyclerAdapter!!.setOnItemLongClickListener(object : TraceAdapter.OnItemLongClickListener {
            override fun onItemLongClick(position: Int): Boolean {
                if (actionMode == null) {
                    actionMode = startActionMode(multiSelectActionModeCallback)
                }
                toggleSelection(position)
                return true
            }
        })
        recyclerAdapter!!.setOnItemClickListener(object : TraceAdapter.OnItemClickListener {
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
        traceList.addAll(listTraces(chosenPackageName!!))
        notifyTraceAdapter()
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
        recyclerAdapter!!.notifyItemChanged(position)
        // edit menu title
        var total = 0
        for (trace in traceList) {
            if (trace.isSelected) {
                total += 1
            }
        }
        actionMode?.menu?.findItem(R.id.menu_rename_trace)?.setVisible(total == 1)
        actionMode?.title = "Total: $total"
    }

    private fun createRenameTraceAlertDialog(mode: ActionMode): Boolean {
        var result = true
        val newTraceForm = View.inflate(this, R.layout.new_trace_dialog, null)
        val newTraceTitle: TextView = newTraceForm.findViewById(R.id.new_trace_label)
        newTraceTitle.text = getString(R.string.split_trace_label)
        val newTraceInput: EditText = newTraceForm.findViewById(R.id.new_trace_input)
        newTraceInput.setText(getString(R.string.split_trace_autofill_hint))
        val builder = AlertDialog.Builder(this@TraceActivity)
            .setTitle("Split Trace")
            .setView(newTraceForm)
            .setPositiveButton("Yes") { dialog, _ ->
                val renameTraceInd = traceList.indexOfFirst { it.isSelected }
                val oldTraceName = traceList[renameTraceInd].traceLabel
                val newTraceName = newTraceInput.text.toString()
                result = renameTrace(chosenPackageName!!, oldTraceName, newTraceName)
                traceList[renameTraceInd].traceLabel = newTraceName
                recyclerAdapter?.notifyItemChanged(renameTraceInd)
                mode.finish()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                mode.finish()
                dialog.dismiss()
            }
        val deleteAlertDialog = builder.show()
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
            .setTitle("Delete trace")
            .setMessage("Are you sure you want to delete this trace recording? " +
                    "You will remove the entire trace, including all screens, view hierarchies, and gestures.")
            .setPositiveButton("Yes") { dialog, _ ->
                result = deleteSelectedTraces()
                notifyTraceAdapter()
                mode.finish()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
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
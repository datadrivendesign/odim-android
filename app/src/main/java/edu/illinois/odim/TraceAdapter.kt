package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class TraceAdapter(context: Context,
                   private val packageName: String,
                   private var itemList: ArrayList<String>) : RecyclerView.Adapter<MyViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private lateinit var itemClickListener : OnItemClickListener

    interface  OnItemClickListener {
        fun onItemClick(traceName: String)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = inflater.inflate(R.layout.trace_view_row, parent, false)
        return MyViewHolder(itemView, itemClickListener)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val traceName = itemList[position]
        val events = getEvents(packageName, traceName)

        holder.itemName = traceName
        holder.traceName.setText(traceName)
//        holder.traceDateTextView.text = traceName  // if (events.size > 0) events[0].substringBefore(";") else "Empty Trace"  // should never be empty, trace created at first event
        holder.traceScreensTextView.text = this.inflater.context.getString(R.string.traceNumScreens, events.size)
        holder.editTraceButton.setOnClickListener{buttonView ->
            val currentTraceName = holder.itemName
            val newTraceName = holder.traceName.text.toString()
            editTraceName(packageName, currentTraceName, newTraceName)
            holder.itemName = holder.traceName.text.toString()
        }

    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

class MyViewHolder(itemView: View, listener: TraceAdapter.OnItemClickListener) : RecyclerView.ViewHolder(itemView)
{
    lateinit var itemName : String
    var traceName : EditText = itemView.findViewById(R.id.trace_name)
    val traceScreensTextView : TextView = itemView.findViewById(R.id.num_trace_screens)
    val editTraceButton : Button = itemView.findViewById(R.id.trace_edit_btn)

    init {
        itemView.setOnClickListener {
            listener.onItemClick(itemName)
        }
    }
}
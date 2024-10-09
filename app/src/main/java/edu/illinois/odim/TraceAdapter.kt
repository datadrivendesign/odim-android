package edu.illinois.odim

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.LocalStorageOps.listEvents


class TraceAdapter(
    context: Context,
    private val packageName: String,
    private var itemList: MutableList<TraceItem>
) : RecyclerView.Adapter<TraceAdapter.TraceViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class TraceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var traceLabel : TextView = itemView.findViewById(R.id.trace_label)
        val traceScreensTextView : TextView = itemView.findViewById(R.id.num_trace_screens)
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int): Boolean
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(position: Int): Boolean
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        itemLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraceViewHolder {
        val itemView = inflater.inflate(R.layout.trace_view_row, parent, false)
        return TraceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TraceViewHolder, position: Int) {
        val traceItem = itemList[position]
        val events = listEvents(packageName, traceItem.traceLabel)
        holder.traceScreensTextView.text = inflater.context.getString(R.string.traceNumScreens, events.size)
        holder.traceLabel.text = traceItem.traceLabel
        holder.itemView.setBackgroundColor(if (itemList[position].isSelected) Color.LTGRAY else Color.TRANSPARENT)
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(position)
        }
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.onItemLongClick(position)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}
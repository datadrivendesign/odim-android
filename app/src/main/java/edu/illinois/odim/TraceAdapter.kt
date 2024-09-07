package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.LocalStorageOps.listEvents


class TraceAdapter(
    context: Context,
    private val packageName: String,
    private var itemList: MutableList<String>
) : RecyclerView.Adapter<MyViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    interface OnItemClickListener {
        fun onItemClick(traceLabel: String)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(traceLabel: String) : Boolean
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        itemLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = inflater.inflate(R.layout.trace_view_row, parent, false)
        return MyViewHolder(itemView, itemClickListener, itemLongClickListener)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val traceLabel = itemList[position]
        val events = listEvents(packageName, traceLabel)
        holder.traceLabel.text = traceLabel
        holder.traceScreensTextView.text = inflater.context.getString(R.string.traceNumScreens, events.size)

    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

class MyViewHolder(itemView: View,
   clickListener: TraceAdapter.OnItemClickListener,
   longClickListener: TraceAdapter.OnItemLongClickListener) : RecyclerView.ViewHolder(itemView)
{
    var traceLabel : TextView = itemView.findViewById(R.id.trace_label)
    val traceScreensTextView : TextView = itemView.findViewById(R.id.num_trace_screens)

    init {
        itemView.setOnClickListener {
            clickListener.onItemClick(traceLabel.text.toString())
        }
        itemView.setOnLongClickListener {
            longClickListener.onItemLongClick(traceLabel.text.toString())
        }
    }
}
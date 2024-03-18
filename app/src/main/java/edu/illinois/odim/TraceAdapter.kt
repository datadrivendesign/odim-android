package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class TraceAdapter(context: Context,
                   private val packageName: String,
                   private var itemList: MutableList<String>) : RecyclerView.Adapter<MyViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private lateinit var itemClickListener : OnItemClickListener

    interface  OnItemClickListener {
        fun onItemClick(traceLabel: String)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = inflater.inflate(R.layout.trace_view_row, parent, false)
        return MyViewHolder(itemView, itemClickListener)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val traceLabel = itemList[position]
        val events = listEvents(packageName, traceLabel)

        holder.traceLabel.text = traceLabel
        holder.traceScreensTextView.text = this.inflater.context.getString(R.string.traceNumScreens, events.size)

    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

class MyViewHolder(itemView: View, listener: TraceAdapter.OnItemClickListener) : RecyclerView.ViewHolder(itemView)
{
    var traceLabel : TextView = itemView.findViewById(R.id.trace_label)
    val traceScreensTextView : TextView = itemView.findViewById(R.id.num_trace_screens)

    init {
        itemView.setOnClickListener {
            listener.onItemClick(traceLabel.text.toString())
        }
    }
}
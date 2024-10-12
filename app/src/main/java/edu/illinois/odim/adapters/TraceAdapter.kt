package edu.illinois.odim.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.R
import edu.illinois.odim.databinding.RecyclerRowTraceBinding
import edu.illinois.odim.dataclasses.TraceItem


class TraceAdapter(private var traceList: MutableList<TraceItem>):
    RecyclerView.Adapter<TraceAdapter.TraceViewHolder>() {
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class TraceViewHolder(private val traceItemView: RecyclerRowTraceBinding):
        RecyclerView.ViewHolder(traceItemView.root) {
        fun bindTraceItem(traceItem: TraceItem) {
            traceItemView.numTraceScreens.text = traceItemView.root.context.getString(
                R.string.traceNumScreens,
                traceItem.numEvents
            )
            traceItemView.traceLabel.text = traceItem.traceLabel
            traceItemView.root.setBackgroundColor(if (traceItem.isSelected) Color.LTGRAY else Color.TRANSPARENT)
        }
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
        val from = LayoutInflater.from(parent.context)
        val itemView = RecyclerRowTraceBinding.inflate(from, parent, false)
        return TraceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TraceViewHolder, position: Int) {
        holder.bindTraceItem(traceList[position])
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(position)
        }
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.onItemLongClick(position)
        }
    }

    override fun getItemCount(): Int { return traceList.size }
}
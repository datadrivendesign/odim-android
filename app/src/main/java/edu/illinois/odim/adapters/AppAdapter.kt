package edu.illinois.odim.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.RecyclerRowAppBinding
import edu.illinois.odim.dataclasses.AppItem


class AppAdapter(private var appList: MutableList<AppItem>):
    RecyclerView.Adapter<AppAdapter.MainViewHolder>() {
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class MainViewHolder(private var appItemView: RecyclerRowAppBinding):
        RecyclerView.ViewHolder(appItemView.root) {
        fun bindAppItem(appItem: AppItem) {
            appItemView.appName.text = appItem.appName
            appItemView.appIcon.setImageDrawable(appItem.appIcon)
            appItemView.root.setBackgroundColor(if (appItem.isSelected) Color.LTGRAY else Color.TRANSPARENT)
        }
    }

    interface  OnItemClickListener {
        fun onItemClick(position: Int)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = RecyclerRowAppBinding.inflate(from, parent, false)
        return MainViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        holder.bindAppItem(appList[position])
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(position)
        }
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.onItemLongClick(position)
        }
    }

    override fun getItemCount(): Int { return appList.size }
}
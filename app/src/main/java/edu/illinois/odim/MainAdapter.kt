package edu.illinois.odim

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.RecyclerRowMainBinding


class MainAdapter(private var appList: MutableList<MainItem>):
    RecyclerView.Adapter<MainAdapter.MainViewHolder>() {
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class MainViewHolder(private var mainItemView: RecyclerRowMainBinding):
        RecyclerView.ViewHolder(mainItemView.root) {
        fun bindAppItem(mainItem: MainItem) {
            mainItemView.mainAppName.text = mainItem.appName
            mainItemView.mainAppIcon.setImageDrawable(mainItem.appIcon)
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
        val itemView = RecyclerRowMainBinding.inflate(from, parent, false)
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
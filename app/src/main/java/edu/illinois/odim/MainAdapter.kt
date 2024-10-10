package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class MainAdapter(
    context: Context,
    itemList: MutableList<String>
) : RecyclerView.Adapter<MainAdapter.MainViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private var itemList : MutableList<String> = itemList
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class MainViewHolder(
        itemView: View,
        listener: OnItemClickListener,
        longClickListener: OnItemLongClickListener
    ) : RecyclerView.ViewHolder(itemView) {
        var appNameTextView : TextView = itemView.findViewById(R.id.main_app_name)
        var appIconImageView : ImageView = itemView.findViewById(R.id.main_app_icon)
        lateinit var appPackage : String

        init {
            itemView.setOnClickListener {
                listener.onItemClick(appPackage)
            }
            itemView.setOnLongClickListener {
                longClickListener.onItemLongClick(appPackage)
            }
        }
    }

    interface  OnItemClickListener {
        fun onItemClick(appPackage: String)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(appPackage: String) : Boolean
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        itemLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val itemView = inflater.inflate(R.layout.recycler_row_main, parent, false)
        return MainViewHolder(itemView, itemClickListener, itemLongClickListener)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        holder.appPackage = itemList[position]
        val packageManager = this.inflater.context.packageManager
        val appInfo = packageManager.getApplicationInfo(itemList[position], 0)
        holder.appNameTextView.text = packageManager.getApplicationLabel(appInfo)
        val icon = packageManager.getApplicationIcon(itemList[position])
        holder.appIconImageView.setImageDrawable(icon)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}
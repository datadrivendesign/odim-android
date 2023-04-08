package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class MainAdapter(context: Context, itemList: ArrayList<String>) : RecyclerView.Adapter<MainViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private var itemList : ArrayList<String> = itemList
    private lateinit var itemClickListener : OnItemClickListener

    interface  OnItemClickListener {
        fun onItemClick(appPackage: String)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val itemView = inflater.inflate(R.layout.main_recycler_row, parent, false)
        return MainViewHolder(itemView, itemClickListener)
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

class MainViewHolder(itemView: View, listener: MainAdapter.OnItemClickListener) : RecyclerView.ViewHolder(itemView)
{
    var appNameTextView : TextView = itemView.findViewById(R.id.main_app_name)
    var appIconImageView : ImageView = itemView.findViewById(R.id.main_app_icon)
    lateinit var appPackage : String

    init {
        itemView.setOnClickListener {
            listener.onItemClick(appPackage)
        }
    }
}
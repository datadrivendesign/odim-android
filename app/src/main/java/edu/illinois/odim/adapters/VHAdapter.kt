package edu.illinois.odim.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.RecyclerRowVhBinding
import edu.illinois.odim.dataclasses.VHItem

class VHAdapter(vhItems: List<VHItem>): RecyclerView.Adapter<VHAdapter.VHViewHolder>() {
    private var vhList: List<VHItem> = vhItems
    private lateinit var itemClickListener : OnItemClickListener
    class VHViewHolder(private val vhItemBinding: RecyclerRowVhBinding):
        RecyclerView.ViewHolder(vhItemBinding.vhItemCardView.rootView) {
        fun bindVHItem(vhItem: VHItem) {
            vhItemBinding.vhText.text = vhItem.text
            vhItemBinding.vhContentDesc.text = vhItem.contentDesc
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int, vhItem: VHItem): Boolean
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VHViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = RecyclerRowVhBinding.inflate(from, parent, false)
        return VHViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: VHViewHolder, position: Int) {
        holder.bindVHItem(vhList[position])
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(position, vhList[position])
        }
    }

    override fun getItemCount(): Int {
        return vhList.size
    }
}
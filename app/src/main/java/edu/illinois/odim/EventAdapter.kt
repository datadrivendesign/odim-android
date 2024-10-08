package edu.illinois.odim

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.CardCellBinding
import java.util.Locale

class EventAdapter(
    screenPreviews: List<ScreenShotPreview>
) : RecyclerView.Adapter<EventAdapter.DetailViewHolder>() {
    private var itemList : List<ScreenShotPreview> = screenPreviews
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class DetailViewHolder(val cardBinding: CardCellBinding): RecyclerView.ViewHolder(cardBinding.cardView.rootView) {
        fun bindScreenshotPreview(ssp: ScreenShotPreview) {
            cardBinding.cover.setImageBitmap(ssp.screenShot)
            if (!ssp.isComplete) {
                cardBinding.incompleteIndicator.visibility = View.VISIBLE
            } else {
                cardBinding.incompleteIndicator.visibility = View.INVISIBLE
            }
            cardBinding.time.text = ssp.timestamp
            cardBinding.event.text = ssp.event
        }

        fun bindEventIndex(ind: Int) {
            cardBinding.index.text = String.format(Locale.getDefault(), "%d", ind)
        }
    }

    interface OnItemClickListener {
        fun onItemClick(cardView: CardCellBinding): Boolean
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = CardCellBinding.inflate(from, parent, false)
        return DetailViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bindScreenshotPreview(itemList[position])
        holder.bindEventIndex(position)
        holder.itemView.setBackgroundColor(if (itemList[position].isSelected) Color.LTGRAY else Color.TRANSPARENT)
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.onItemLongClick(position)
        }
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(holder.cardBinding)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}
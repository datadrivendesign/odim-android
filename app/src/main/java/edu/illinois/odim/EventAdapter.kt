package edu.illinois.odim

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.RecyclerRowEventBinding
import java.util.Locale

class EventAdapter(
    screenPreviews: List<ScreenShotPreview>
) : RecyclerView.Adapter<EventAdapter.DetailViewHolder>() {
    private var screenList: List<ScreenShotPreview> = screenPreviews
    private lateinit var itemClickListener: OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    class DetailViewHolder(private val eventRowBinding: RecyclerRowEventBinding):
        RecyclerView.ViewHolder(eventRowBinding.eventCardView.rootView) {
        fun bindScreenshotPreview(ssp: ScreenShotPreview) {
            eventRowBinding.cover.setImageBitmap(ssp.screenShot)
            if (!ssp.isComplete) {
                eventRowBinding.incompleteIndicator.visibility = View.VISIBLE
            } else {
                eventRowBinding.incompleteIndicator.visibility = View.INVISIBLE
            }
            eventRowBinding.time.text = ssp.timestamp
            eventRowBinding.event.text = ssp.event
        }

        fun bindEventIndex(ind: Int) {
            eventRowBinding.index.text = String.format(Locale.getDefault(), "%d", ind)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = RecyclerRowEventBinding.inflate(from, parent, false)
        return DetailViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bindScreenshotPreview(screenList[position])
        holder.bindEventIndex(position)
        holder.itemView.setBackgroundColor(if (screenList[position].isSelected) Color.LTGRAY else Color.TRANSPARENT)
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.onItemLongClick(position)
        }
        holder.itemView.setOnClickListener {
            itemClickListener.onItemClick(position)
        }
    }

    override fun getItemCount(): Int {
        return screenList.size
    }
}
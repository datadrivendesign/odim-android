package edu.illinois.odim

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.CardCellBinding

class EventAdapter(screenPreviews: ArrayList<ScreenShotPreview>) : RecyclerView.Adapter<DetailViewHolder>() {
    private var itemList : ArrayList<ScreenShotPreview> = screenPreviews
    private lateinit var itemClickListener : OnItemClickListener
    private lateinit var itemLongClickListener: OnItemLongClickListener

    interface OnItemClickListener {
        fun onItemClick(cardView: CardCellBinding)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(cardView: CardCellBinding) : Boolean
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        itemLongClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = CardCellBinding.inflate(from, parent, false)
        return DetailViewHolder(itemView, itemClickListener, itemLongClickListener)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bindScreenshotPreview(itemList[position])
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

class DetailViewHolder(
    private val cardBinding : CardCellBinding,
    clickListener: EventAdapter.OnItemClickListener,
    longClickListener: EventAdapter.OnItemLongClickListener) : RecyclerView.ViewHolder(cardBinding.cardView.rootView)
{
    init {
        itemView.setOnClickListener {
            clickListener.onItemClick(cardBinding)
        }
        itemView.setOnLongClickListener {
            longClickListener.onItemLongClick(cardBinding)
        }
    }

    fun bindScreenshotPreview(ssp: ScreenShotPreview) {
        cardBinding.cover.setImageBitmap(ssp.screenShot)
        cardBinding.time.text = ssp.timestamp
        cardBinding.event.text = ssp.event
    }
}
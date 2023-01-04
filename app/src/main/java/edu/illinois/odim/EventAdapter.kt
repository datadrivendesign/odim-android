package edu.illinois.odim

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.databinding.CardCellBinding

class EventAdapter(context: Context, itemList: ArrayList<ScreenShotPreview>) : RecyclerView.Adapter<DetailViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context)
    private var itemList : ArrayList<ScreenShotPreview> = itemList
    private lateinit var itemClickListener : OnItemClickListener

    interface  OnItemClickListener {
        fun onItemClick(cardView: CardCellBinding)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val from = LayoutInflater.from(parent.context)
        val itemView = CardCellBinding.inflate(from, parent, false)
        return DetailViewHolder(itemView, itemClickListener)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bindScreenshotPreview(itemList[position])
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}

class DetailViewHolder(
    private val cardView : CardCellBinding,
    private val listener: EventAdapter.OnItemClickListener) : RecyclerView.ViewHolder(cardView.root)
{
    init {
        itemView.setOnClickListener {
            listener.onItemClick(cardView)
        }
    }

    fun bindScreenshotPreview(ssp: ScreenShotPreview) {
        cardView.cover.setImageBitmap(ssp.screenShot)
        cardView.time.text = ssp.timestamp
        cardView.event.text = ssp.event
    }
}
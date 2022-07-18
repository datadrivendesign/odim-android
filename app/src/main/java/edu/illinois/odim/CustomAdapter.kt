import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.R


class CustomAdapter(context: Context, itemList: ArrayList<String>) : RecyclerView.Adapter<MyViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context);
    private var itemList : ArrayList<String> = itemList;
    private lateinit var itemClickListener : OnItemClickListener

    interface OnItemClickListener {
        fun onItemClick(view: View)//parent: AdapterView<*>?, view: View, position: Int, id: Long)
    }

    fun setOnItemClickListener(listener : OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = inflater.inflate(R.layout.recycler_view_row, parent, false);
        val holder = MyViewHolder(view, itemClickListener);
        return holder;
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textView.setText(itemList.get(position));
    }

    override fun getItemCount(): Int {
        return itemList.size;
    }
}

class MyViewHolder(textView: View, listener: CustomAdapter.OnItemClickListener) : RecyclerView.ViewHolder(textView)
{
    var textView : TextView = itemView.findViewById(R.id.packageName);

    init {
        itemView.setOnClickListener {
            listener.onItemClick(textView)
        }
    }
}
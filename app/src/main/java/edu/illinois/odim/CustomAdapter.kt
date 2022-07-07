import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.R


class CustomAdapter(context: Context, packageList: ArrayList<String>) : RecyclerView.Adapter<MyViewHolder>() {
    private var inflater : LayoutInflater = LayoutInflater.from(context);
    private var packageList = packageList;

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = inflater.inflate(R.layout.recycler_view_row, parent, false);
        val holder = MyViewHolder(view);
        return holder;
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textView.setText(packageList.get(position));
    }

    override fun getItemCount(): Int {
        return packageList.size;
    }
}

class MyViewHolder(textView: View) : RecyclerView.ViewHolder(textView)
{
    var textView : TextView = itemView.findViewById(R.id.packageName);
}
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.illinois.odim.R


class CustomAdapter(private val context: Context, private val arrayAdapter: ArrayAdapter<String>) :
    RecyclerView.Adapter<CustomAdapter.MyViewHolder>() {
    private val inflater : LayoutInflater = LayoutInflater.from(context);

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        var view = inflater.inflate(R.layout.custom_layout, parent, false);
        var holder = MyViewHolder(view);
        return holder;    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.serial_number.setText(<your string array[position]>);
    }

    override fun getItemCount(): Int {
        return <size of your string array list>;
    }

    class MyViewHolder : RecyclerView.ViewHolder
    {
        TextView serial_number;

        public MyViewHolder(View itemView) {
            super(itemView);
            serial_number = (TextView)itemView.findViewById(R.id.serialNo_CL);
        }
    }
}
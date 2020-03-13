package edu.illinois.recordingservice;

import android.content.Intent;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private  ListView listView;
    private static ArrayAdapter<String> arrayAdapter;
    private static ArrayList<String> list_of_package = new ArrayList<>();
    private static  Set<String> package_set = new HashSet<String>();

    private static Map<String, ArrayList<String>> package_eventType_map = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle(" Packages");

        listView = (ListView) findViewById(R.id.packageListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,list_of_package);

        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(),TraceActivity.class);
                String package_name = ((TextView)view).getText().toString();
                intent.putExtra("package_name", package_name);
                intent.putStringArrayListExtra("eventType_list", package_eventType_map.get(package_name));
//                intent.putExtra("events", (Parcelable) package_event_map.get(eventName));
                startActivity(intent);
            }
        });
    }

    public static void add_package(String packageName) {

        package_set.add(packageName);

        list_of_package.clear();
        list_of_package.addAll(package_set);

        if (arrayAdapter != null) {
            arrayAdapter.notifyDataSetChanged();
        }
        if(package_eventType_map.get(packageName) == null) {
            package_eventType_map.put(packageName, new ArrayList<String>());

        }
    }

    public static void add_event(String package_name, String eventType) {
        package_eventType_map.get(package_name).add(eventType);
    }

}

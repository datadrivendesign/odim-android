package edu.illinois.recordingservice;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class TraceActivity extends AppCompatActivity {

    private ListView listView;
    private String package_name;
    private ArrayList<String> eventType_list;
    private static ArrayAdapter<String> arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trace);

        package_name = getIntent().getExtras().get("package_name").toString();
        eventType_list = getIntent().getStringArrayListExtra("eventType_list");

        setTitle(" Package Name: " + package_name);

        listView = (ListView) findViewById(R.id.eventListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, eventType_list);

        listView.setAdapter(arrayAdapter);

    }

}

package edu.illinois.recordingservice;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ViewHierarchyActivity extends AppCompatActivity {
    private ListView listView;
    private String package_name;
    private String trace_name;
    private String event_name;
    private static ArrayAdapter<String> arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_hierarchy);

        package_name = getIntent().getExtras().get("package_name").toString();
        trace_name = getIntent().getExtras().get("trace_name").toString();
        event_name = getIntent().getExtras().get("event_name").toString();

        setTitle("View Hierarchy");

        listView = (ListView) findViewById(R.id.vhListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, MyAccessibilityService.get_vh(package_name, trace_name, event_name));

        listView.setAdapter(arrayAdapter);

    }

    public static void notifyVHAdapter() {
        if (arrayAdapter != null) {
            arrayAdapter.notifyDataSetChanged();
        }
    }
}

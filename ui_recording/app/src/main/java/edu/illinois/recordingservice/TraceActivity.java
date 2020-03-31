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

public class TraceActivity extends AppCompatActivity {

    private ListView listView;
    private String package_name;
    private static ArrayAdapter<String> arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trace);

        package_name = getIntent().getExtras().get("package_name").toString();

        setTitle(package_name);

        listView = (ListView) findViewById(R.id.traceListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, MyAccessibilityService.get_traces(package_name));

        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(),EventActivity.class);
                String trace_name = ((TextView)view).getText().toString();
                intent.putExtra("package_name", package_name);
                intent.putExtra("trace_name", trace_name);
                startActivity(intent);
            }
        });

    }

    public static void notifyTraceAdapter() {

        if (arrayAdapter != null) {
            arrayAdapter.notifyDataSetChanged();
        }

    }


}
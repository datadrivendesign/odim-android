package edu.illinois.recordingservice;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class EventActivity extends AppCompatActivity {

    private ListView listView;
    private String package_name;
    private String trace_name;
    private static ArrayAdapter<String> arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event);

        package_name = getIntent().getExtras().get("package_name").toString();
        trace_name = getIntent().getExtras().get("trace_name").toString();


        setTitle(package_name + ": " + trace_name);

        listView = (ListView) findViewById(R.id.eventListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, MyAccessibilityService.get_events(package_name, trace_name));

        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(),ScreenShotActivity.class);
                String event_name = ((TextView)view).getText().toString();
                intent.putExtra("package_name", package_name);
                intent.putExtra("trace_name", trace_name);
                intent.putExtra("event_name", event_name);
                startActivity(intent);
            }
        });

    }

    public static void notifyEventAdapter() {
        if (arrayAdapter != null) {
            arrayAdapter.notifyDataSetChanged();
        }
    }

}

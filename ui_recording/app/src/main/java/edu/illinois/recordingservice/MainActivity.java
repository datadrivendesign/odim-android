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

    private ListView listView;
    private static ArrayAdapter<String> arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle(" Packages");

        listView = (ListView) findViewById(R.id.packageListView);

        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,MyAccessibilityService.get_packages());

        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(),TraceActivity.class);
                String package_name = ((TextView)view).getText().toString();
                intent.putExtra("package_name", package_name);
                startActivity(intent);
            }
        });
    }

}

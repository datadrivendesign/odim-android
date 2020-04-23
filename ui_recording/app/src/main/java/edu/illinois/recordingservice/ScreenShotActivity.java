package edu.illinois.recordingservice;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import static android.graphics.Bitmap.Config.ARGB_8888;

public class ScreenShotActivity extends AppCompatActivity {
    private ImageView imageView;
    private String package_name;
    private String trace_name;
    private String event_name;
    private Canvas canvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screenshot);

        package_name = getIntent().getExtras().get("package_name").toString();
        trace_name = getIntent().getExtras().get("trace_name").toString();
        event_name = getIntent().getExtras().get("event_name").toString();

        setTitle("ScreenShot (Click Image for VH)");

        imageView = (ImageView) findViewById(R.id.screenshot);

        ScreenShot screenshot = MyAccessibilityService.get_screenshot(package_name, trace_name, event_name);
        if (screenshot != null) {

            Bitmap myBit = screenshot.getBitmap().copy(ARGB_8888, true);

            canvas = new Canvas(myBit);

            Rect rect = screenshot.getRect();

            if (screenshot.getAction_type() == ScreenShot.TYPE_CLICK) {
                Paint paint = new Paint();
                paint.setColor(Color.rgb(255, 165, 0));
                paint.setAlpha(100);
                canvas.drawCircle(rect.centerX(), rect.centerY(), (float) ((rect.height() + rect.width()) * 0.25), paint);
            } else if (screenshot.getAction_type() == ScreenShot.TYPE_SCROLL) {
                Paint paint = new Paint();
                paint.setColor(Color.rgb(255, 165, 0));
                paint.setAlpha(100);
                canvas.drawOval(rect.centerX()-50, rect.centerY()-100, rect.centerX()+50, rect.centerY()+100, paint);
            }  else if (screenshot.getAction_type() == ScreenShot.TYPE_LONG_CLICK) {
                Paint paint = new Paint();
                paint.setColor(Color.rgb(0, 165, 255));
                paint.setAlpha(100);
                canvas.drawCircle(rect.centerX(), rect.centerY(), (float) ((rect.height() + rect.width()) * 0.25), paint);
            }


            imageView.setImageBitmap(myBit);

//            imageView.setImageBitmap(screenshot.getBitmap());

//        imageView.setOnClickListener(new ImageView.OnClickListener() {
//            @Override
//            public void onClick(AdapterView<?> parent, View view, int position, long id) {
//                Intent intent = new Intent(getApplicationContext(),ScreenShotActivity.class);
//                startActivity(intent);
//            }
//        });

            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getApplicationContext(),ViewHierarchyActivity.class);
                    intent.putExtra("package_name", package_name);
                    intent.putExtra("trace_name", trace_name);
                    intent.putExtra("event_name", event_name);
                    startActivity(intent);
                }
            });
        }

    }

}


//package edu.illinois.recordingservice;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
//import android.view.View;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.ImageView;
//import android.widget.ListView;
//import android.widget.TextView;
//
//public class ScreenShotActivity extends AppCompatActivity {
//    private ImageView imageView;
////    private String package_name;
////    private String trace_name;
////    private String event_name;
////    private static ArrayAdapter<String> arrayAdapter;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_screenshot);
//
////        package_name = getIntent().getExtras().get("package_name").toString();
////        trace_name = getIntent().getExtras().get("trace_name").toString();
////        event_name = getIntent().getExtras().get("event_name").toString();
//
//        setTitle("ScreenShot");
//
//        imageView = (ImageView) findViewById(R.id.screenshot);
//
//        imageView.setImageBitmap(MyAccessibilityService.bitmap);
//
//    }
//}
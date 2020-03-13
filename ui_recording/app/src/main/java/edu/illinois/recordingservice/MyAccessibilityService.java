package edu.illinois.recordingservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;


import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static android.view.accessibility.AccessibilityEvent.eventTypeToString;

public class MyAccessibilityService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        // Create an overlay and display the action bar
        System.out.println("onServiceConnected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.eventTypes=AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 100;
        info.packageNames = null;
        setServiceInfo(info);


    }

//    Consumer<Bitmap> innerConsumer = new Consumer<Bitmap>() {
//        @Override
//        public void accept(Bitmap bitmap) {
//
//            Log.i("Hello", bitmap.toString());
//
//        }
//    };

//    private void takeScreenshot(Window window) {
//        Date now = new Date();
//        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
//
//        try {
//            // image naming and path  to include sd card  appending name you choose for file
//            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpg";
//
//            // create bitmap screen capture
//            View v1 = window.getDecorView().getRootView();
//            v1.setDrawingCacheEnabled(true);
//            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
//            v1.setDrawingCacheEnabled(false);
//
//            File imageFile = new File(mPath);
//
//            FileOutputStream outputStream = new FileOutputStream(imageFile);
//            int quality = 100;
//            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
//            outputStream.flush();
//            outputStream.close();
//
//            openScreenshot(imageFile);
//        } catch (Throwable e) {
//            // Several error may come out with file handling or DOM
//            e.printStackTrace();
//        }
//    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {

            //MainActivity.add_content(packageName);

//            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
//                    event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
//            }

            //Window window =
            String packageName = event.getPackageName().toString();
            if (packageName != null && !event.getPackageName().equals("edu.illinois.finalproject") &&
                    ((event.getEventType()== AccessibilityEvent.TYPE_VIEW_CLICKED)   ||
                    (event.getEventType()== AccessibilityEvent.TYPE_VIEW_CLICKED)   ||
                    (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SELECTED)  ||
                    (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SCROLLED)  ||
                    (event.getEventType()== AccessibilityEvent.TYPE_VIEW_FOCUSED))  ) {
                Date date = new Date(event.getEventTime());
                SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                String dateFormatted = formatter.format(date);

                String eventTime = "Event Time: " + dateFormatted;
                String eventType = "Event Type: " + eventTypeToString(event.getEventType());
                Log.i("Time + Type", eventTime + "; " + eventType);
                Log.i("Package Name: ", packageName);

                AccessibilityNodeInfo nodeInfo = event.getSource();
                if (nodeInfo != null) {
                    Log.i("View Hierachy:", nodeInfo.toString());
                }

                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    performGlobalAction(9);
                }

                MainActivity.add_package(packageName);

                MainActivity.add_event(packageName, eventTime + "; " + eventType);
            }

        }

    }

    @Override
    public void onInterrupt() {

    }

}


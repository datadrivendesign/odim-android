package edu.illinois.recordingservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
//import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;


import com.amplifyframework.AmplifyException;
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin;
import com.amplifyframework.core.Amplify;
import com.amplifyframework.storage.s3.AWSS3StoragePlugin;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;

import static android.graphics.Bitmap.wrapHardwareBuffer;
import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.accessibility.AccessibilityEvent.eventTypeToString;

public class MyAccessibilityService extends AccessibilityService {

    private String last_package_name;

    private static ArrayList<String> package_list = new ArrayList<>();
    private static Set<String> package_set = new HashSet<String>();

    private static Layer package_layer = new Layer();

    private Bitmap current_bitmap;

    private ScreenShot current_screenshot;

    private int forward = 0;
    private int next = 0;

    @Override
    protected void onServiceConnected() {
        // Create the service
        System.out.println("onServiceConnected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.eventTypes=AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 100;
        info.packageNames = null;
        setServiceInfo(info);

        try {
            // Add these lines to add the AWSCognitoAuthPlugin and AWSS3StoragePlugin plugins
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.addPlugin(new AWSS3StoragePlugin());
            Amplify.configure(getApplicationContext());

            Log.i("MyAmplifyApp", "Initialized Amplify");
        } catch (AmplifyException error) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error);
        }

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        if (packageName == null || event.getPackageName().equals("edu.illinois.recordingservice")) {
            return;
        }

        boolean isNewTrace = false;

        if (last_package_name == null || !packageName.equals(last_package_name)) {
            isNewTrace = true;
        }

        if ((event.getEventType()== AccessibilityEvent.TYPE_VIEW_CLICKED)
//                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SCROLLED)
//                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
//                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {

//            || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SCROLLED)

            // Parse event description
            Date date = new Date(event.getEventTime());
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateFormatted = formatter.format(date);

            String eventTime = "Event Time: " + dateFormatted;
            String eventType = "Event Type: " + eventTypeToString(event.getEventType());

            String eventDescription =  eventTime + "; " + eventType;

            Log.i("KKK", eventType);


            // Screenshot
            int action_type;
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                action_type = ScreenShot.TYPE_CLICK;
            } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                action_type = ScreenShot.TYPE_SCROLL;
            } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                action_type = ScreenShot.TYPE_LONG_CLICK;
            } else {
                action_type = ScreenShot.TYPE_SELECT;
            }

            while (forward != next) {
                Log.i("AAA", String.valueOf(forward) + "!!!" + String.valueOf(next));
            }
            Log.i("AAA", "Finally out!!! Yeah!!!");
            forward++;

            Consumer<ScreenshotResult> consumer = new Consumer<ScreenshotResult>() {
                @Override
                public void accept(ScreenshotResult screenshotResult) {
                    current_bitmap = wrapHardwareBuffer(screenshotResult.getHardwareBuffer(), screenshotResult.getColorSpace());
                    next++;
                }
            };

            Boolean hasTakenScreenShot = takeScreenshot(DEFAULT_DISPLAY, THREAD_POOL_EXECUTOR, consumer);
//            try {
//                THREAD_POOL_EXECUTOR.wait();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            Log.i("Screenshot", String.valueOf(hasTakenScreenShot));

            while (forward != next) {
                Log.i("BBB", String.valueOf(forward) + "!!!" + String.valueOf(next));
            }
            Log.i("BBB", "Finally out!!! Yeah!!!");

            // parse view hierarchy
            // current node
            AccessibilityNodeInfo node = event.getSource();
            if (node == null) {
               return;
            }

            Rect outbounds = new Rect();
            node.getBoundsInScreen(outbounds);

            AccessibilityNodeInfo root = getRootInActiveWindow();
            ArrayList<Rect> boxes = new ArrayList<Rect>();

            if (root != null) {
                boxes.addAll(get_boxes(root));
            }

            current_screenshot = new ScreenShot(current_bitmap, outbounds, action_type, boxes);


            // VH
//            String json = parse_vh_to_json(node);
//
//            String vh_currentnode = "Current Node: " + "\n" + json + "\n";

            // all nodes
            AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
            String vh = "";
            if (rootInActiveWindow != null) {
                vh = parse_vh_to_json(rootInActiveWindow);
            }

            // add the event
            add_event(packageName, isNewTrace, eventDescription, current_screenshot, vh);
            last_package_name = packageName;

        }

    }

    private void uploadFile(String packageId, String traceId, String gestureId, String gestureDescription, String vh_content, Bitmap bitmap) {

        // upload VH
        File vhFile = new File(getApplicationContext().getFilesDir(), "vh");
        String user_id = "test_user04";
        String base_location = user_id + "/" + packageId + "/" + traceId + "/" + gestureId + "/";
        String vh_location = base_location + "view_hierarchy";

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(vhFile));
            writer.append(vh_content);
            writer.close();
        } catch (Exception exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception);
        }

        Amplify.Storage.uploadFile(
                vh_location,
                vhFile,
                result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()),
                storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure)
        );

        // upload screenshot
        File screenshotFile = new File(getApplicationContext().getFilesDir(), "screenshot");
        String screenshot_location = base_location + "screenshot";

        try (FileOutputStream out = new FileOutputStream(screenshotFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (IOException e) {
            e.printStackTrace();
        }

        Amplify.Storage.uploadFile(
                screenshot_location,
                screenshotFile,
                result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()),
                storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure)
        );

        // upload gesture description
        File gestureFile = new File(getApplicationContext().getFilesDir(), "vh");
        String gesture_location = base_location + "gesture_description";

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(gestureFile));
            writer.append(gestureDescription);
            writer.close();
        } catch (Exception exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception);
        }

        Amplify.Storage.uploadFile(
                gesture_location,
                gestureFile,
                result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()),
                storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure)
        );
    }


    private ArrayList<Rect> get_boxes(AccessibilityNodeInfo node) {
        ArrayList<Rect> boxes = new ArrayList<Rect>();
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        boxes.add(rect);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo current_node = node.getChild(i);
            if (current_node != null) {
                boxes.addAll(get_boxes(current_node));
            }
        }
        Log.i("Box size", String.valueOf(boxes.size()));
        return boxes;
    }


    private String parse_vh_to_json(AccessibilityNodeInfo node) {
        Map<String,String> map = new HashMap<>();

        map.put("package_name", node.getPackageName().toString());
        map.put("class_name", node.getClassName().toString());

        map.put("scrollable", String.valueOf(node.isScrollable()));
        if (node.getParent() != null) {
            CharSequence cs = node.getParent().getClassName();
            if (cs != null) {
                map.put("parent", node.getParent().getClassName().toString());
            }
        } else {
            map.put("parent", "none");
        }
        map.put("clickable", String.valueOf(node.isClickable()));
        map.put("focusable", String.valueOf(node.isFocusable()));
        map.put("long-clickable", String.valueOf(node.isLongClickable()));
        map.put("enabled", String.valueOf(node.isEnabled()));

        Rect outbounds = new Rect();
        node.getBoundsInScreen(outbounds);
        map.put("bounds_in_screen", outbounds.toString());

        map.put("visibility", String.valueOf(node.isVisibleToUser()));
        if (node.getContentDescription() != null) {
            map.put("content-desc", node.getContentDescription().toString());
        } else {
            map.put("content-desc", "none");
        }

        node.getBoundsInParent(outbounds);
        map.put("bounds_in_parent", outbounds.toString());

        map.put("focused", String.valueOf(node.isFocused()));
        map.put("selected", String.valueOf(node.isSelected()));

        map.put("children_count", String.valueOf(node.getChildCount()));

        map.put( "checkable", String.valueOf(node.isCheckable()));
        map.put( "checked", String.valueOf(node.isChecked()));

        CharSequence text = node.getText();
        if (text != null) {
            map.put("text_field", text.toString());
        }

        String children_vh = "[";

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo current_node = node.getChild(i);
            if (current_node != null) {
                children_vh += parse_vh_to_json(current_node) + ",";
            }

        }
        children_vh += "]";


        Log.i("VH!!!!!!!", children_vh);



        map.put("children", children_vh);

        //map.put("to_string", node.toString());

        Gson gson = new Gson();
        String json = gson.toJson(map);

        json = json.replaceAll("\\\\","");

        return json;
    }


//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//    private String parseAllNodeVH(AccessibilityNodeInfo root) {
//        String vh = "";
//        Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
//        deque.add(root);
//        while (deque != null && !deque.isEmpty()) {
//            AccessibilityNodeInfo node = deque.removeFirst();
//            if (node != null) {
//                vh = vh + parse_vh_to_json(node) + "\n" + "\n";
////                Log.i("Oppps", String.valueOf(node.getChildCount()));
//                for (int i = 0; i < node.getChildCount(); i++) {
//                    AccessibilityNodeInfo current_node = node.getChild(i);
//                    if (current_node != null) {
//                        deque.addLast(current_node);
//                    }
//                }
//            }
//        }
//        return vh;
//    }


    private void add_event(String packageName, boolean isNewTrace, String eventDescription, ScreenShot currentScreenShot, String viewHierachy) {

        // add the package
        HashMap<String, Layer> package_map = package_layer.getMap();
        if (!package_map.containsKey(packageName)) {
            // this is a new package
            package_set.add(packageName);
            package_list.clear();
            package_list.addAll(package_set);
            package_layer.setList(package_list);

            package_map.put(packageName, new Layer());
            package_layer.setMap(package_map);
        }

        MainActivity.notifyPackageAdapter();

        // add the trace
        String traceName;
        Layer trace_layer = package_map.get(packageName);
        if (trace_layer == null) {
            return;
        }
        HashMap<String, Layer> trace_map = trace_layer.getMap();
        ArrayList<String> trace_list = trace_layer.getList();
        if (isNewTrace) {
            // this is a new trace
            traceName = "Trace " + (trace_list.size() + 1);
            trace_list.add(traceName);
            trace_layer.setList(trace_list);
            trace_map.put(traceName, new Layer());
            trace_layer.setMap(trace_map);
        } else {
            traceName = "Trace " + trace_list.size();
        }

        TraceActivity.notifyTraceAdapter();


        // add the event
        Layer event_layer = trace_map.get(traceName);
        if (event_layer == null) {
            return;
        }
        ArrayList<String> event_list = event_layer.getList();
        event_list.add(eventDescription);
        event_layer.setList(event_list);

        HashMap<String, Layer> event_map = event_layer.getMap();
        event_map.put(eventDescription, new Layer());
        event_layer.setMap(event_map);

//        Log.i("Oppps", event_layer.getList().get(0));
//        Log.i("Oppps", Boolean.toString(event_layer.getMap().containsKey(eventDescription)));

        EventActivity.notifyEventAdapter();

        // add the screenshot
        Layer screenshot_layer = event_map.get(eventDescription);
        if (screenshot_layer == null) {
            return;
        }

        screenshot_layer.setScreenShot(currentScreenShot);

        HashMap<String, Layer> screenshot_map = screenshot_layer.getMap();
        screenshot_map.put(eventDescription, new Layer());
        screenshot_layer.setMap(screenshot_map);

        // add the view hierarchy
        Layer view_hierarchy_layer = screenshot_map.get(eventDescription);
        if (view_hierarchy_layer == null) {
            return;
        }
        ArrayList<String> view_hierarchy_list = new ArrayList<String>();
        view_hierarchy_list.add(viewHierachy);
        view_hierarchy_layer.setList(view_hierarchy_list);

        ViewHierarchyActivity.notifyVHAdapter();

        uploadFile(packageName, traceName, eventDescription, eventDescription, viewHierachy, currentScreenShot.getBitmap());

    }

    public static ArrayList<String> get_packages() {
        return package_list;
    }

    public static ArrayList<String> get_traces(String package_name) {
        return package_layer.getMap().get(package_name).getList();
    }

    public static ArrayList<String> get_events(String package_name, String trace_name) {
        return package_layer.getMap().get(package_name).getMap().get(trace_name).getList();
    }

    public static ScreenShot get_screenshot(String package_name, String trace_name, String event_name) {
        return package_layer.getMap().get(package_name).getMap().get(trace_name).getMap().get(event_name).getScreenShot();
    }

    public static ArrayList<String> get_vh(String package_name, String trace_name, String event_name) {
        return package_layer.getMap().get(package_name).getMap().get(trace_name).getMap().get(event_name).getMap().get(event_name).getList();
    }




    @Override
    public void onInterrupt() {

    }

}


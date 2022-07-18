package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.AsyncTask.THREAD_POOL_EXECUTOR
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Consumer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

fun get_packages(): ArrayList<String>? {
    return MyAccessibilityService.package_list
}

fun get_traces(package_name: String?): ArrayList<String> {
    return MyAccessibilityService.package_layer.map[package_name]!!.list
}

fun get_events(package_name: String?, trace_name: String?): ArrayList<String> {
    return MyAccessibilityService.package_layer.map[package_name]!!.map[trace_name]!!.list
}

fun get_screenshot(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ScreenShot? {
    return MyAccessibilityService.package_layer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.screenShot
}

fun get_vh(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ArrayList<String> {
    return MyAccessibilityService.package_layer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.map[event_name]!!.list
}

class MyAccessibilityService : AccessibilityService() {

    private var last_package_name: String? = null

    companion object {
        val package_list: ArrayList<String> = ArrayList()
        val package_set: MutableSet<String> = HashSet()

        val package_layer = Layer()
    }


    private var current_bitmap: Bitmap? = null

    private var current_screenshot: ScreenShot? = null

    private var forward = 0
    private var next = 0

    private var gestures_map: HashMap<String, String>? = null

    private val user_id = "test_user15"



    override fun onServiceConnected() {
        // Create the service
        println("onServiceConnected")
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.notificationTimeout = 100
        info.packageNames = null
        serviceInfo = info
        try {
            // Add these lines to add the AWSCognitoAuthPlugin and AWSS3StoragePlugin plugins
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSS3StoragePlugin())
            Amplify.configure(applicationContext)
            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: AmplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
        gestures_map = HashMap()

//        Amplify.Auth.signUp(
//                "zhilin",
//                "Password123",
//                AuthSignUpOptions.builder().userAttribute(AuthUserAttributeKey.email(), "zhilinz2@illinois.edu").build(),
//                result -> Log.i("AuthQuickStart", "Result: " + result.toString()),
//                error -> Log.e("AuthQuickStart", "Sign up failed", error)
//        );

//        Amplify.Auth.confirmSignUp(
//                "zhilin",
//                "061319",
//                result -> Log.i("AuthQuickstart", result.isSignUpComplete() ? "Confirm signUp succeeded" : "Confirm sign up not complete"),
//                error -> Log.e("AuthQuickstart", error.toString())
//        );
        Amplify.Auth.signIn(
            "zhilin",
            "Password123",
            { result ->
                Log.i(
                    "AuthQuickstart",
                    if (result.isSignInComplete()) "Sign in succeeded" else "Sign in not complete"
                )
            }
        ) { error -> Log.e("AuthQuickstart", error.toString()) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val DEBUG_TAG = "NetworkStatusExample"
        val connMgr: ConnectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isWifiConn = false
        var isMobileConn = false
        for (network in connMgr.getAllNetworks()) {
            val networkInfo: NetworkInfo? = connMgr.getNetworkInfo(network)
            if (networkInfo != null) {
                if (networkInfo.getType() === ConnectivityManager.TYPE_WIFI) {
                    isWifiConn = isWifiConn or networkInfo!!.isConnected()
                }
            }
            if (networkInfo != null) {
                if (networkInfo.getType() === ConnectivityManager.TYPE_MOBILE) {
                    isMobileConn = isMobileConn or networkInfo!!.isConnected()
                }
            }
        }
        Log.d(DEBUG_TAG, "Wifi connected: $isWifiConn")
        Log.d(DEBUG_TAG, "Mobile connected: $isMobileConn")
        if (!isWifiConn) {
            return
        }
        if (event == null || event.packageName == null) {
            return
        }
        val packageName = event.packageName.toString()
        if (packageName == null || event.packageName == "edu.illinois.recordingservice") {
            return
        }
        var isNewTrace = false
        if (last_package_name == null || packageName != last_package_name) {
            isNewTrace = true
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED //                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SCROLLED)
        //                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
        //                || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {

//            || (event.getEventType()== AccessibilityEvent.TYPE_VIEW_SCROLLED)

            // Parse event description
            val date = Date(event.eventTime)
            val formatter = SimpleDateFormat("HH:mm:ss.SSS")
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
            val dateFormatted: String = formatter.format(date)
            val eventTime = "Event Time: $dateFormatted"
            val eventType = "Event Type: " + eventTypeToString(event.eventType)
            val eventDescription = "$eventTime; $eventType"
            Log.i("KKK", eventType)


            // Screenshot
            val action_type: Int
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                action_type = ScreenShot.TYPE_CLICK
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                action_type = ScreenShot.TYPE_SCROLL
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                action_type = ScreenShot.TYPE_LONG_CLICK
            } else {
                action_type = ScreenShot.TYPE_SELECT
            }
            while (forward != next) {
                Log.i("AAA", "$forward!!!$next")
            }
            Log.i("AAA", "Finally out!!! Yeah!!!")
            forward++
            val consumer: Consumer<ScreenshotResult> = object : Consumer<ScreenshotResult?>() {
                fun accept(screenshotResult: ScreenshotResult) {
                    current_bitmap = wrapHardwareBuffer(
                        screenshotResult.hardwareBuffer,
                        screenshotResult.colorSpace
                    )
                    next++
                }
            }
            val hasTakenScreenShot: Boolean =
                takeScreenshot(DEFAULT_DISPLAY, THREAD_POOL_EXECUTOR, consumer)
            //            try {
//                THREAD_POOL_EXECUTOR.wait();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            Log.i("Screenshot", hasTakenScreenShot.toString())
            while (forward != next) {
                Log.i("BBB", "$forward!!!$next")
            }
            Log.i("BBB", "Finally out!!! Yeah!!!")

            // parse view hierarchy
            // current node
            val node = event.source ?: return
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            val root = rootInActiveWindow
            val boxes: ArrayList<Rect> = ArrayList<Rect>()
            if (root != null) {
                boxes.addAll(get_boxes(root)!!)
            }
            current_screenshot = ScreenShot(current_bitmap, outbounds, action_type, boxes)


            // VH
//            String json = parse_vh_to_json(node);
//
//            String vh_currentnode = "Current Node: " + "\n" + json + "\n";

            // all nodes
            val rootInActiveWindow = rootInActiveWindow
            var vh = ""
            if (rootInActiveWindow != null) {
                vh = parse_vh_to_json(rootInActiveWindow)
            }

            // add the event
            add_event(node, packageName, isNewTrace, eventDescription, current_screenshot!!, vh)
            last_package_name = packageName
        }
    }

    private fun uploadFile(
        packageId: String,
        trace_number: String,
        action_number: String,
        gestureDescription: String,
        vh_content: String,
        bitmap: Bitmap?
    ) {
        val options: StorageUploadFileOptions = StorageUploadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        // upload VH
        val vhFile = File(applicationContext.filesDir, "vh")
        val base_location = "$user_id/$packageId/$trace_number/"
        val vh_location = base_location + "view_hierarchies" + "/" + action_number
        try {
            val writer = BufferedWriter(FileWriter(vhFile))
            writer.append(vh_content)
            writer.close()
        } catch (exception: Exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception)
        }
        Amplify.Storage.uploadFile(
            vh_location,
            vhFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }

        // upload screenshot
        val screenshotFile = File(applicationContext.filesDir, "screenshot")
        val screenshot_location = base_location + "screenshots" + "/" + action_number
        try {
            FileOutputStream(screenshotFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

//        Amplify.Storage.uploadFile(
//                key,
//                file,
//                options,
//                result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + key,
//                        error -> Log.e("MyAmplifyApp", "Upload failed", error)
//                );
        Amplify.Storage.uploadFile(
            screenshot_location,
            screenshotFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }

        // upload gestures
        val gson = Gson()
        var json: String = gson.toJson(gestures_map)
        json = json.replace("\\\\".toRegex(), "")
        val gestureFile = File(applicationContext.filesDir, "vh")
        val gesture_location = base_location + "gestures"
        try {
            val writer = BufferedWriter(FileWriter(gestureFile))
            writer.append(json)
            writer.close()
        } catch (exception: Exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception)
        }
        Amplify.Storage.uploadFile(
            gesture_location,
            gestureFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.getKey()) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }
    }


    private fun get_boxes(node: AccessibilityNodeInfo): ArrayList<Rect>? {
        val boxes: ArrayList<Rect> = ArrayList<Rect>()
        val rect = Rect()
        node.getBoundsInScreen(rect)
        boxes.add(rect)
        for (i in 0 until node.getChildCount()) {
            val current_node = node.getChild(i)
            if (current_node != null) {
                boxes.addAll(get_boxes(current_node)!!)
            }
        }
        Log.i("Box size", java.lang.String.valueOf(boxes.size()))
        return boxes
    }


    private fun parse_vh_to_json(node: AccessibilityNodeInfo): String {
        val map: MutableMap<String, String> = HashMap()
        map["package_name"] = node.getPackageName().toString()
        map["class_name"] = node.getClassName().toString()
        map["scrollable"] = java.lang.String.valueOf(node.isScrollable())
        if (node.getParent() != null) {
            val cs = node.getParent().className
            if (cs != null) {
                map["parent"] = node.getParent().className.toString()
            }
        } else {
            map["parent"] = "none"
        }
        map["clickable"] = java.lang.String.valueOf(node.isClickable())
        map["focusable"] = java.lang.String.valueOf(node.isFocusable())
        map["long-clickable"] = java.lang.String.valueOf(node.isLongClickable())
        map["enabled"] = java.lang.String.valueOf(node.isEnabled())
        val outbounds = Rect()
        node.getBoundsInScreen(outbounds)
        map["bounds_in_screen"] = outbounds.toString()
        map["visibility"] = java.lang.String.valueOf(node.isVisibleToUser())
        if (node.getContentDescription() != null) {
            map["content-desc"] = node.getContentDescription().toString()
        } else {
            map["content-desc"] = "none"
        }
        node.getBoundsInParent(outbounds)
        map["bounds_in_parent"] = outbounds.toString()
        map["focused"] = java.lang.String.valueOf(node.isFocused())
        map["selected"] = java.lang.String.valueOf(node.isSelected())
        map["children_count"] = java.lang.String.valueOf(node.getChildCount())
        map["checkable"] = java.lang.String.valueOf(node.isCheckable())
        map["checked"] = java.lang.String.valueOf(node.isChecked())
        val text = node.getText()
        if (text != null) {
            val text_field = text.toString()
            if (text_field != "") {
                map["text_field"] = text.toString()
            }
        }
        var children_vh = "["
        for (i in 0 until node.getChildCount()) {
            val current_node = node.getChild(i)
            if (current_node != null) {
                children_vh += parse_vh_to_json(current_node) + ","
            }
        }
        children_vh += "]"
        Log.i("VH!!!!!!!", children_vh)
        map["children"] = children_vh

        //map.put("to_string", node.toString());
        val gson = Gson()
        var json: String = gson.toJson(map)
        json = json.replace("\\\\".toRegex(), "")
        return json
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
    private fun add_event(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventDescription: String,
        currentScreenShot: ScreenShot,
        viewHierachy: String
    ) {

        // add the package
        val package_map = package_layer.map
        if (!package_map.containsKey(packageName)) {
            // this is a new package
            package_set.add(packageName)
            package_list.clear()
            package_list.addAll(package_set)
            package_layer.list = package_list
            package_map[packageName] = Layer()
            package_layer.map = package_map
        }
        notifyPackageAdapter()

        // add the trace
        val traceName: String
        val trace_layer = package_map[packageName] ?: return
        val trace_map = trace_layer.map
        val trace_list = trace_layer.list
        if (isNewTrace) {
            // this is a new trace
            traceName = "trace_" + (trace_list.size + 1)
            trace_list.add(traceName)
            trace_layer.list = trace_list
            trace_map[traceName] = Layer()
            trace_layer.map = trace_map
        } else {
            traceName = "trace_" + trace_list.size
        }
        notifyTraceAdapter()

        // add the event
        val event_name: String
        val event_layer = trace_map[traceName] ?: return
        val event_list = event_layer.list
        event_name = java.lang.String.valueOf(event_list.size() + 1)
        event_list.add(eventDescription)
        event_layer.list = event_list
        val event_map = event_layer.map
        event_map[eventDescription] = Layer()
        event_layer.map = event_map

//        Log.i("Oppps", event_layer.getList().get(0));
//        Log.i("Oppps", Boolean.toString(event_layer.getMap().containsKey(eventDescription)));
        notifyEventAdapter()

        // update gesture map
        val outbounds = Rect()
        node.getBoundsInScreen(outbounds)
        val coordinates = "[" + "[" + outbounds.centerX() + "," + outbounds.centerY() + "]" + "]"
        gestures_map!![event_name] = coordinates

        // add the screenshot
        val screenshot_layer = event_map[eventDescription] ?: return
        screenshot_layer.screenShot = currentScreenShot
        val screenshot_map = screenshot_layer.map
        screenshot_map[eventDescription] = Layer()
        screenshot_layer.map = screenshot_map

        // add the view hierarchy
        val view_hierarchy_layer = screenshot_map[eventDescription] ?: return
        val view_hierarchy_list = ArrayList<String>()
        view_hierarchy_list.add(viewHierachy)
        view_hierarchy_layer.list = view_hierarchy_list
        notifyVHAdapter()
        uploadFile(
            packageName,
            traceName,
            event_name,
            eventDescription,
            viewHierachy,
            currentScreenShot.bitmap
        )
    }

    override fun onInterrupt() {}
}
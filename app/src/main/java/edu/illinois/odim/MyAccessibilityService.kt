package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.AsyncTask.THREAD_POOL_EXECUTOR
import android.os.Build
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.amplifyframework.storage.s3.AWSS3StoragePlugin
import com.google.gson.Gson
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Consumer

val package_list: ArrayList<String> = ArrayList()
val package_set: MutableSet<String> = HashSet()

val package_layer = Layer()

fun getPackages(): ArrayList<String> {
    return package_list
}

fun getTraces(package_name: String?): ArrayList<String> {
    return package_layer.map[package_name]!!.list
}

fun getEvents(package_name: String?, trace_name: String?): ArrayList<String> {
    return package_layer.map[package_name]!!.map[trace_name]!!.list
}

fun getScreenshot(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ScreenShot {
    return package_layer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.screenShot
}

fun getVh(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ArrayList<String> {
    return package_layer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.map[event_name]!!.list
}

class MyAccessibilityService : AccessibilityService() {

    private var lastPackageName: String? = null

    companion object {
        const val DEBUG_TAG = "NetworkStatusExample"
    }


    private var currentBitmap: Bitmap? = null

    private var currentScreenshot: ScreenShot? = null

    private var forward = 0
    private var next = 0

    private var gesturesMap: HashMap<String, String>? = null

    private val userId = "test_user15"



    override fun onServiceConnected() {
        // Create the service
        println("onServiceConnected")
        val info = AccessibilityServiceInfo()
        info.apply {
            // TODO: why are we adding both? Isn't TYPES_ALL_MASK include the other?
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK or AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            notificationTimeout = 100
            packageNames = null
        }
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
        gesturesMap = HashMap()

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
                    if (result.isSignInComplete) "Sign in succeeded" else "Sign in not complete"
                )
            }
        ) { error -> Log.e("AuthQuickstart", error.toString()) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val connMgr: ConnectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isWifiConn = false
        var isMobileConn = false

        // found non-deprecated solution from: https://stackoverflow.com/questions/49819923/kotlin-checking-network-status-using-connectivitymanager-returns-null-if-networ
        // specifically answered by @AliSh
        // check if connected to wifi or mobile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connMgr.activeNetwork ?: return
            val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) ?: return
            isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            isMobileConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {   // TODO: Needed for backwards compatibility, but this code is deprecated as we serve as low as API 21
            for (network in connMgr.allNetworks) {
                val networkInfo: NetworkInfo? = connMgr.getNetworkInfo(network)
                if (networkInfo?.type == ConnectivityManager.TYPE_WIFI) {
                    isWifiConn = isWifiConn or networkInfo.isConnected
                }
                if (networkInfo?.type == ConnectivityManager.TYPE_MOBILE) {
                    isMobileConn = isMobileConn or networkInfo.isConnected
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
        if (event.packageName == "edu.illinois.recordingservice") {
            return
        }
        var isNewTrace = false
        if (lastPackageName == null || packageName != lastPackageName) {
            isNewTrace = true
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            || (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
            || (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
            || (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {
            // Parse event description
            val date = Date(event.eventTime)
            val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val dateFormatted: String = formatter.format(date)
            val eventTime = "Event Time: $dateFormatted"
            val eventType = "Event Type: " + eventTypeToString(event.eventType)
            val eventDescription = "$eventTime; $eventType"
            Log.i("KKK", eventType)


            // Screenshot
            val actionType: Int = when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    ScreenShot.TYPE_CLICK
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    ScreenShot.TYPE_SCROLL
                }
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    ScreenShot.TYPE_LONG_CLICK
                }
                else -> {
                    ScreenShot.TYPE_SELECT
                }
            }
            while (forward != next) {
                Log.i("AAA", "$forward!!!$next")
            }
            Log.i("AAA", "Finally out!!! Yeah!!!")
            forward++
            val consumer: Consumer<ScreenshotResult> = object : Consumer<ScreenshotResult?>() {
                fun accept(screenshotResult: ScreenshotResult) {
                    currentBitmap = wrapHardwareBuffer(
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
                boxes.addAll(getBoxes(root))
            }
            currentScreenshot = ScreenShot(currentBitmap, outbounds, actionType, boxes)


            // VH
//            String json = parse_vh_to_json(node);
//
//            String vh_currentnode = "Current Node: " + "\n" + json + "\n";

            // all nodes
            val rootInActiveWindow = rootInActiveWindow
            var vh = ""
            if (rootInActiveWindow != null) {
                vh = parseVHToJson(rootInActiveWindow)
            }

            // add the event
            addEvent(node, packageName, isNewTrace, eventDescription, currentScreenshot!!, vh)
            lastPackageName = packageName
        }
    }

    private fun uploadFile(
        packageId: String,
        trace_number: String,
        action_number: String,
        gestureDescription: String,  // TODO: we aren't using this, should we keep it?
        vh_content: String,
        bitmap: Bitmap?
    ) {
        val options: StorageUploadFileOptions = StorageUploadFileOptions.builder()
            .accessLevel(StorageAccessLevel.PRIVATE)
            .build()

        // upload VH
        val vhFile = File(applicationContext.filesDir, "vh")
        val baseLocation = "$userId/$packageId/$trace_number/"
        val viewHierarchyLocation = baseLocation + "view_hierarchies" + "/" + action_number
        try {
            val writer = BufferedWriter(FileWriter(vhFile))
            writer.append(vh_content)
            writer.close()
        } catch (exception: Exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception)
        }
        Amplify.Storage.uploadFile(
            viewHierarchyLocation,
            vhFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.key) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }

        // upload screenshot
        val screenshotFile = File(applicationContext.filesDir, "screenshot")
        val screenshotLocation = baseLocation + "screenshots" + "/" + action_number
        try {
            FileOutputStream(screenshotFile).use { out ->
                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
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
            screenshotLocation,
            screenshotFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.key) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }

        // upload gestures
        val gson = Gson()
        var json: String = gson.toJson(gesturesMap)
        json = json.replace("\\\\".toRegex(), "")
        val gestureFile = File(applicationContext.filesDir, "vh")
        val gestureLocation = baseLocation + "gestures"
        try {
            val writer = BufferedWriter(FileWriter(gestureFile))
            writer.append(json)
            writer.close()
        } catch (exception: Exception) {
            Log.e("MyAmplifyApp", "Upload failed", exception)
        }
        Amplify.Storage.uploadFile(
            gestureLocation,
            gestureFile,
            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.key) }
        ) { storageFailure -> Log.e("MyAmplifyApp", "Upload failed", storageFailure) }
    }


    private fun getBoxes(node: AccessibilityNodeInfo): ArrayList<Rect> {
        val boxes: ArrayList<Rect> = ArrayList<Rect>()
        val rect = Rect()
        node.getBoundsInScreen(rect)
        boxes.add(rect)
        for (i in 0 until node.childCount) {
            val currentNode = node.getChild(i)
            if (currentNode != null) {
                boxes.addAll(getBoxes(currentNode))
            }
        }
        Log.i("Box size", java.lang.String.valueOf(boxes.size))
        return boxes
    }


    private fun parseVHToJson(node: AccessibilityNodeInfo): String {
        val map: MutableMap<String, String> = HashMap()
        map["package_name"] = node.packageName.toString()
        map["class_name"] = node.className.toString()
        map["scrollable"] = java.lang.String.valueOf(node.isScrollable)
        if (node.parent != null) {
            val cs = node.parent.className
            if (cs != null) {
                map["parent"] = node.parent.className.toString()
            }
        } else {
            map["parent"] = "none"
        }
        map["clickable"] = java.lang.String.valueOf(node.isClickable)
        map["focusable"] = java.lang.String.valueOf(node.isFocusable)
        map["long-clickable"] = java.lang.String.valueOf(node.isLongClickable)
        map["enabled"] = java.lang.String.valueOf(node.isEnabled)
        val outbounds = Rect()
        node.getBoundsInScreen(outbounds)
        map["bounds_in_screen"] = outbounds.toString()
        map["visibility"] = java.lang.String.valueOf(node.isVisibleToUser)
        if (node.contentDescription != null) {
            map["content-desc"] = node.contentDescription.toString()
        } else {
            map["content-desc"] = "none"
        }
        node.getBoundsInParent(outbounds)   // TODO: why do we need this?
        map["bounds_in_parent"] = outbounds.toString()
        map["focused"] = java.lang.String.valueOf(node.isFocused)
        map["selected"] = java.lang.String.valueOf(node.isSelected)
        map["children_count"] = java.lang.String.valueOf(node.childCount)
        map["checkable"] = java.lang.String.valueOf(node.isCheckable)
        map["checked"] = java.lang.String.valueOf(node.isChecked)
        val text = node.text
        if (text != null) {
            val textField = text.toString()
            if (textField != "") {
                map["text_field"] = text.toString()
            }
        }
        var childrenVH = "["
        for (i in 0 until node.childCount) {
            val currentNode = node.getChild(i)
            if (currentNode != null) {
                childrenVH += parseVHToJson(currentNode) + ","
            }
        }
        childrenVH += "]"
        Log.i("VH!!!!!!!", childrenVH)
        map["children"] = childrenVH

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
    private fun addEvent(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventDescription: String,
        currentScreenShot: ScreenShot,
        viewHierarchy: String
    ) {

        // add the package
        val packageMap = package_layer.map
        if (!packageMap.containsKey(packageName)) {
            // this is a new package
            package_set.add(packageName)
            package_list.clear()
            package_list.addAll(package_set)
            package_layer.list = package_list
            packageMap[packageName] = Layer()
            package_layer.map = packageMap
        }
        notifyPackageAdapter()

        // add the trace
        val traceName: String
        val traceLayer = packageMap[packageName] ?: return
        val traceMap = traceLayer.map
        val traceList = traceLayer.list
        if (isNewTrace) {
            // this is a new trace
            traceName = "trace_" + (traceList.size + 1)
            traceList.add(traceName)
            traceLayer.list = traceList
            traceMap[traceName] = Layer()
            traceLayer.map = traceMap
        } else {
            traceName = "trace_" + traceList.size
        }
        notifyTraceAdapter()

        // add the event
        val eventName: String
        val eventLayer = traceMap[traceName] ?: return
        val eventList = eventLayer.list
        eventName = java.lang.String.valueOf(eventList.size + 1)
        eventList.add(eventDescription)
        eventLayer.list = eventList
        val eventMap = eventLayer.map
        eventMap[eventDescription] = Layer()
        eventLayer.map = eventMap

//        Log.i("Oppps", event_layer.getList().get(0));
//        Log.i("Oppps", Boolean.toString(event_layer.getMap().containsKey(eventDescription)));
        notifyEventAdapter()

        // update gesture map
        val outbounds = Rect()
        node?.getBoundsInScreen(outbounds)
        val coordinates = "[" + "[" + outbounds.centerX() + "," + outbounds.centerY() + "]" + "]"
        gesturesMap!![eventName] = coordinates

        // add the screenshot
        val screenshotLayer = eventMap[eventDescription] ?: return
        screenshotLayer.screenShot = currentScreenShot
        val screenshotMap = screenshotLayer.map
        screenshotMap[eventDescription] = Layer()
        screenshotLayer.map = screenshotMap

        // add the view hierarchy
        val viewHierarchyLayer = screenshotMap[eventDescription] ?: return
        val viewHierarchyList = ArrayList<String>()
        viewHierarchyList.add(viewHierarchy)
        viewHierarchyLayer.list = viewHierarchyList
        notifyVHAdapter()
        uploadFile(
            packageName,
            traceName,
            eventName,
            eventDescription,
            viewHierarchy,
            currentScreenShot.bitmap
        )
    }

    override fun onInterrupt() {}
}
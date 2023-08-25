package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.gildor.coroutines.okhttp.await
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.logging.Level
import java.util.logging.Logger


val packageList: ArrayList<String> = ArrayList()
val packageSet: MutableSet<String> = HashSet()
val packageLayer = Layer()
val redactionMap: HashMap<String, HashMap<String, String>> = HashMap()
internal var workerId = "test_user"
internal var projectCode = "test"  // TODO: use this to query cloud bucket and traces

fun getPackages(): ArrayList<String> {
    return packageList
}

fun getTraces(package_name: String?): ArrayList<String> {
    return packageLayer.map[package_name]!!.list
}

fun getEvents(package_name: String?, trace_name: String?): ArrayList<String> {
    return packageLayer.map[package_name]!!.map[trace_name]!!.list
}

fun getScreenshot(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ScreenShot {
    return packageLayer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.screenShot
}

fun getVh(
    package_name: String?,
    trace_name: String?,
    event_name: String?
): ArrayList<String> {
    return packageLayer.map[package_name]!!.map[trace_name]!!.map[event_name]!!.map[event_name]!!.list
}

fun setVh(
    package_name: String?,
    trace_name: String?,
    event_name: String?,
    json_string: String?
) {
    packageLayer
        .map[package_name]!!
        .map[trace_name]!!
        .map[event_name]!!
        .map[event_name]!!.list[0] = json_string!!
}

fun editTraceName(packageName: String, traceName: String, newTraceName: String) {
    // check if current trace and new trace name are same
    if (traceName == newTraceName) {
        return
    }
    // change gesturesMap, redactionMap
    if (MyAccessibilityService.gesturesMap!!.containsKey(traceName)) {
        MyAccessibilityService.gesturesMap!![newTraceName] = MyAccessibilityService.gesturesMap!!.remove(traceName)!!
    }
    if (redactionMap.containsKey(traceName)) {
        redactionMap[newTraceName] = redactionMap.remove(traceName)!!
    }
    // change list in package layer and move map
    val traceIdx = packageLayer.map[packageName]!!.list.indexOf(traceName)
    if (traceIdx > -1) {
        packageLayer.map[packageName]!!.list[traceIdx] = newTraceName
        packageLayer.map[packageName]!!.map[newTraceName] = packageLayer.map[packageName]!!.map.remove(traceName)!!
    }
    // add toast saying trace has been updated
    Toast.makeText(MyAccessibilityService.appContext, "Trace name is updated!", Toast.LENGTH_SHORT).show()
}

fun uploadFile(
    packageId: String,
    trace_name: String,
    event_name: String,
    vh_content: String,
    bitmap: Bitmap?
) : Boolean {
    // try to upload VH content to AWS
    val uploadScope = CoroutineScope(Dispatchers.IO)
    Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    val client = OkHttpClient()
    var isSuccessUpload = true
    try {
        // Upload VH file
        uploadScope.launch {
            val vhMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$workerId/$packageId/$trace_name/view_hierarchies/$event_name")
                .header("Connection", "close")
                .post(vh_content.toRequestBody(vhMediaType))
                .build()
            // check if api request was successful and log result
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload VH")
            } else {
                Log.e("api", "fail upload VH")
                isSuccessUpload = false
            }
            response.body?.close()
        }
        // exit function if upload was not successful and log
        if (!isSuccessUpload) {
            Log.e("upload", "upload VH failure in the api")
            return false
        }
        // Write json gestures to upload
        val gson = Gson()
        var json: String = gson.toJson(MyAccessibilityService.gesturesMap!![trace_name])
        json = json.replace("\\\\".toRegex(), "")
        // upload gestures file
        uploadScope.launch {
            val gestureMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$workerId/$packageId/$trace_name/gestures")
                .header("Connection", "close")
                .post(json.toRequestBody(gestureMediaType))
                .build()
            // check if api request was successful and log result
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload gestures")
            } else {
                Log.i("api", "fail upload gestures")
                isSuccessUpload = false
            }
            response.body?.close()
        }
        // exit function if upload was not successful and log
        if (!isSuccessUpload) {
            Log.e("upload", "upload failure in the api")
            return false
        }
        // upload screenshots
        uploadScope.launch {
            val byteOut = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteOut)
            val bitmapBase64 = Base64.encodeToString(byteOut.toByteArray(), Base64.DEFAULT)
            val screenshotMediaType = "text/plain".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$workerId/$packageId/$trace_name/screenshots/$event_name")
                .addHeader("Content-Transfer-Encoding", "base64")
                .addHeader("Content-Type", "text/plain")
                .header("Connection", "close")
                .post(bitmapBase64.toRequestBody(screenshotMediaType))
                .build()
            // check if api request was successful and log result
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload screenshot")
            } else {
                Log.e("api", "fail upload screenshot")
                isSuccessUpload = false
            }
            response.body?.close()
        }
        // exit function if upload was not successful and log
        if (!isSuccessUpload) {
            Log.e("upload", "upload failure in the api")
            return false
        }
        // upload redactions
        if (redactionMap.containsKey(trace_name) && redactionMap[trace_name]!!.containsKey(event_name)) {
            // upload redactions
            uploadScope.launch {
                val redactionMediaType = "text/plain".toMediaType()
                val output = "startX,startY,endX,endY,label\n" + redactionMap[trace_name]!![event_name]!!
                val request = Request.Builder()
                    .url("http://10.0.2.2:3000/aws/upload/$workerId/$packageId/$trace_name/redactions/$event_name")
                    .addHeader("Content-Type", "text/plain")
                    .header("Connection", "close")
                    .post(output.toRequestBody(redactionMediaType))
                    .build()
                // check if api request was successful and log result
                val response: Response = client.newCall(request).await()
                if (response.isSuccessful) {
                    Log.i("api", "success upload redactions")
                } else {
                    Log.i("api", "fail upload redactions")
                    isSuccessUpload = false
                }
                response.body?.close()
            }
        }

    } catch (exception: Exception) {
        Log.e("ODIMUpload", "Upload filed", exception)
        return false
    }
    return isSuccessUpload
}

class MyAccessibilityService : AccessibilityService() {
    private var lastPackageName: String? = null
    private var currentBitmap: Bitmap? = null
    private var currentScreenshot: ScreenShot? = null
    private var isScreenEventPaired: Boolean = false
    private var windowManager: WindowManager? = null
    var currRootWindow: AccessibilityNodeInfo? = null
    var currVHBoxes: ArrayList<Rect> = ArrayList()
    var currVHString: String? = null
    var currTouchPackage: String? = null
    var currTouchTime: String? = null

    companion object {
        lateinit var appContext: Context
        var gesturesMap: HashMap<String, HashMap<String, String>>? = null
    }

    fun getInteractionTime(): String {
        val date = Date(System.currentTimeMillis()) //event.eventTime)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        // Create the service
        Log.i("onServiceConnected", "Accessibility Service Connected")
        appContext = applicationContext
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_SELECTED or
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.notificationTimeout = 50
        info.packageNames = null
        serviceInfo = info
        // initialize map to capture gestures
        gesturesMap = HashMap()
        // add invisible layout to get touches to screen
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        val layout = FrameLayout(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        windowManager!!.addView(layout, params)
        // record screenshot and view hierarchy when screen touch is detected
        layout.setOnTouchListener (object : View.OnTouchListener {
            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                // do not update screenshot or vh immediately after home button pressed
                currRootWindow = rootInActiveWindow
                // validate if home button pressed, root window different from last touch
                if (currTouchPackage != null &&
                    lastPackageName == currTouchPackage &&
                    (currRootWindow == null ||
                            (currRootWindow!!.packageName == "com.google.android.apps.nexuslauncher" &&
                            currRootWindow!!.packageName != currTouchPackage))
                ) {
                    // record event type as home button press
                    val eventTime = currTouchTime
                    val eventType = "TYPE_HOME_PRESSED"
                    val eventDescription = "$eventTime; $eventType"
                    // record screen type
                    val actionType = ScreenShot.TYPE_HOME
                    // create gesture for home button press
                    val windowMetrics = windowManager!!.currentWindowMetrics
                    val gestureX = windowMetrics.bounds.width() / 2 // assume home button is at bottom middle
                    val gestureY = windowMetrics.bounds.height()
                    val outbounds = Rect(gestureX-20, gestureY-50, gestureX+20, gestureY-10)  // give no source node if home button pressed
                    val vhString = currVHString
                    val vhBoxes = ArrayList(currVHBoxes)
                    // record vh and boxes
                    currentScreenshot = ScreenShot(currentBitmap!!, outbounds, actionType, null, vhBoxes)
                    // add home button press to event
                    addEvent(null, currTouchPackage!!, false, eventDescription, null, currentScreenshot!!, vhString!!)
                    // reset everything
                    currVHString = null
                    currVHBoxes.clear()
                    currTouchPackage = null //currRootWindow!!.packageName.toString()
                    lastPackageName = currRootWindow!!.packageName.toString()
                    return false
                }
                // reset vh and boxes to record next screen touch
                if (currVHBoxes.isNotEmpty()) {
                    currVHBoxes.clear()
                }
                currVHString = null

                if (currRootWindow == null ||
                    currRootWindow!!.packageName == "edu.illinois.odim" ||
                    currRootWindow!!.packageName == "com.google.android.apps.nexuslauncher"
                ) {
                    return false
                }
                // get view hierarchy at this time
                currVHString = parseVHToJson(currRootWindow!!)
                currVHBoxes = ArrayList(getBoxes(currRootWindow!!))
                Log.i("currRootWindow", "update window")
                // take screenshot and record current bitmap globally
                takeScreenshot(
                    DEFAULT_DISPLAY,
                    appContext.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            isScreenEventPaired = false
                            currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            result.hardwareBuffer.close()
                            Log.i("screenshot", "update screen")
                            currTouchPackage = currRootWindow!!.packageName.toString()
                            currTouchTime = getInteractionTime()
                        }

                        override fun onFailure(errCode: Int) {
                            Log.e("ScreenshotFailure:", "Error code: $errCode")
                        }
                    }
                )
                return false
            }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // found non-deprecated solution from: https://stackoverflow.com/questions/49819923/kotlin-checking-network-status-using-connectivitymanager-returns-null-if-networ
        // specifically answered by @AliSh
        // check if connected to wifi or mobile
        val connMgr: ConnectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connMgr.activeNetwork ?: return
        val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) ?: return
        val isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!isWifiConn) {
            return
        }
        // do not record unnecessary app packages
        // however, record the last screeen of a trace (app package -> nexuslauncher)
        if (event == null || event.packageName == null) {
            return
        }
        val packageName = event.packageName.toString()
        if (event.packageName == "edu.illinois.odim" ||
            (event.packageName == "com.google.android.apps.nexuslauncher")
        ) {
            lastPackageName = event.packageName.toString()
            return
        }
        var isNewTrace = false
        if (lastPackageName == null || packageName != lastPackageName) {
            isNewTrace = true
        }

        if (isScreenEventPaired) {  // ignore following non user events
            return
        }
        // construct interaction event
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)) {
            if (currentBitmap == null) {
                return
            }
            isScreenEventPaired = true
            // Parse event description
            val eventTime = getInteractionTime()
            val eventType = eventTypeToString(event.eventType)
            val eventDescription = "$eventTime; $eventType"
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
                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    ScreenShot.TYPE_SELECT
                }
                else -> {
                    ScreenShot.TYPE_SELECT
                }
            }
            // check if event scroll, add delta coordinates
            var scrollCoords : Pair<Int, Int>? = null
            if (actionType == ScreenShot.TYPE_SCROLL) {
                scrollCoords = Pair(event.scrollDeltaX, event.scrollDeltaY)
            }
            // get vh element interacted with and parse view hierarchy of current screen if null
            val node = event.source ?: return
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            if (currRootWindow == null) {
                Log.i("window", "refresh window root")
                currRootWindow = rootInActiveWindow ?: return
            }
            val boxes: ArrayList<Rect> = ArrayList()
            if (currVHBoxes.isEmpty()) {
                boxes.addAll(getBoxes(currRootWindow!!))
            } else {
                boxes.addAll(currVHBoxes)
            }
            // construct screenshot
            currentScreenshot = ScreenShot(currentBitmap!!, outbounds, actionType, scrollCoords, boxes)
            // create json string of VH
            var vh = currVHString
            if (vh == null) {
                vh = parseVHToJson(currRootWindow!!)
            }
            // add the event
            addEvent(node, packageName, isNewTrace, eventDescription, scrollCoords, currentScreenshot!!, vh)
            lastPackageName = packageName
        }
    }

    private fun getBoxes(node: AccessibilityNodeInfo): ArrayList<Rect> {
        val boxes: ArrayList<Rect> = ArrayList()
        val rect = Rect()
        node.getBoundsInScreen(rect)
        boxes.add(rect)
        for (i in 0 until node.childCount) {
            val currentNode = node.getChild(i)
            if (currentNode != null) {
                boxes.addAll(getBoxes(currentNode))
            }
        }
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
        val outbounds = Rect() // Rect(x1 y1, x2, y2) -> "[x1, y1, x2, y2]"
        node.getBoundsInScreen(outbounds)
        map["bounds_in_screen"] = outbounds.toString()
        map["visibility"] = java.lang.String.valueOf(node.isVisibleToUser)
        if (node.contentDescription != null) {
            map["content-desc"] = node.contentDescription.toString()
        } else {
            map["content-desc"] = "none"
        }
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
                childrenVH += parseVHToJson(currentNode)
                if (i < node.childCount-1) {
                    childrenVH += ","
                }
            }
        }
        childrenVH += "]"
        map["children"] = childrenVH
        // convert to json string
        val gson = Gson()
        return gson.toJson(map)
    }

    private fun getGestureCoordinates(node: AccessibilityNodeInfo?, scrollCoords: Pair<Int, Int>?) : String {
        var coordinates = "[["
        if (node == null) {
            // take into account when node is empty (when home button is pressed)
            val windowMetrics = windowManager!!.currentWindowMetrics
            val gestureX = windowMetrics.bounds.width() / 2 // assume home button is at bottom middle
            val gestureY = windowMetrics.bounds.height()
            coordinates += "${gestureX},${gestureY-30}"
        } else {
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            coordinates += "${outbounds.centerX()},${outbounds.centerY()}"
            if (scrollCoords != null) {
                coordinates += ",${scrollCoords.first},${scrollCoords.second}"
            }
        }
        coordinates += "]]"
        return coordinates
    }

    private fun addEvent(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventDescription: String,
        scrollCoords: Pair<Int, Int>?,
        currentScreenShot: ScreenShot,
        viewHierarchy: String
    ) {

        // add the package and notify adapter the list has made changes
        val packageMap = packageLayer.map
        if (!packageMap.containsKey(packageName)) {
            // this is a new package
            packageSet.add(packageName)
            packageList.clear()
            packageList.addAll(packageSet)
            packageLayer.list = packageList
            packageMap[packageName] = Layer()
            packageLayer.map = packageMap
        }
        notifyPackageAdapter()
        // add the trace and notify adapter the list has made changes
        val traceName: String
        val traceLayer = packageMap[packageName] ?: return
        val traceMap = traceLayer.map
        val traceList = traceLayer.list
        if (isNewTrace) {
            // this is a new trace
            traceName = eventDescription.substringBefore(";")
            traceList.add(traceName)
            traceLayer.list = traceList
            traceMap[traceName] = Layer()
            traceLayer.map = traceMap
        } else {
            traceName = traceList.last()
        }
        notifyTraceAdapter()
        // add the event and notify adapter the list has made changes
        val eventLayer = traceMap[traceName] ?: return
        val eventList = eventLayer.list
        eventList.add(eventDescription)
        eventLayer.list = eventList
        val eventMap = eventLayer.map
        eventMap[eventDescription] = Layer()
        eventLayer.map = eventMap
        notifyEventAdapter()
        // update gesture map with new gesture
        val coordinates = getGestureCoordinates(node, scrollCoords)
        val eventName = eventDescription.substringBefore(";")
        if (gesturesMap!!.containsKey(traceName)) {
            gesturesMap!![traceName]?.set(eventName, coordinates)
        } else {
            gesturesMap!![traceName] = HashMap()
            gesturesMap!![traceName]?.set(eventName, coordinates)
        }
        // add a new screenshot to list of traces
        val screenshotLayer = eventMap[eventDescription] ?: return
        screenshotLayer.screenShot = currentScreenShot
        val screenshotMap = screenshotLayer.map
        screenshotMap[eventDescription] = Layer()
        screenshotLayer.map = screenshotMap
        // add the view hierarchy to list of traces
        val viewHierarchyLayer = screenshotMap[eventDescription] ?: return
        val viewHierarchyList = ArrayList<String>()
        viewHierarchyList.add(viewHierarchy)
        viewHierarchyLayer.list = viewHierarchyList
    }

    override fun onInterrupt() {}
}
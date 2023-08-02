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

fun uploadFile(   // TODO: add redaction data to uploads as well
    packageId: String,
    trace_number: String,
    action_number: String,
    vh_content: String,
    bitmap: Bitmap?
) : Boolean {
    // try to upload VH content to AWS
    val uploadScope = CoroutineScope(Dispatchers.IO)
    val client = OkHttpClient()
    val bucket = "mobileodimbucket155740-dev"  // TODO: will retrieve from project api
    Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    var isSuccessUpload = true
    try {
        // Upload VH file
        uploadScope.launch {
            val gestureMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$workerId/$packageId/$trace_number/view_hierarchies/$action_number")
                .header("Connection", "close")
                .post(vh_content.toRequestBody(gestureMediaType))
                .build()
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload gestures")
            } else {
                Log.e("api", "fail upload gestures")
                isSuccessUpload = false
            }
            response.body?.close()
        }

        if (!isSuccessUpload) {
            Log.e("upload", "upload failure in the api")
            return false
        }

        // Write json gestures to upload
        val gson = Gson()
        var json: String = gson.toJson(MyAccessibilityService.gesturesMap)
        json = json.replace("\\\\".toRegex(), "")
        // upload gestures file
        uploadScope.launch {
            val vhMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$workerId/$packageId/$trace_number/gestures")
                .header("Connection", "close")
                .post(json.toRequestBody(vhMediaType))
                .build()
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload view hierarchies")
            } else {
                Log.i("api", "fail upload view hierarchies")
                isSuccessUpload = false
            }
            response.body?.close()
        }

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
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$workerId/$packageId/$trace_number/screenshots/$action_number")
                .addHeader("Content-Transfer-Encoding", "base64")
                .addHeader("Content-Type", "text/plain")
                .header("Connection", "close")
                .post(bitmapBase64.toRequestBody(screenshotMediaType))
                .build()

            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload screenshot")
            } else {
                Log.e("api", "fail upload screenshot")
                isSuccessUpload = false
            }
            response.body?.close()
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

    private var windowManager: WindowManager? = null

    private var isScreenEventPaired: Boolean = false

    var currRootWindow: AccessibilityNodeInfo? = null
    var currVHBoxes: ArrayList<Rect> = ArrayList()
    var currVHString: String? = null

    companion object {
        lateinit var appContext: Context
        var gesturesMap: HashMap<String, String>? = null
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
        layout.setOnTouchListener (object : View.OnTouchListener {
            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                currRootWindow = rootInActiveWindow
                if (!currVHBoxes.isEmpty()) {
                    currVHBoxes.clear()
                }
                currVHString = null

                if (currRootWindow == null ||
                    currRootWindow!!.packageName == "edu.illinois.recordingservice" ||
                    currRootWindow!!.packageName == "edu.illinois.odim" ||
                    currRootWindow!!.packageName == "com.google.android.apps.nexuslauncher"
                ) {
                    return false
                }

                currVHBoxes = getBoxes(currRootWindow!!)
                currVHString = parseVHToJson(currRootWindow!!)
                Log.i("currRootWindow", "update window")

                takeScreenshot(
                    DEFAULT_DISPLAY,
                    appContext.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            isScreenEventPaired = false
                            currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            result.hardwareBuffer.close()
                            Log.i("screenshot", "update screen")
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
        val connMgr: ConnectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // found non-deprecated solution from: https://stackoverflow.com/questions/49819923/kotlin-checking-network-status-using-connectivitymanager-returns-null-if-networ
        // specifically answered by @AliSh
        // check if connected to wifi or mobile
        val networkCapabilities = connMgr.activeNetwork ?: return
        val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) ?: return
        val isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        if (!isWifiConn) {
            return
        }
        if (event == null || event.packageName == null) {
            return
        }
        Log.i("screen event pair", isScreenEventPaired.toString() + ":" + eventTypeToString(event.eventType) + ":" + event.packageName)

        val packageName = event.packageName.toString()
        if (event.packageName == "edu.illinois.recordingservice" ||
            event.packageName == "edu.illinois.odim" ||
            event.packageName == "com.google.android.apps.nexuslauncher"
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

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {
            if (currentBitmap == null) {
                return
            }

            isScreenEventPaired = true
            // Parse event description
            val date = Date(System.currentTimeMillis()) //event.eventTime)
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val eventTime = formatter.format(date)
            val eventType = eventTypeToString(event.eventType)
            val eventDescription = "$eventTime; $eventType"


            // Screenshot
            var scroll_coords : Pair<Int, Int>? = null
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
            if (actionType == ScreenShot.TYPE_SCROLL) {
                Log.i("scroll:", "x:"+event.scrollDeltaX.toString()+",y:"+event.scrollDeltaY.toString())
                scroll_coords = Pair(event.scrollDeltaX, event.scrollDeltaY)
            }

            // parse view hierarchy
            // current node
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


            // add vh to screenshot
            Log.i("currBitmap", currentBitmap?.colorSpace.toString())
            currentScreenshot = ScreenShot(currentBitmap!!, outbounds, actionType, scroll_coords, boxes)

            // all nodes
            var vh = currVHString
            if (vh == null) {
                vh = parseVHToJson(currRootWindow!!)
            }


            // add the event
            addEvent(node, packageName, isNewTrace, eventDescription, currentScreenshot!!, vh)  // TODO: add scrolls to gesture
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

        val gson = Gson()
        return gson.toJson(map)
    }

    private fun addEvent(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventDescription: String,
        currentScreenShot: ScreenShot,
        viewHierarchy: String
    ) {

        // add the package
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

        // add the trace
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

        notifyEventAdapter()

        // update gesture map
        val outbounds = Rect()
        node?.getBoundsInScreen(outbounds)
        val coordinates = "[" + "[" + outbounds.centerX() + "," + outbounds.centerY() + "]" + "]"
        gesturesMap!![eventName] = coordinates  // TODO: update gesture map to include scrolls

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
    }

    override fun onInterrupt() {}
}
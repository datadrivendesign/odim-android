package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import ru.gildor.coroutines.okhttp.await
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger


val packageList: ArrayList<String> = ArrayList()
val packageSet: MutableSet<String> = HashSet()

val packageLayer = Layer()

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

fun uploadFile(
    packageId: String,
    trace_number: String,
    action_number: String,
    vh_content: String,
    bitmap: Bitmap?
) : Boolean {
    // try to upload VH content to AWS
    val uploadScope = CoroutineScope(Dispatchers.IO)
    val client = OkHttpClient()
    val bucket = "mobileodimbucket155740-dev"
    Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    var isSuccessUpload = true
    try {
        // Upload VH file
        uploadScope.launch {
            val gestureMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$userId/$packageId/$trace_number/view_hierarchies/$action_number")
                .header("Connection", "close")
                .post(vh_content.toRequestBody(gestureMediaType))
                .build()
            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload gestures")
            } else {
                Log.i("api", "fail upload gestures")
                isSuccessUpload = false
            }
            response.body?.close()
        }

        if (!isSuccessUpload) {
            return isSuccessUpload
        }

        // Write json gestures to upload
        val gson = Gson()
        var json: String = gson.toJson(MyAccessibilityService.gesturesMap)
        json = json.replace("\\\\".toRegex(), "")
        // upload gestures file
        uploadScope.launch {
            val vhMediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$userId/$packageId/$trace_number/gestures")
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
            return false
        }

        // upload gestures
        uploadScope.launch {
            val byteOut = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteOut)
            val bitmapBase64 = Base64.encodeToString(byteOut.toByteArray(), Base64.DEFAULT)
            val screenshotMediaType = "text/plain".toMediaType()
            val request = Request.Builder()
                .url("http://10.0.2.2:3000/aws/upload/$bucket/$userId/$packageId/$trace_number/screenshots/$action_number")
                .addHeader("Content-Transfer-Encoding", "base64")
                .addHeader("Content-Type", "text/plain")
                .header("Connection", "close")
                .post(bitmapBase64.toRequestBody(screenshotMediaType))
                .build()

            val response: Response = client.newCall(request).await()
            if (response.isSuccessful) {
                Log.i("api", "success upload screenshot")
            } else {
                Log.i("api", "fail upload screenshot")
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

    private lateinit var future: ScheduledFuture<*>
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    companion object {
        lateinit var appContext: Context

        var gesturesMap: HashMap<String, String>? = null
    }

    override fun onServiceConnected() {
        // Create the service
        Log.i("onServiceConnected", "Accessibility Service Connected")
        appContext = applicationContext
        val info = AccessibilityServiceInfo()
        info.apply {
            // TODO: why are we adding both? Isn't TYPES_ALL_MASK include the other?
            eventTypes =
                AccessibilityEvent.TYPES_ALL_MASK or AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            notificationTimeout = 100
            packageNames = null
        }
        serviceInfo = info
        gesturesMap = HashMap()

        // TODO: start up background scheduled screenshot take
        recordScreenPeriodically()
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
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            || (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)  // TODO: may need to remove this
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
            synchronized(this) {
                Log.i("currBitmap", currentBitmap?.colorSpace.toString())
                currentScreenshot = ScreenShot(currentBitmap, outbounds, actionType, boxes)
            }

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
        return boxes
    }

    private fun recordScreenPeriodically() {
        future = scheduledExecutorService.scheduleAtFixedRate({
            takeScreenshot(
                DEFAULT_DISPLAY,
                scheduledExecutorService,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                    }

                    override fun onFailure(errCode: Int) {
                        Log.e("ScreenshotFailure:", "Error code: $errCode")
                    }

                })
        }, 0, 400, TimeUnit.MILLISECONDS)
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

        //map.put("to_string", node.toString());
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
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        future.cancel(true)
        return super.onUnbind(intent)
    }
}
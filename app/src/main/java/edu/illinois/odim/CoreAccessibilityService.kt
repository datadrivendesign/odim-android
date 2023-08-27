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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import edu.illinois.odim.MyAccessibilityService.Companion.gesturesMap
import edu.illinois.odim.MyAccessibilityService.Companion.redactionMap
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
const val apiUrlPrefix = "https://35.224.147.233/api"
//const val localApiUrlPrefix = "http://10.0.2.2:3000/api"

fun getPackages(): ArrayList<String> {
    return packageList
}

fun getTraces(packageName: String?): ArrayList<String> {
    return packageLayer.map[packageName]!!.list
}

fun getEvents(packageName: String?, traceLabel: String?): ArrayList<String> {
    return packageLayer.map[packageName]!!.map[traceLabel]!!.list
}

fun getScreenshot(packageName: String?, traceLabel: String?, eventLabel: String?): ScreenShot {
    return packageLayer.map[packageName]!!.map[traceLabel]!!.map[eventLabel]!!.screenShot
}

fun getVh(packageName: String?, traceLabel: String?, eventLabel: String?): ArrayList<String> {
    return packageLayer.map[packageName]!!.map[traceLabel]!!.map[eventLabel]!!.map[eventLabel]!!.list
}

fun setVh(packageName: String?, traceLabel: String?, eventLabel: String?, vhJsonString: String?) {
    packageLayer.map[packageName]!!.map[traceLabel]!!.map[eventLabel]!!.map[eventLabel]!!.list[0] = vhJsonString!!
}

// Upload screen POST: req.body -> vh, img (bit64 string), created date, gestures
// make sure to record the screen _ids for traces
suspend fun uploadScreen(client: OkHttpClient,
                         gson: Gson,
                         packageName: String,
                         traceLabel: String,
                         eventLabel: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    // get bitmap as bit64 string
    val byteOut = ByteArrayOutputStream()
    val bitmap = getScreenshot(packageName, traceLabel, eventLabel).bitmap
    bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteOut)
    val bitmapBase64 = Base64.encodeToString(byteOut.toByteArray(), Base64.DEFAULT)
    // retrieve vh
    val vhString = getVh(packageName, traceLabel, eventLabel)[0]
    // retrieve screen timestamp
    val screenCreatedAt = eventLabel.substringBefore(";")
    // retrieve gesture for screen
    val gesture: Gesture = gesturesMap[traceLabel]?.get(eventLabel) ?: Gesture()
    // construct request body
    val reqBodyJSONObj = JsonObject()
    reqBodyJSONObj.addProperty("vh", vhString)
    reqBodyJSONObj.addProperty("img", bitmapBase64)
    reqBodyJSONObj.addProperty("created", screenCreatedAt)
    reqBodyJSONObj.add("gestures", gson.toJsonTree(gesture) as JsonObject)
    val reqBody = gson.toJson(reqBodyJSONObj)
    // run the POST request
    val screenPostRequest = Request.Builder()
        .url("$apiUrlPrefix/screens")
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .header("Connection", "close")
        .post(reqBody.toRequestBody(jsonMediaType))
        .build()
    return client.newCall(screenPostRequest).await()
}

// Upload redaction POST - screen: objectId
suspend fun uploadRedaction(client: OkHttpClient,
                            gson: Gson,
                            redaction: Redaction,
                            screenId: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val reqBodyJSONObj: JsonObject = gson.toJsonTree(redaction) as JsonObject
    reqBodyJSONObj.addProperty("screen", screenId)
    val reqBody = gson.toJson(reqBodyJSONObj)
    val vhPostRequest = Request.Builder()
        .url("$apiUrlPrefix/redactions")
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .header("Connection", "close")
        .post(reqBody.toRequestBody(jsonMediaType))
        .build()
    return client.newCall(vhPostRequest).await()
}

// Upload trace POST - also include app: packageName, screens: [objectIds]
suspend fun uploadTrace(client: OkHttpClient,
                        gson: Gson,
                        screenIds: ArrayList<String>,
                        packageName: String,
                        traceLabel: String,
                        traceDescription: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val reqBodyJSONObj = JsonObject()
    reqBodyJSONObj.add("screens", gson.toJsonTree(screenIds) as JsonArray)
    reqBodyJSONObj.addProperty("app", packageName)
    reqBodyJSONObj.addProperty("created", traceLabel)
    reqBodyJSONObj.addProperty("description", traceDescription)
    reqBodyJSONObj.addProperty("worker", workerId)
    val reqBody = gson.toJson(reqBodyJSONObj)
    val tracePostRequest = Request.Builder()
        .url("$apiUrlPrefix/traces")
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .header("Connection", "close")
        .post(reqBody.toRequestBody(jsonMediaType))
        .build()
    return client.newCall(tracePostRequest).await()
}

suspend fun uploadFullTraceContent(
    packageName: String,
    traceLabel: String,
    traceDescription: String
) : Boolean {
    // try to upload VH content to AWS
    Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    val client = OkHttpClient()
    var isSuccessUpload: Boolean
    Log.i("full trace", "called!")
    try {
        // Upload VH file
        val gson = Gson()
        val screenIds = ArrayList<String>()
        val traceEvents: ArrayList<String> = getEvents(packageName, traceLabel)
        for (event: String in traceEvents) {
            // add POST request for screens
            var screenId: String
            val screenResBody = uploadScreen(client, gson, packageName, traceLabel, event)
            isSuccessUpload = screenResBody.isSuccessful
            val screenResBodyStr = screenResBody.body?.string() ?: ""
            val returnedScreen =
                gson.fromJson(screenResBodyStr, HashMap<String, Any?>()::class.java)
            screenId = (returnedScreen["_id"] as String?)!!
            screenResBody.body?.close()
            if (isSuccessUpload) {
                Log.i("api", "success upload screenshot")
                screenIds.add(screenId)
            } else {
                Log.e("api", "fail upload screenshot")
                return false
            }
            // add POST request for redactions
            val redactions: MutableSet<Redaction> =
                redactionMap[traceLabel]?.get(event) ?: mutableSetOf()
            for (redaction: Redaction in redactions) {
                val redactionResBody = uploadRedaction(client, gson, redaction, screenId)
                isSuccessUpload = redactionResBody.isSuccessful
                redactionResBody.body?.close()
                if (isSuccessUpload) {
                    Log.i("api", "success upload redaction")
                } else {
                    Log.e("api", "fail upload redaction")
                    return false
                }
            }
        }
        // add POST request for traces
        val traceResBody =
            uploadTrace(client, gson, screenIds, packageName, traceLabel, traceDescription)
        isSuccessUpload = traceResBody.isSuccessful
        traceResBody.body?.close()
        if (isSuccessUpload) {
            Log.i("api", "success upload trace")
        } else {
            Log.e("api", "fail upload trace")
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
        var gesturesMap: HashMap<String, HashMap<String, Gesture>> = HashMap()
        val redactionMap: HashMap<String, HashMap<String, MutableSet<Redaction>>> = HashMap()
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
                    val eventLabel = "$eventTime; $eventType"
                    // record vh and boxes
                    val vhString = currVHString
                    val vhBoxes = ArrayList(currVHBoxes)
                    // create screenshot
                    currentScreenshot = ScreenShot(currentBitmap!!, vhBoxes)
                    // add home button press to event
                    addEvent(null, currTouchPackage!!, false, eventLabel, null, currentScreenshot!!, vhString!!)
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
            val eventLabel = "$eventTime; $eventType"
            // check if event scroll, add delta coordinates
            var scrollCoords : Pair<Int, Int>? = null
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
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
            currentScreenshot = ScreenShot(currentBitmap!!, boxes)
            // create json string of VH
            var vh = currVHString
            if (vh == null) {
                vh = parseVHToJson(currRootWindow!!)
            }
            // add the event
            addEvent(node, packageName, isNewTrace, eventLabel, scrollCoords, currentScreenshot!!, vh)
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

    private fun createGesture(node: AccessibilityNodeInfo?, scrollCoords: Pair<Int, Int>?) : Gesture {
        val windowMetrics = windowManager!!.currentWindowMetrics
        val screenWidth = windowMetrics.bounds.width().toFloat()
        val screenHeight = windowMetrics.bounds.height().toFloat()
        val centerX: Float
        val centerY: Float
        var scrollDX = 0F
        var scrollDY = 0F
        if (node == null) {
            // take into account when node is empty (when home button is pressed)
            centerX = (screenWidth/2) / screenWidth
            centerY = (screenHeight-30) / screenHeight
        } else {
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            centerX = outbounds.centerX()/screenWidth
            centerY = outbounds.centerY()/screenHeight
            if (scrollCoords != null) {
                scrollDX = scrollCoords.first/screenWidth
                scrollDY = scrollCoords.second/screenHeight
            }
        }
        return Gesture(centerX, centerY, scrollDX, scrollDY)
    }

    private fun addEvent(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventLabel: String,
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
        val traceLabel: String
        val traceLayer = packageMap[packageName] ?: return
        val traceMap = traceLayer.map
        val traceList = traceLayer.list
        if (isNewTrace) {
            // this is a new trace
            traceLabel = eventLabel.substringBefore(";")
            traceList.add(traceLabel)
            traceLayer.list = traceList
            traceMap[traceLabel] = Layer()
            traceLayer.map = traceMap
        } else {
            traceLabel = traceList.last()
        }
        notifyTraceAdapter()
        // add the event and notify adapter the list has made changes
        val eventLayer = traceMap[traceLabel] ?: return
        val eventList = eventLayer.list
        eventList.add(eventLabel)
        eventLayer.list = eventList
        val eventMap = eventLayer.map
        eventMap[eventLabel] = Layer()
        eventLayer.map = eventMap
        notifyEventAdapter()
        // update gesture map with new gesture
        val gesture = createGesture(node, scrollCoords)
        if (gesturesMap.containsKey(traceLabel)) {
            gesturesMap[traceLabel]?.set(eventLabel, gesture)
        } else {
            gesturesMap[traceLabel] = HashMap()
            gesturesMap[traceLabel]?.set(eventLabel, gesture)
        }
        // add a new screenshot to list of traces
        val screenshotLayer = eventMap[eventLabel] ?: return
        screenshotLayer.screenShot = currentScreenShot
        val screenshotMap = screenshotLayer.map
        screenshotMap[eventLabel] = Layer()
        screenshotLayer.map = screenshotMap
        // add the view hierarchy to list of traces
        val viewHierarchyLayer = screenshotMap[eventLabel] ?: return
        val viewHierarchyList = ArrayList<String>()
        viewHierarchyList.add(viewHierarchy)
        viewHierarchyLayer.list = viewHierarchyList
    }

    override fun onInterrupt() {}
}
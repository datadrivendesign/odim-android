package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.PixelFormat
import android.graphics.Rect
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
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.MyAccessibilityService.Companion.gesturesMap
import edu.illinois.odim.MyAccessibilityService.Companion.redactionMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.gildor.coroutines.okhttp.await
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
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
const val apiUrlPrefix = "https://api.denizarsan.com"

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

private suspend fun uploadScreen(client: OkHttpClient,
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
    reqBodyJSONObj.add("gesture", gson.toJsonTree(gesture) as JsonObject)
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

private suspend fun uploadRedaction(client: OkHttpClient,
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

private fun getAppVersion(packageName: String): Long {
    val pInfo: PackageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
    return PackageInfoCompat.getLongVersionCode(pInfo)
}

private suspend fun uploadTrace(client: OkHttpClient,
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
    reqBodyJSONObj.addProperty("version", getAppVersion(packageName))
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
            val screenResBodyStr = screenResBody.body?.string() ?: "{}"
            val returnedScreen =
                gson.fromJson(screenResBodyStr, JsonObject::class.java)
            screenId = returnedScreen.getAsJsonPrimitive("_id").asString
            screenResBody.body?.close()
            if (isSuccessUpload && screenId.isNotEmpty()) {
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
        Log.e("edu.illinois.odim", "Upload failed", exception)
        return false
    }
    return isSuccessUpload
}

class MyAccessibilityService : AccessibilityService() {
    private var currentBitmap: Bitmap? = null
    private var currentScreenshot: ScreenShot? = null
    private var isScreenEventPaired: Boolean = false
    private var windowManager: WindowManager? = null
    var currRootWindow: AccessibilityNodeInfo? = null
    var currVHBoxes: ArrayList<Rect> = ArrayList()
    var currVHString: String? = null
    private var lastEventPackageName: String = "null"
    private var lastTouchPackage: String = "null"
    var currTouchTime: String? = null
    private val odimPackageName = "edu.illinois.odim"
    private val settingsPackageName = "com.android.settings"

    companion object {
        lateinit var appContext: Context
        lateinit var appLauncherPackageName: String
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
        info.notificationTimeout = 500
        info.packageNames = null
        serviceInfo = info

        val intent = Intent("android.intent.action.MAIN")
        intent.addCategory("android.intent.category.HOME")
        appLauncherPackageName = packageManager.resolveActivity(
                                    intent,
                                    PackageManager.MATCH_DEFAULT_ONLY
                                )!!.activityInfo.packageName

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
                if (lastTouchPackage != "null" &&
                    lastTouchPackage != odimPackageName &&
                    lastTouchPackage != settingsPackageName &&
                    lastTouchPackage != appLauncherPackageName &&
                    lastTouchPackage == lastEventPackageName &&
                    currRootWindow?.packageName.toString() != lastTouchPackage &&
                    (currRootWindow?.packageName.toString() == "null" ||
                            currRootWindow?.packageName.toString() == appLauncherPackageName)) {
                    Log.i("home", "button pressed")
                    // record event type as home button press
                    isScreenEventPaired = true
                    val eventTime = currTouchTime
                    val eventType = "TYPE_HOME_PRESSED"
                    val eventLabel = "$eventTime; $eventType"
                    // record vh and boxes
                    val vhString = currVHString
                    // we need this line, if we use currVHBoxes in screenshot it will passed by
                    // reference and boxes will be lost when clear() is called
                    val vhBoxes = ArrayList(currVHBoxes)
                    // create screenshot
                    currentScreenshot = ScreenShot(currentBitmap!!, vhBoxes)
                    // add home button press to event
                    addEvent(null, lastTouchPackage, false, eventLabel, null, currentScreenshot!!, vhString!!)
                    // reset everything
                    currVHString = null
                    currVHBoxes.clear()
                    lastTouchPackage = currRootWindow?.packageName.toString()
                    return false
                }
                // reset vh and boxes to record next screen touch
                if (currVHBoxes.isNotEmpty()) {
                    currVHBoxes.clear()
                }
                currVHString = null
                Log.i("odim", "touch package " + (currRootWindow?.packageName ?: "null"))
                if (currRootWindow == null ||
                    currRootWindow!!.packageName == odimPackageName ||
                    currRootWindow!!.packageName == appLauncherPackageName ||
                    currRootWindow!!.packageName == settingsPackageName
                ) {
                    lastTouchPackage = currRootWindow?.packageName.toString()
                    return false
                }

                // get view hierarchy at this time
                val byteStream = ByteArrayOutputStream()
                val jsonWriter = JsonWriter(OutputStreamWriter(byteStream, "UTF-8"))
                Log.i("odim", "before parse")
                currVHBoxes = ArrayList()
                currVHString = currRootWindow.toString()
                parseVHToJson(currRootWindow!!, currVHBoxes, jsonWriter)
                jsonWriter.flush()
                Log.i("odim", "after parse")
                currVHString = String(byteStream.toByteArray())
                Log.i("currRootWindow", "update window")
                byteStream.close()
                jsonWriter.close()

                takeScreenshot(
                    DEFAULT_DISPLAY,
                    appContext.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            currTouchTime = getInteractionTime()
                            // take screenshot and record current bitmap globally
                            // update screen
                            isScreenEventPaired = false
                            currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                            result.hardwareBuffer.close()
                            Log.i("screenshot", "update screen")
                            lastTouchPackage = currRootWindow!!.packageName.toString()
                        }

                        override fun onFailure(errCode: Int) {
                            Log.e("edu.illinois.odim", "Screenshot error code: $errCode")
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
        // TODO: move this upload function
//        val connMgr: ConnectivityManager =
//            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val networkCapabilities = connMgr.activeNetwork ?: return
//        val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) ?: return
//        val isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
//        if (!isWifiConn) {
//            return
//        }

        // do not record unnecessary app packages
        // however, record the last screeen of a trace (app package -> nexuslauncher)

        Log.i("event", event?.packageName.toString())
        if (event == null) {
            return
        }
        val packageName = event.packageName.toString()
        if (packageName == "null" ||
            packageName == odimPackageName ||
            packageName == appLauncherPackageName ||
            packageName == settingsPackageName
        ) {
            lastEventPackageName = packageName
            return
        }

        var isNewTrace = false
        if (packageName != lastEventPackageName) {
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
            val eventTime = currTouchTime //getInteractionTime()
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
            // create json string of VH
            var vh = currVHString
            if (vh == null) {
                val byteStream = ByteArrayOutputStream()
                val jsonWriter = JsonWriter(OutputStreamWriter(byteStream, "UTF-8"))
                Log.i("odim", "before parse")
                currVHBoxes.clear()
                parseVHToJson(currRootWindow!!, currVHBoxes, jsonWriter)
                jsonWriter.flush()
                Log.i("odim", "after parse")
                vh = String(byteStream.toByteArray())
                jsonWriter.close()
                byteStream.close()
            }
            // we need this line, if we use currVHBoxes in Screenshot it will passed by reference
            // and boxes will be lost when clear() is called
            val boxes = ArrayList(currVHBoxes)
            currentScreenshot = ScreenShot(currentBitmap!!, boxes)
            // add the event
            addEvent(node, packageName, isNewTrace, eventLabel, scrollCoords, currentScreenshot!!, vh)
            lastEventPackageName = packageName
        }
    }

    private fun parseVHToJson(node: AccessibilityNodeInfo,
                              boxes: ArrayList<Rect>,
                              jsonWriter: JsonWriter) {
        try {
            jsonWriter.beginObject()
            // add children to json
            jsonWriter.name("children")
            jsonWriter.beginArray()
            for (i in 0 until node.childCount) {
                val currentNode = node.getChild(i)
                if (currentNode != null) {
                    parseVHToJson(currentNode, boxes, jsonWriter)
                }
            }
            jsonWriter.endArray()
            // write coordinates as string to json
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            jsonWriter.name("bounds_in_screen").value(outbounds.toString())
            boxes.add(outbounds)
            // parent and class name
            jsonWriter.name("package_name").value(node.packageName.toString())
            jsonWriter.name("class_name").value(node.className.toString())
            if (node.parent != null) {
                val parentClass = node.parent.className ?: "none"
                jsonWriter.name("parent").value(parentClass.toString())
            } else {
                jsonWriter.name("parent").value("none")
            }
            // add vh element boolean properties
            jsonWriter.name("scrollable").value(node.isScrollable)
            jsonWriter.name("clickable").value(node.isClickable)
            jsonWriter.name("focusable").value(node.isFocusable)
            jsonWriter.name("focused").value(node.isFocused)
            jsonWriter.name("checkable").value(node.isCheckable)
            jsonWriter.name("checked").value(node.isChecked)
            jsonWriter.name("long-clickable").value(node.isLongClickable)
            jsonWriter.name("enabled").value(node.isEnabled)
            jsonWriter.name("visibility").value(node.isVisibleToUser)
            jsonWriter.name("selected").value(node.isSelected)
            // write text and content description to json
            val contentDesc = node.contentDescription ?: "none"
            jsonWriter.name("content-desc").value(contentDesc.toString())
            val text = node.text
            if (text != null) {
                val textField = text.toString()
                if (textField.isNotEmpty()) {
                    jsonWriter.name("text_field").value(textField)
                }
            }
            jsonWriter.name("children_count").value(node.childCount)
            jsonWriter.endObject()
        } catch (e: IOException) {
            Log.e("edu.illinois.odim", "IOException", e)
        }
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
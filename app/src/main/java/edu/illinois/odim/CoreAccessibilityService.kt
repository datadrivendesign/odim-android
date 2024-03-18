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
import android.graphics.BitmapFactory
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
import androidx.core.content.pm.PackageInfoCompat
import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.MyAccessibilityService.Companion.isAppContextInitialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.gildor.coroutines.okhttp.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

val kotlinMapper = ObjectMapper().registerKotlinModule()
internal var workerId = "test_user"
const val apiUrlPrefix = "https://api.denizarsan.com"
const val TRACES_DIR = "TRACES"
const val VH_PREFIX = "vh-"
const val GESTURE_PREFIX = "gesture-"
const val REDACT_PREFIX = "redact-"

fun listPackages(): MutableList<String> {
    if (!isAppContextInitialized()) {
        return arrayListOf()
    }
    val packageDir = File(appContext.filesDir, TRACES_DIR)
    Log.i("loadPackages", packageDir.absolutePath)

    return if(packageDir.exists()) {
        packageDir.listFiles()?.filter {
            it.isDirectory
        }?.map {
            it.name
        }?.toMutableList() ?: arrayListOf()
    } else {
        packageDir.mkdir()
        arrayListOf()
    }
}

fun listTraces(packageName: String): MutableList<String> {
    val traceDir = File(appContext.filesDir, "$TRACES_DIR/$packageName")
    return if (traceDir.exists()) {
        return traceDir.listFiles()?.filter {
            it.isDirectory
        }?.map {
            it.name
        }?.sortedDescending()?.toMutableList() ?: arrayListOf()
    } else {
        traceDir.mkdirs()
        arrayListOf()
    }
}

fun listEvents(packageName: String, trace: String): MutableList<String>  {
    val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace")
    return if (eventDir.exists()) {
        eventDir.listFiles()?.filter {
            it.isDirectory
        }?.map {
            it.name
        }?.sorted()?.toMutableList() ?: arrayListOf()
    } else {
        eventDir.mkdirs()
        arrayListOf()
    }
}

fun loadScreenshot(packageName: String, trace: String, event: String): Bitmap {
    val screenFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$event.png")
    if (screenFile.exists()) {
        Log.i("loadScreen", "loaded")
        val bitmapBytes = screenFile.readBytes()
        return BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.size)
    } else {
        throw FileNotFoundException("Screenshot was not found")
    }
}

fun saveScreenshot(packageName: String, trace: String, event: String, screen: Bitmap): Boolean {
    return try {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        if (!eventDir.exists()) {
            eventDir.mkdirs()
        }
        val fileScreen = File(eventDir, "$event.png")
        FileOutputStream(fileScreen, false).use {stream ->
            if(!screen.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IOException("Couldn't save bitmap")
            } else {
                Log.i("saveScreen", "saved")
            }
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

fun loadVH(packageName: String, trace: String, event: String): String {
    val jsonFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$event.json")
    if (jsonFile.exists()) {
        return jsonFile.readText(Charsets.UTF_8)
    } else {
        throw FileNotFoundException("VH was not found")
    }
}

fun saveVH(packageName: String, trace: String, event: String, vhJsonString: String) : Boolean {
    return try {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        if (!eventDir.exists()) {
            eventDir.mkdirs()
        }
        val fileVH = File(eventDir, "$VH_PREFIX$event.json")
        FileOutputStream(fileVH, false).use {stream ->
            stream.write(vhJsonString.toByteArray())
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}
fun loadGestures(packageName: String, trace: String, event: String): Gesture {
    val jsonFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$GESTURE_PREFIX$event.json")
    if (jsonFile.exists()) {
        val gestureJsonString = jsonFile.readText(Charsets.UTF_8)
        return kotlinMapper.readValue<Gesture>(gestureJsonString)
    } else {
        throw FileNotFoundException("Gesture was not found")
    }
}

fun saveGestures(packageName: String, trace: String, event: String, gesture: Gesture) : Boolean {
    return try {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        if (!eventDir.exists()) {
            eventDir.mkdirs()
        }
        val fileGestures = File(eventDir, "$GESTURE_PREFIX$event.json")
        FileOutputStream(fileGestures, false).use {stream ->
            val gestureJson = kotlinMapper.writeValueAsBytes(gesture)
            stream.write(gestureJson)
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

fun loadRedactions(packageName: String, trace: String, event: String): MutableSet<Redaction> {
    val jsonFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$REDACT_PREFIX$event.json")
    return if (jsonFile.exists()) {
        val redactJsonString = jsonFile.readText(Charsets.UTF_8)
        Log.d("redact string", redactJsonString)
        kotlinMapper.readValue<MutableSet<Redaction>>(redactJsonString)
    } else {
        mutableSetOf()
    }
}

fun saveRedactions(packageName: String, trace: String, event: String, redaction: Redaction) : Boolean {
    return try {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        if (!eventDir.exists()) {
            eventDir.mkdirs()
        }
        val redactions = mutableSetOf<Redaction>()
        val fileRedacts = File(eventDir, "$REDACT_PREFIX$event.json")
        if (fileRedacts.exists()) {
            val currRedacts = loadRedactions(packageName, trace, event)
            redactions.addAll(currRedacts)
        }
        redactions.add(redaction)

        FileOutputStream(fileRedacts, false).use {stream ->
            stream.write(kotlinMapper.writeValueAsBytes(redactions))
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

private suspend fun uploadScreen(client: OkHttpClient,
                         mapper: ObjectMapper,
                         packageName: String,
                         traceLabel: String,
                         eventLabel: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    // get bitmap as bit64 string
    ByteArrayOutputStream().use {
        val bitmap = loadScreenshot(packageName, traceLabel, eventLabel)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        val bitmapBase64 = Base64.encodeToString(it.toByteArray(), Base64.DEFAULT)
        // retrieve vh
        val vhString = loadVH(packageName, traceLabel, eventLabel)
        // retrieve screen timestamp
        val screenCreatedAt = eventLabel.substringBefore(";")
        // retrieve gesture for screen
        val gesture: Gesture =  loadGestures(packageName, traceLabel, eventLabel)
        // construct request body
        val reqBodyJSONObj = mapper.createObjectNode()
        reqBodyJSONObj.put("vh", vhString)
        reqBodyJSONObj.put("img", bitmapBase64)
        reqBodyJSONObj.put("created", screenCreatedAt)
        reqBodyJSONObj.set<JsonNode>("gesture", mapper.valueToTree(gesture) as JsonNode)
        val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
        // run the POST request
        val screenPostRequest = Request.Builder()
            .url("$apiUrlPrefix/screens")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .post(reqBody.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(screenPostRequest).await()
    }
}

private suspend fun uploadRedaction(client: OkHttpClient,
                            mapper: ObjectMapper,
                            redaction: Redaction,
                            screenId: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val reqBodyJSONObj = mapper.valueToTree<ObjectNode>(redaction)
    reqBodyJSONObj.put("screen", screenId)
    val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
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
                        mapper: ObjectMapper,
                        screenIds: ArrayList<String>,
                        packageName: String,
                        traceLabel: String,
                        traceDescription: String): Response {
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val reqBodyJSONObj = mapper.createObjectNode()// JsonObject()
    reqBodyJSONObj.putArray("screens").addAll(mapper.valueToTree(screenIds) as ArrayNode)
    reqBodyJSONObj.put("app", packageName)
    reqBodyJSONObj.put("created", traceLabel)
    reqBodyJSONObj.put("description", traceDescription)
    reqBodyJSONObj.put("worker", workerId)
    reqBodyJSONObj.put("version", getAppVersion(packageName))
    val reqBody = mapper.writeValueAsString(reqBodyJSONObj)

    val tracePostRequest = Request.Builder()
        .url("$apiUrlPrefix/traces")
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .header("Connection", "close")
        .post(reqBody.toRequestBody(jsonMediaType))
        .build()
    return client.newCall(tracePostRequest).await()
}

fun checkWiFiConnection(): Boolean {
    val connMgr: ConnectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager  // Context.CONNECTIVITY_SERVICE
    val networkCapabilities = connMgr.activeNetwork //?: return false
    if (networkCapabilities == null) {
        Log.i("error upload", "no Wi-Fi connection")
        return false
    }
    val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) //?: return false
    if (activeNetwork == null) {
        Log.i("error upload", "no Wi-Fi connection")
        return false
    }
    val isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    if (!isWifiConn) {
        Log.i("error upload", "no Wi-Fi connection")
        return false
    }
    return true
}
suspend fun uploadFullTraceContent(
    packageName: String,
    traceLabel: String,
    traceDescription: String
) : Boolean {
    // try to check network status, found non-deprecated solution from:
    // https://stackoverflow.com/questions/49819923/kotlin-checking-network-status-using-connectivitymanager-returns-null-if-networ
    // specifically answered by @AliSh to check if connected to wifi or mobile
    val isWifiConnected = checkWiFiConnection()
    if (!isWifiConnected) {
        // TODO: give notification about not connected to wifi
        Toast.makeText(appContext, "Cannot upload without connection to Wi-Fi!", Toast.LENGTH_SHORT).show()
        return false
    }

    Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    val client = OkHttpClient()
    var isSuccessUpload: Boolean
    try {
        // Upload VH file
        val mapper = ObjectMapper()
        val screenIds = ArrayList<String>()
        val traceEvents: List<String> = listEvents(packageName, traceLabel)// getEvents(packageName, traceLabel)
        for (event: String in traceEvents) {
            // add POST request for screens
            var screenId: String
            uploadScreen(client, mapper, packageName, traceLabel, event).use {
                isSuccessUpload = it.isSuccessful
                val screenResBodyStr = it.body?.string() ?: "{}"
                val returnedScreen = mapper.readTree(screenResBodyStr)
                screenId = returnedScreen.get("_id").asText()
            }

            if (isSuccessUpload && screenId.isNotEmpty()) {
                Log.i("api", "success upload screenshot")
                screenIds.add(screenId)
            } else {
                Log.e("api", "fail upload screenshot")
                return false
            }
            // add POST request for redactions
            val redactions: MutableSet<Redaction> = loadRedactions(packageName, traceLabel, event)
            for (redaction: Redaction in redactions) {
                uploadRedaction(client, mapper, redaction, screenId).use {
                    isSuccessUpload = it.isSuccessful
                }
                if (isSuccessUpload) {
                    Log.i("api", "success upload redaction")
                } else {
                    Log.e("api", "fail upload redaction")
                    return false
                }
            }
        }
        // add POST request for traces
        uploadTrace(client, mapper, screenIds, packageName, traceLabel, traceDescription).use {
            isSuccessUpload = it.isSuccessful
        }

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
    private var isScreenEventPaired: Boolean = false
    private var windowManager: WindowManager? = null
    var currRootWindow: AccessibilityNodeInfo? = null
    var currVHString: String? = null
    private var lastEventPackageName: String = "null"
    private var lastTouchPackage: String = "null"
    var currTouchTime: String? = null
    private val odimPackageName = "edu.illinois.odim"
    private val settingsPackageName = "com.android.settings"

    companion object {
        lateinit var appContext: Context
        fun isAppContextInitialized(): Boolean { return ::appContext.isInitialized }
        lateinit var appLauncherPackageName: String
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
                        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_VIEW_SELECTED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.notificationTimeout = 300
        info.packageNames = null
        info.flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
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
        // record screenshot and view hierarchy when screen touch is detected with separate coroutines
        layout.setOnTouchListener (object : View.OnTouchListener {
            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                currRootWindow = rootInActiveWindow
                currVHString = null
                if (currRootWindow?.packageName.toString() == "null" ||
                    currRootWindow!!.packageName == odimPackageName ||
                    currRootWindow!!.packageName == appLauncherPackageName ||
                    currRootWindow!!.packageName == settingsPackageName
                ) {
                    lastTouchPackage = currRootWindow?.packageName.toString()
                    return false
                }

                // get view hierarchy at this time
                GlobalScope.launch(Dispatchers.IO) {
                    val fullTime = measureTimeMillis {
                        ByteArrayOutputStream().use { baos ->
                            JsonFactory().createGenerator(baos, JsonEncoding.UTF8).use { writer ->
                                parseVHToJson(currRootWindow!!, writer)
                                writer.flush()
                                currVHString = String(baos.toByteArray())
                                if (currVHString.isNullOrEmpty()) {
                                    currVHString = currRootWindow.toString()
                                }
                                Log.i("currRootWindow", "update VH")
                            }
                        }
                    }
                    Log.d("MEASURE_TIME", "Parse time took ${fullTime}ms")
                }

                GlobalScope.launch(Dispatchers.Default) {
                    val time = measureTimeMillis {
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
                    }
                    Log.d("MEASURE_TIME", "Screenshot time took ${time}ms")
                }
                return false
            }
        })
    }

    // TODO: check if can break parts into coroutine and multithread
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.i("MEASURE_EVENT", event?.packageName.toString())
        if (event == null) {
            return
        }
        if (isScreenEventPaired) {  // ignore if screenshot already paired with event
            return
        }
        var currEventPackageName = event.packageName.toString()
        // before we decide if new trace, we should check if BACK or HOME button
        val systemUIPackageName = "com.android.systemui"
        val backBtnText = "[Back]"
        val homeBtnText = "[Home]"
        val overviewBtnTexts = listOf("[Overview]", "[Recents]") // TODO: do we want this button to be used?
        val isBackBtnPressed = (event.packageName == systemUIPackageName) && (event.text.toString() == backBtnText)
        val isHomeBtnPressed = (event.packageName == systemUIPackageName) && (event.text.toString() == homeBtnText)
        val isOverviewBtnPressed = (event.packageName == systemUIPackageName) && (overviewBtnTexts.contains(event.text.toString()))
        // ignore new systemui package name update if home or back button pressed
        var isNewTrace = false
        if (currEventPackageName != lastEventPackageName && !isBackBtnPressed && !isHomeBtnPressed && !isOverviewBtnPressed) {
            // continue trace even if back and home button pressed
            isNewTrace = true
        }
        if (isBackBtnPressed || isHomeBtnPressed || isOverviewBtnPressed) {
            Log.i("pressed", "systemui buttons")
            currEventPackageName = lastEventPackageName
        }
        Log.i("currEventPackageName", currEventPackageName)
        if (currEventPackageName == "null" ||
            currEventPackageName == odimPackageName ||
            currEventPackageName == appLauncherPackageName ||
            currEventPackageName == settingsPackageName
        ) {
            lastEventPackageName = currEventPackageName
            return
        }
        // construct interaction event
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) ||
            (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {
            if (currentBitmap == null) {
                return
            }
            isScreenEventPaired = true
            // check if event scroll, add delta coordinates
            var scrollCoords : Pair<Int, Int>? = null
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                scrollCoords = Pair(event.scrollDeltaX, event.scrollDeltaY)
            }
            // get vh element interacted with and parse view hierarchy of current screen if null
            val node = event.source ?: return
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            // create json string of VH
            val vh = currVHString ?: return
            // Parse event description
            val eventTime = currTouchTime //getInteractionTime()
            val eventType = eventTypeToString(event.eventType)
            val eventLabel = "$eventTime; $eventType"
            // add the event
            addEvent(node, currEventPackageName, isNewTrace, eventLabel, scrollCoords, currentBitmap!!, vh)
            lastEventPackageName = if (isBackBtnPressed) {
                currEventPackageName
            } else {
                event.packageName.toString()
            }
        }
    }

//    fun longLog(str: String) {
//        if (str.length > 4000) {
//            Log.d("MEASURE_VH", str.substring(0, 4000))
//            longLog(str.substring(4000))
//        } else Log.d("MEASURE_VH", str)
//    }

    private fun parseVHToJson(node: AccessibilityNodeInfo,
                              jsonWriter: JsonGenerator) {
        try {
            // "visit" the node on tree
            jsonWriter.writeStartObject()
            // write coordinates as string to json
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            jsonWriter.writeStringField("bounds_in_screen", outbounds.flattenToString())
            // parent and class name
            jsonWriter.writeStringField("package_name", node.packageName?.toString() ?: "null")
            jsonWriter.writeStringField("class_name", node.className?.toString() ?: "null")
            if (node.parent != null) {
                jsonWriter.writeStringField("parent", node.parent.className?.toString() ?: "none")
            } else {
                jsonWriter.writeStringField("parent", "none")
            }
            // add vh element boolean properties
            jsonWriter.writeBooleanField("scrollable", node.isScrollable)
            jsonWriter.writeBooleanField("clickable", node.isClickable)
            jsonWriter.writeBooleanField("focusable", node.isFocusable)
            jsonWriter.writeBooleanField("focused", node.isFocused)
            jsonWriter.writeBooleanField("checkable", node.isCheckable)
            jsonWriter.writeBooleanField("checked", node.isChecked)
            jsonWriter.writeBooleanField("long-clickable", node.isLongClickable)
            jsonWriter.writeBooleanField("enabled", node.isEnabled)
            jsonWriter.writeBooleanField("visibility", node.isVisibleToUser)
            jsonWriter.writeBooleanField("selected", node.isSelected)
            // write text and content description to json
            jsonWriter.writeStringField("content-desc", node.contentDescription?.toString() ?: "none")
            val text = node.text
            if (text != null) {
                jsonWriter.writeStringField("text_field", text.toString())
            }
            jsonWriter.writeNumberField("children_count", node.childCount)
            // recursive call
            // add children to json
            jsonWriter.writeFieldName("children")
            jsonWriter.writeStartArray()
            for (i in 0 until node.childCount) {
                val currentNode = node.getChild(i)
                if (currentNode != null) {
                    parseVHToJson(currentNode, jsonWriter)
                }
            }
            // sub-problem
            jsonWriter.writeEndArray()
            jsonWriter.writeEndObject()
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

    /**
     *
     */
    private fun addEvent(
        node: AccessibilityNodeInfo?,
        packageName: String,
        isNewTrace: Boolean,
        eventLabel: String,
        scrollCoords: Pair<Int, Int>?,
        currentScreenShot: Bitmap,
        viewHierarchy: String
    ) {
        val traceList = listTraces(packageName)// traceLayer.list
        val traceLabel = if (isNewTrace) {
            eventLabel.substringBefore(";")
        } else {
            traceList.first()  // trace is sorted in descending order
        }
        // update gesture map with new gesture
        val gesture = createGesture(node, scrollCoords)
        saveGestures(packageName, traceLabel, eventLabel, gesture)
        // add a new screenshot to list of traces
        saveScreenshot(packageName, traceLabel, eventLabel, currentScreenShot)
        // add the view hierarchy to list of traces
        saveVH(packageName, traceLabel, eventLabel, viewHierarchy)
    }

    override fun onInterrupt() {}
}
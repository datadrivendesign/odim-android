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
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.kotlin.core.Amplify
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.StorageException
import com.amplifyframework.storage.options.StorageUploadFileOptions
import com.amplifyframework.storage.s3.AWSS3StoragePlugin
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
suspend fun uploadFile(
    packageId: String,
    trace_number: String,
    action_number: String,
    vh_content: String,
    bitmap: Bitmap?,
    uploadButton: Button?,
    vh_app_ctx: Context
) {
    val options: StorageUploadFileOptions = StorageUploadFileOptions.builder()
        .accessLevel(StorageAccessLevel.PRIVATE)
        .build()
    val baseLocation = "$userId/$packageId/$trace_number/"
    val initButtonText = uploadButton?.text
    val appCtx = MyAccessibilityService.appContext
    // try to upload VH content to AWS
    val vhFile = File(MyAccessibilityService.appContext.filesDir, "vh")
    val viewHierarchyLocation = baseLocation + "view_hierarchies" + "/" + action_number
    try {
        // get VH content and write to the to-be-uploaded file
        val writer = BufferedWriter(FileWriter(vhFile))
        writer.append(vh_content)
        writer.close()
        // Upload VH file
        val vhUpload = Amplify.Storage.uploadFile(viewHierarchyLocation, vhFile, options)
        val vhProgressJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            async {
                vhUpload.progress().collect {
                    if (uploadButton != null) {
                        uploadButton.text = appCtx.getString(R.string.upload_vh_progress, it.fractionCompleted * 100)
                    }
            } }
        }
        // update if successfully uploaded VH
        val vhResult = vhUpload.result()
        vhProgressJob.cancel()
        Log.i("MyAmplifyApp", "Successfully uploaded: " + vhResult.key)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = MyAccessibilityService.appContext.getString(R.string.upload_vh_success)
            }
        }
    } catch (error: StorageException) {
        Log.e("MyAmplifyApp", "Upload failed", error)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = appCtx.getString(R.string.upload_vh_fail)
            }
            Toast.makeText(vh_app_ctx, appCtx.getString(R.string.upload_vh_toast_fail), Toast.LENGTH_LONG).show()
        }
    } catch (exception: IOException) {
        Log.e("MyAmplifyApp", "Write to file failed", exception)
    } catch (exception: Exception) {
        Log.e("MyAmplifyApp", "Upload filed", exception)
    }
    // try to upload screenshot content to AWS
    val screenshotFile = File(appCtx.filesDir, "screenshot")
    val screenshotLocation = baseLocation + "screenshots" + "/" + action_number
    try {
        // write screenshot to a file for upload
        FileOutputStream(screenshotFile).use { out ->
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
        }
        // upload gestures file
        val screenUpload = Amplify.Storage.uploadFile(screenshotLocation, screenshotFile, options)
        val screenProgressJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            async {
                screenUpload.progress().collect {
                    if (uploadButton != null) {
                        uploadButton.text = appCtx.getString(
                            R.string.upload_screen_progress,
                            it.fractionCompleted * 100)
                    }
                }
            }
        }
        // update button if successfully uploaded screenshot
        val screenResult = screenUpload.result()
        screenProgressJob.cancel()
        Log.i("MyAmplifyApp", "Successfully uploaded: " + screenResult.key)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = appCtx.getString(R.string.upload_screen_success)
            }
        }
    } catch (error: StorageException) {
        Log.e("MyAmplifyApp", "Upload failed", error)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = appCtx.getString(R.string.upload_screen_fail)
            }
            Toast.makeText(
                vh_app_ctx,
                appCtx.getString(R.string.upload_screen_toast_fail),
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (exception: IOException) {
        Log.e("MyAmplifyApp", "Write to file failed", exception)
    } catch (exception: Exception) {
        Log.e("MyAmplifyApp", "Upload filed", exception)
    }

    // Write json gestures to file for upload
    val gson = Gson()
    var json: String = gson.toJson(MyAccessibilityService.gesturesMap)
    json = json.replace("\\\\".toRegex(), "")
    val gestureFile = File(MyAccessibilityService.appContext.filesDir, "vh")
    val gestureLocation = baseLocation + "gestures"
    try {
        val writer = BufferedWriter(FileWriter(gestureFile))
        writer.append(json)
        writer.close()
        // upload gestures file
        val gestureUpload = Amplify.Storage.uploadFile(gestureLocation, gestureFile, options)
        val gestureProgressJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            async {
                gestureUpload.progress().collect {
                    if (uploadButton != null) {
                        uploadButton.text = appCtx.getString(
                            R.string.upload_gesture_progress,
                            it.fractionCompleted * 100)
                    }
                }
            }
        }
        // update button and make toast if successfully uploaded gesture
        val gestureResult = gestureUpload.result()
        gestureProgressJob.cancel()
        Log.i("MyAmplifyApp", "Successfully uploaded: " + gestureResult.key)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = initButtonText
            }
            Toast.makeText(
                vh_app_ctx,
                appCtx.getString(R.string.upload_all_toast_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (error: StorageException) {
        Log.e("MyAmplifyApp", "Upload failed", error)
        CoroutineScope(Dispatchers.Main + Job()).launch {
            if (uploadButton != null) {
                uploadButton.text = appCtx.getString(R.string.upload_gesture_fail)
            }
            Toast.makeText(
                vh_app_ctx,
                appCtx.getString(R.string.upload_gesture_toast_fail),
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (exception: IOException) {
        Log.e("MyAmplifyApp", "Write to file failed", exception)
    } catch (exception: Exception) {
        Log.e("MyAmplifyApp", "Upload failed", exception)
    }
}

class MyAccessibilityService : AccessibilityService() {

    private var lastPackageName: String? = null

    private var currentBitmap: Bitmap? = null

    private var currentScreenshot: ScreenShot? = null

    lateinit var future: ScheduledFuture<*>
    val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    companion object {
        lateinit var appContext: Context

        var gesturesMap: HashMap<String, String>? = null
    }

//    private val userId = "test_user15"

    private suspend fun amplifySignUp() {
        val options = AuthSignUpOptions.builder()
            .userAttribute(AuthUserAttributeKey.email(), "carlguo2@illinois.edu")
            .build()
        try {
            val result = Amplify.Auth.signUp("carl_and_rizky", "dddg_ODIM_mobi", options
            )
            Log.i("AuthQuickStart", "Result: $result")
        } catch (error: AuthException) {
            Log.e("AuthQuickStart", "Sign up failed", error)
        }

        try {
            val result = Amplify.Auth.confirmSignUp("carl_and_rizky", "815098",)
            Log.i("AuthQuickstart",
                if (result.isSignUpComplete) { "Confirm signUp succeeded" }
                else { "Confirm sign up not complete" }
            )
        } catch (error: AuthException) {
            Log.e("AuthQuickstart", error.toString())
        }
    }

    private suspend fun amplifyLogIn() {
        try {
            val result = Amplify.Auth.signIn("carl_and_rizky", "dddg_ODIM_mobi")
            Log.i("AuthQuickstart",
                if (result.isSignInComplete) "Sign in succeeded" else "Sign in not complete"
            )
        } catch (error: AuthException) {
            Log.e("AuthQuickstart", error.toString())
        }

    }

    override fun onServiceConnected() {
        // Create the service
        Log.i("onServiceConnected", "Accessibility Service Connected")
        appContext = applicationContext
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

//        amplifySignUp()
        CoroutineScope(Dispatchers.IO).launch() {
            amplifyLogIn()
        }

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
                event.packageName == "edu.illinois.odim") {
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
//        Log.i("Box size", java.lang.String.valueOf(boxes.size))
        return boxes
    }

    private fun recordScreenPeriodically() {
        future = scheduledExecutorService.scheduleAtFixedRate({
            takeScreenshot(DEFAULT_DISPLAY, scheduledExecutorService, object: TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
//                    Log.i("Screenshot:", "Take screenshot success")
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
        var x1 = outbounds.left
        var y1 = outbounds.top
        var x2 = outbounds.right
        var y2 = outbounds.bottom
        map["bounds_in_screen"] = outbounds.toString() // "[$x1, $y1, $x2, $y2]"
        map["visibility"] = java.lang.String.valueOf(node.isVisibleToUser)
        if (node.contentDescription != null) {
            map["content-desc"] = node.contentDescription.toString()
        } else {
            map["content-desc"] = "none"
        }
        node.getBoundsInParent(outbounds)   // TODO: why do we need this?
        x1 = outbounds.left
        y1 = outbounds.top
        x2 = outbounds.right
        y2 = outbounds.bottom
        map["bounds_in_parent"] = outbounds.toString() // "[$x1, $y1, $x2, $y2]"
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
        map["children"] = childrenVH

        //map.put("to_string", node.toString());
        val gson = Gson()
        var json: String = gson.toJson(map)
//        json = json.replace("\\\\".toRegex(), "")
        return json
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
        notifyVHAdapter()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        future.cancel(true)
        return super.onUnbind(intent)
    }
}
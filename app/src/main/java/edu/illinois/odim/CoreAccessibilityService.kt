package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import edu.illinois.odim.dataclasses.CaptureStore
import edu.illinois.odim.dataclasses.CaptureTask
import edu.illinois.odim.dataclasses.Gesture
import edu.illinois.odim.utils.LocalStorageOps.GESTURE_PREFIX
import edu.illinois.odim.utils.LocalStorageOps.listEventData
import edu.illinois.odim.utils.LocalStorageOps.listEvents
import edu.illinois.odim.utils.LocalStorageOps.listTraces
import edu.illinois.odim.utils.LocalStorageOps.renameEvent
import edu.illinois.odim.utils.LocalStorageOps.renameScreenshot
import edu.illinois.odim.utils.LocalStorageOps.renameVH
import edu.illinois.odim.utils.LocalStorageOps.saveGesture
import edu.illinois.odim.utils.LocalStorageOps.saveScreenshot
import edu.illinois.odim.utils.LocalStorageOps.saveCapture
import edu.illinois.odim.utils.LocalStorageOps.saveVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.system.measureTimeMillis

internal var workerId = "test_user"
var DELIM = "; "

class MyAccessibilityService : AccessibilityService() {
    private var currentBitmap: Bitmap? = null
    private var isNewTrace: Boolean = false
    private var windowManager: WindowManager? = null
    private var currRootWindow: AccessibilityNodeInfo? = null
    private var currVHString: String? = null
    private var lastTouchPackageName: String = "null"
    private var currTouchTime: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // static variables
    companion object {
        lateinit var appContext: Context
        var captureTask: CaptureTask? = null
        fun isAppContextInitialized(): Boolean { return ::appContext.isInitialized }
        lateinit var APP_LAUNCHER_PACKAGE: String
        private const val TIME_DIFF = 100
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val ODIM_PACKAGE = "edu.illinois.odim"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        // Create the service
        Log.i("onServiceConnected", "Accessibility Service Connected")
        appContext = applicationContext
        // filter out unwanted packages from recording
        val intent = Intent("android.intent.action.MAIN")
        intent.addCategory("android.intent.category.HOME")
        APP_LAUNCHER_PACKAGE = packageManager.resolveActivity(
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
        layout.setOnTouchListener { _, _ ->
            currRootWindow = rootInActiveWindow
            currVHString = null
            if (currRootWindow?.packageName.toString() == "null" ||
                currRootWindow!!.packageName == ODIM_PACKAGE ||
                currRootWindow!!.packageName == APP_LAUNCHER_PACKAGE ||
                currRootWindow!!.packageName == SETTINGS_PACKAGE
            ) {
                lastTouchPackageName = currRootWindow?.packageName.toString()
                return@setOnTouchListener false
            }
            val rootPackageName = currRootWindow!!.packageName.toString()
            Log.i("TOUCH_PACKAGE", rootPackageName)
            // get view hierarchy at this time
            serviceScope.launch(Dispatchers.Main) {
                val vhJob = async(Dispatchers.IO) {
                    measureTimeMillis {
                        ByteArrayOutputStream().use { baos ->
                            JsonFactory().createGenerator(baos, JsonEncoding.UTF8).use { writer ->
                                parseVHToJson(currRootWindow!!, writer)
                                writer.flush()
                                currVHString = String(baos.toByteArray())
                                if (currVHString.isNullOrEmpty()) {
                                    currVHString = currRootWindow.toString()
                                }
                                Log.d("currRootWindow", "update VH")
                            }
                        }
                    }
                }
                // coroutine to take screenshot
                val screenshotJob = async(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<Long> { continuation ->
                        val startTime = System.currentTimeMillis()
                        measureTimeMillis {
                            takeScreenshot(
                                DEFAULT_DISPLAY,
                                appContext.mainExecutor,
                                object : TakeScreenshotCallback {
                                    override fun onSuccess(result: ScreenshotResult) {
                                        // TODO: check if screens are equal, filter if so
                                        // update screenshot and record current bitmap globally
                                        currentBitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                        result.hardwareBuffer.close()
                                        Log.d("screenshot", "update screen")
                                        continuation.resume(System.currentTimeMillis() - startTime)
                                    }
                                    override fun onFailure(errCode: Int) {
                                        currentBitmap = null
                                        Log.e("edu.illinois.odim", "Screenshot error code: $errCode")
                                        continuation.resume(System.currentTimeMillis() - startTime)
                                    }
                                }
                            )
                        }
                    }
                }
                // wait for coroutine jobs to finish
                val vhTime = vhJob.await()
                val screenshotTime = screenshotJob.await()
                Log.d("MEASURE_TIME", "Parse time took ${vhTime}ms")
                Log.d("MEASURE_TIME", "Screenshot time took ${screenshotTime}ms")
                // once we have screen and vh, save screen as event
                if (currentBitmap == null) {
                    return@launch
                }
                currTouchTime = getInteractionTime()
                if (rootPackageName != lastTouchPackageName) {
                    // create new trace for another app when new app detected in touch
                    Log.i("TOUCH_PACKAGE", "root: $rootPackageName, last package: $lastTouchPackageName")
                    isNewTrace = true
                }
                val tempEventLabel = "${currTouchTime}$DELIM${getString(R.string.type_unknown)}"
                val traceLabel = getCurrentTraceLabel(isNewTrace, rootPackageName, tempEventLabel) ?: return@launch
                saveScreenshot(rootPackageName, traceLabel, tempEventLabel, currentBitmap!!)
                saveVH(rootPackageName, traceLabel, tempEventLabel, currVHString!!)
                if (isNewTrace) {  // add capture task description for new trace
                    captureTask?.let { capture ->
                        if (capture.capture.appId == rootPackageName) {
                            val captureStore = CaptureStore(capture.capture.id, capture.task.description)
                            saveCapture(rootPackageName, traceLabel, captureStore)
                        }
                    }
                }
                isNewTrace = false
                lastTouchPackageName = rootPackageName
            }
            return@setOnTouchListener false
        }
    }

    private fun getInteractionTime(): String {
        val date = Date(System.currentTimeMillis())
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }

    private fun convertInteractionDateToMillis(time: String): Long {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return formatter.parse(time)!!.time
    }

    private fun parseVHToJson(node: AccessibilityNodeInfo, jsonWriter: JsonGenerator) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // if negative coordinate, don't record object
            if (bounds.left < 0 || bounds.top < 0 || bounds.right < 0 || bounds.bottom < 0) {
                return
            }
            // "visit" the node on tree
            jsonWriter.writeStartObject()
            // write coordinates as string to json
            jsonWriter.writeStringField("bounds_in_screen", bounds.flattenToString())
            // id, parent, and class name
            jsonWriter.writeStringField("id", node.viewIdResourceName)
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
            // add children to json
            jsonWriter.writeFieldName("children")
            jsonWriter.writeStartArray()
            for (i in 0 until node.childCount) {
                val currentNode = node.getChild(i)
                if (currentNode != null) {
                    parseVHToJson(currentNode, jsonWriter)  // recursive call
                }
            }
            // end recursive call (base case if no more in the stack)
            jsonWriter.writeEndArray()
            jsonWriter.writeEndObject()
        } catch (e: IOException) {
            Log.e("edu.illinois.odim", "IOException", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // skip if event is null
        if (event == null || event.packageName == null) {
            return
        }
        // skip if there has not been a touch yet
        if (currTouchTime == null) {
            return
        }
        var currEventPackageName = event.packageName.toString()
        Log.i("EVENT_PACKAGE", currEventPackageName)
        // before we decide if new trace, we should check if BACK or HOME button pressed
        val backBtnText = "[Back]"
        val homeBtnText = "[Home]"
        val overviewBtnTexts = listOf("[Overview]", "[Recents]")
        val isBackBtnPressed = (event.packageName == SYSTEMUI_PACKAGE) && (event.text.toString() == backBtnText)
        val isHomeBtnPressed = (event.packageName == SYSTEMUI_PACKAGE) && (event.text.toString() == homeBtnText)
        val isOverviewBtnPressed = (event.packageName == SYSTEMUI_PACKAGE) && (overviewBtnTexts.contains(event.text.toString()))
        val isSystemUIBtnPressed = isBackBtnPressed || isHomeBtnPressed || isOverviewBtnPressed
        // ignore new systemui package name update if home or back button pressed
        if (isSystemUIBtnPressed) {
            currEventPackageName = lastTouchPackageName
        }
        // ignore this selected list of packages in events
        if (currEventPackageName == "null" ||
            currEventPackageName == ODIM_PACKAGE ||
            currEventPackageName == APP_LAUNCHER_PACKAGE ||
            currEventPackageName == SETTINGS_PACKAGE
        ) {
            return
        }
        // grab latest screen and check if screen is already paired
        val latestTrace = getLatestTrace(currEventPackageName) ?: return
        val latestEvent = getLatestEvent(currEventPackageName, latestTrace) ?: return
        val eventData = listEventData(currEventPackageName, latestTrace, latestEvent)
        var isGestureRecorded = false
        eventData.forEach {
            if (it.contains(GESTURE_PREFIX)) {
                isGestureRecorded = true
                return@forEach
            }
        }
        if (isGestureRecorded) {
            return
        }
        val touchTime = convertInteractionDateToMillis(currTouchTime!!)
        val eventTime = System.currentTimeMillis()
        if (eventTime - touchTime > TIME_DIFF) {
            return
        }
        // get vh element interacted with and parse the coordinates
        val node = event.source
        var outbounds: Rect? = null
        var viewId: String? = null
        if (node != null) {
            outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            viewId = node.viewIdResourceName
        }
        val className = event.className.toString()
        // check if event scroll, add delta coordinates
        var scrollCoords : Pair<Int, Int>? = null
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            scrollCoords = Pair(event.scrollDeltaX, event.scrollDeltaY)
        }
        // add the event as gesture
        val gesture = createGestureFromNode(isSystemUIBtnPressed, className, outbounds, viewId, scrollCoords)
        // Parse event description and rename event with proper interaction name
        val eventLabel = createEventLabel(event.eventType)
        // rename all items in the event dir and event dir as well
        renameScreenshot(currEventPackageName, latestTrace, latestEvent, eventLabel)
        renameVH(currEventPackageName, latestTrace, latestEvent, eventLabel)
        renameEvent(currEventPackageName, latestTrace, latestEvent, eventLabel)
        saveGesture(currEventPackageName, latestTrace, eventLabel, gesture)
    }

    private fun createEventLabel(eventType: Int): String {
        val eventTime = currTouchTime
        val eventTypeString = eventTypeToString(eventType)
        return "$eventTime$DELIM$eventTypeString"
    }

    private fun getLatestTrace(eventPackageName: String): String? {
        val traceList = listTraces(eventPackageName)
        return try {
            traceList.first()  // trace is sorted in descending order
        } catch (e: NoSuchElementException) {
            null
        }
    }

    private fun getLatestEvent(eventPackageName: String, trace: String): String? {
        val eventList = listEvents(eventPackageName, trace)
        try {
            return eventList.last() // trace is sorted in ascending order
        } catch (e: NoSuchElementException) {
            return null
        }
    }

    private fun getCurrentTraceLabel(isNewTrace: Boolean, eventPackageName: String, eventLabel: String): String? {
        val traceLabel = if (isNewTrace) {
            eventLabel.substringBefore(DELIM)
        } else {
            getLatestTrace(eventPackageName)
        }
        return traceLabel
    }

    private fun createGestureFromNode(
        isSystemUIBtnPressed: Boolean,
        className: String,
        outbounds: Rect?,
        viewId: String?,
        scrollCoords: Pair<Int, Int>?
    ) : Gesture {
        val windowMetrics = windowManager!!.currentWindowMetrics
        val screenWidth = windowMetrics.bounds.width().toFloat()
        val screenHeight = windowMetrics.bounds.height().toFloat()
        val centerX: Float
        val centerY: Float
        var scrollDX = 0F
        var scrollDY = 0F
        if (outbounds == null) {
          if (!isSystemUIBtnPressed) {
              return Gesture(className)
          } else {
              // take into account when node is empty (when home button is pressed)
              centerX = (screenWidth/2) / screenWidth
              centerY = (screenHeight-30) / screenHeight
          }
        } else {
            centerX = outbounds.centerX()/screenWidth
            centerY = outbounds.centerY()/screenHeight
        }
        if (scrollCoords != null) {
            scrollDX = scrollCoords.first/screenWidth
            scrollDY = scrollCoords.second/screenHeight
        }
        return Gesture(centerX, centerY, scrollDX, scrollDY, viewId)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
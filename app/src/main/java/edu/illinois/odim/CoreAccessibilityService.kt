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
import edu.illinois.odim.LocalStorageOps.listTraces
import edu.illinois.odim.LocalStorageOps.saveGesture
import edu.illinois.odim.LocalStorageOps.saveScreenshot
import edu.illinois.odim.LocalStorageOps.saveVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.measureTimeMillis

internal var workerId = "test_user"

class MyAccessibilityService : AccessibilityService() {
    private var currentBitmap: Bitmap? = null
    private var isScreenEventPaired: Boolean = false
    private var windowManager: WindowManager? = null
    private var currRootWindow: AccessibilityNodeInfo? = null
    private var currVHString: String? = null
    private var lastEventPackageName: String = "null"
    private var lastTouchPackage: String = "null"
    var currTouchTime: String? = null
    private val odimPackageName = "edu.illinois.odim"
    private val settingsPackageName = "com.android.settings"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // static variables
    companion object {
        lateinit var appContext: Context
        fun isAppContextInitialized(): Boolean { return ::appContext.isInitialized }
        lateinit var appLauncherPackageName: String
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        // Create the service
        Log.i("onServiceConnected", "Accessibility Service Connected")
        appContext = applicationContext
        // filter out unwanted packages from recording
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
        layout.setOnTouchListener { _, motionEvent ->
            currRootWindow = rootInActiveWindow
            currVHString = null
            if (currRootWindow?.packageName.toString() == "null" ||
                currRootWindow!!.packageName == odimPackageName ||
                currRootWindow!!.packageName == appLauncherPackageName ||
                currRootWindow!!.packageName == settingsPackageName
            ) {
                lastTouchPackage = currRootWindow?.packageName.toString()
                return@setOnTouchListener false
            }
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
                val screenshotJob = async(Dispatchers.Default) {
                    measureTimeMillis {
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
                                    Log.d("screenshot", "update screen")
                                }
                                override fun onFailure(errCode: Int) {
                                    // TODO: set to null if an error occurs so we don't record
                                    Log.e("edu.illinois.odim", "Screenshot error code: $errCode")
                                }
                            }
                        )
                    }
                }
                // wait for coroutine jobs to finish
                val vhTime = vhJob.await()
                val screenshotTime = screenshotJob.await()
                Log.d("MEASURE_TIME", "Parse time took ${vhTime}ms")
                Log.d("MEASURE_TIME", "Screenshot time took ${screenshotTime}ms")
                // TODO: once we have screen and vh, save screen as event
            }
            lastTouchPackage = currRootWindow!!.packageName.toString()
            return@setOnTouchListener false
        }
    }

    fun getInteractionTime(): String {
        val date = Date(System.currentTimeMillis()) //event.eventTime)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }

    private fun parseVHToJson(node: AccessibilityNodeInfo, jsonWriter: JsonGenerator) {
        try {
            // "visit" the node on tree
            jsonWriter.writeStartObject()
            // write coordinates as string to json
            val outbounds = Rect()
            node.getBoundsInScreen(outbounds)
            jsonWriter.writeStringField("bounds_in_screen", outbounds.flattenToString())
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // odim recording logic begins
        if (event == null || event.packageName == null) {
            return
        }
        if (isScreenEventPaired) {  // ignore if screenshot already paired with event
            return
        }
        var currEventPackageName = event.packageName.toString()
        // before we decide if new trace, we should check if BACK or HOME button pressed
        val systemUIPackageName = "com.android.systemui"
        val backBtnText = "[Back]"
        val homeBtnText = "[Home]"
        val overviewBtnTexts = listOf("[Overview]", "[Recents]") // TODO: do we want this button to be used?
        val isBackBtnPressed = (event.packageName == systemUIPackageName) && (event.text.toString() == backBtnText)
        val isHomeBtnPressed = (event.packageName == systemUIPackageName) && (event.text.toString() == homeBtnText)
        val isOverviewBtnPressed = (event.packageName == systemUIPackageName) && (overviewBtnTexts.contains(event.text.toString()))
        val isSystemUIBtnPressed = isBackBtnPressed || isHomeBtnPressed || isOverviewBtnPressed
        // ignore new systemui package name update if home or back button pressed
        var isNewTrace = false
        if (currEventPackageName != lastEventPackageName && !isSystemUIBtnPressed) {
            // continue trace even if back and home button pressed
            isNewTrace = true
        }
        if (isSystemUIBtnPressed) {
            Log.d("pressed", "systemui buttons")
            currEventPackageName = lastEventPackageName
        }
        Log.d("currEventPackageName", currEventPackageName)
        if (currEventPackageName == "null" ||
            currEventPackageName == odimPackageName ||
            currEventPackageName == appLauncherPackageName ||
            currEventPackageName == settingsPackageName
        ) {
            lastEventPackageName = currEventPackageName
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
        // null checks for both bitmap and vh in touch listener
        if (currentBitmap == null) {
            return
        }
        val vh = currVHString ?: return
        isScreenEventPaired = true
        // Parse event description
        val eventLabel = createEventLabel(event.eventType)
        // check if event scroll, add delta coordinates
        var scrollCoords : Pair<Int, Int>? = null
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            scrollCoords = Pair(event.scrollDeltaX, event.scrollDeltaY)
        }
        // add the event as gesture, screenshot, and vh
        val traceLabel = getCurrentTraceLabel(isNewTrace, currEventPackageName, eventLabel)
        val gesture = createGestureFromNode(isSystemUIBtnPressed, className, outbounds, viewId, scrollCoords)
        saveGesture(currEventPackageName, traceLabel, eventLabel, gesture)
        saveScreenshot(currEventPackageName, traceLabel, eventLabel, currentBitmap!!)
        saveVH(currEventPackageName, traceLabel, eventLabel, vh)
        // update new last event package to track
        lastEventPackageName = if (isBackBtnPressed) {
            currEventPackageName
        } else {
            event.packageName.toString()
        }
    }

    private fun createEventLabel(eventType: Int): String {
        val eventTime = currTouchTime
        val eventTypeString = eventTypeToString(eventType)
        return "$eventTime; $eventTypeString"
    }

    private fun getCurrentTraceLabel(isNewTrace: Boolean, eventPackageName: String, eventLabel: String): String {
        val traceList = listTraces(eventPackageName)
        val traceLabel = if (isNewTrace) {
            eventLabel.substringBefore(";")
        } else {
            traceList.first()  // trace is sorted in descending order
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
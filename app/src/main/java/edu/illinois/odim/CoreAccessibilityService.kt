package edu.illinois.odim

import android.animation.ValueAnimator
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.wrapHardwareBuffer
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
import android.widget.FrameLayout
import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import edu.illinois.odim.dataclasses.AgentState
import edu.illinois.odim.utils.AgentUtils
import com.fasterxml.jackson.databind.ObjectMapper
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
import kotlinx.coroutines.coroutineScope
import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.system.measureTimeMillis

var DELIM = "; "

class MyAccessibilityService : AccessibilityService() {
    private var currentBitmap: Bitmap? = null
    private var isNewTrace: Boolean = false
    private var windowManager: WindowManager? = null
    private var overlayLayout: FrameLayout? = null
    private var currRootWindow: AccessibilityNodeInfo? = null
    private var currVHString: String? = null
    private var lastTouchPackageName: String = "null"
    private var currTouchTime: String? = null
    var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val objectMapper = ObjectMapper()
    private var glowAnimator: ValueAnimator? = null
    val agentController = AgentController(this)
    // static variables
    companion object {
        lateinit var instance: MyAccessibilityService
        lateinit var appContext: Context
        var captureTask: CaptureTask? = null
        fun isAppContextInitialized(): Boolean { return ::appContext.isInitialized }
        lateinit var APP_LAUNCHER_PACKAGE: String
        private const val TIME_DIFF = 100
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val ODIM_PACKAGE = "edu.illinois.odim"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }

    fun executeAction(action: edu.illinois.odim.dataclasses.AgentAction, elements: List<edu.illinois.odim.dataclasses.ActionableElement>) {
        Log.i("Agent", "Executing action: ${action.actionType} (index: ${action.index}, direction: ${action.direction}, text: ${action.text})")
        when (action.actionType.lowercase()) {
            "click" -> action.index?.let { performClick(it, elements) }
            "type" -> action.text?.let { performType(it) }
            "scroll", "swipe" -> action.direction?.let { performScroll(it) }
            "navigate_back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "navigate_home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "status" -> Log.i("Agent", "Goal Status: ${action.goalStatus}")
            else -> Log.w("Agent", "Unknown action type: ${action.actionType}")
        }
    }

    private fun performClick(index: Int, elements: List<edu.illinois.odim.dataclasses.ActionableElement>) {
        val element = elements.find { it.index == index }
        if (element == null) {
            Log.e("Agent", "Click failed: Element with index $index not found in ${elements.size} elements")
            return
        }

        val x = element.center.x.toFloat()
        val y = element.center.y.toFloat()
        val displayMetrics = appContext.resources.displayMetrics
        Log.d("Agent", "Performing click at ($x, $y) for element index $index: ${element.text.ifEmpty { element.contentDescription.ifEmpty { "no text" } }}")
        Log.d("Agent", "Screen metrics: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, Element bounds: ${element.bounds}")
        
        val clickPath = Path().apply {
            moveTo(x, y)
            lineTo(x, y) // Ensure the path has a point for the duration
        }
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 100))
        
        val gesture = gestureBuilder.build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i("Agent", "Click gesture COMPLETED at ($x, $y)")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                val strokes = gestureDescription?.strokeCount ?: 0
                Log.e("Agent", "Click gesture CANCELLED at ($x, $y). Strokes: $strokes")
            }
        }, null)
        Log.d("Agent", "dispatchGesture returned: $dispatched")
    }

    private fun performType(text: String) {
        val root = rootInActiveWindow ?: return
        val focusedNode = root.findFocus(FOCUS_INPUT)
        if (focusedNode == null) {
            Log.e("Agent", "Type failed: No focused input field found")
            return
        }

        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.i("Agent", "Type completed: $text")
    }

    private fun performScroll(direction: String) {
        val windowMetrics = windowManager?.currentWindowMetrics ?: return
        val width = windowMetrics.bounds.width()
        val height = windowMetrics.bounds.height()
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        var endX = centerX
        var endY = centerY
        
        val scrollAmount = height / 4f // Smaller scroll amount might be more reliable
        
        when (direction.lowercase()) {
            "up" -> endY = centerY - scrollAmount
            "down" -> endY = centerY + scrollAmount
            "left" -> endX = centerX - width / 4f
            "right" -> endX = centerX + width / 4f
            else -> {
                Log.e("Agent", "Scroll failed: Unknown direction $direction")
                return
            }
        }

        Log.d("Agent", "Performing scroll $direction: from ($centerX, $centerY) to ($endX, $endY) on screen ${width}x${height}")
        
        val scrollPath = Path().apply {
            moveTo(centerX, centerY)
            lineTo(endX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(scrollPath, 0, 800)) // Slower scroll
        
        val gesture = gestureBuilder.build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i("Agent", "Scroll $direction gesture COMPLETED")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                val strokes = gestureDescription?.strokeCount ?: 0
                Log.e("Agent", "Scroll $direction gesture CANCELLED. Strokes: $strokes")
            }
        }, null)
        Log.d("Agent", "dispatchGesture returned: $dispatched")
    }

    suspend fun captureAndSaveState(traceLabel: String, eventLabel: String, overridePackageName: String? = null): AgentState? {
        val state = captureCurrentState() ?: return null
        
        val pkgToSave = overridePackageName ?: state.packageName
        saveScreenshot(pkgToSave, traceLabel, eventLabel, state.screenshot)
        saveVH(pkgToSave, traceLabel, eventLabel, objectMapper.writeValueAsString(state.vh))
        
        return state
    }

    suspend fun captureCurrentState(): AgentState? = coroutineScope {
        val root = rootInActiveWindow ?: return@coroutineScope null
        val rootPackageName = root.packageName.toString()
        
        // Start screenshot immediately - this is an async system call
        val screenshotDeferred = async { captureScreenshot() }
        
        // Start VH capture immediately on current thread (Main) 
        // while system works on the screenshot
        val vhString = captureVH(rootPackageName)
        
        val bitmap = screenshotDeferred.await()
        
        if (vhString == null || bitmap == null) {
            Log.e("Agent", "Failed to capture state: vh=${vhString != null}, bitmap=${bitmap != null}")
            return@coroutineScope null
        }
        
        AgentState(
            screenshot = bitmap,
            vh = objectMapper.readTree(vhString),
            packageName = rootPackageName
        )
    }

    private suspend fun captureScreenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            DEFAULT_DISPLAY,
            appContext.mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    result.hardwareBuffer.close()
                    continuation.resume(bitmap)
                }
                override fun onFailure(errCode: Int) {
                    Log.e("edu.illinois.odim", "Screenshot error code: $errCode")
                    continuation.resume(null)
                }
            }
        )
    }

    private suspend fun captureVH(rootPackageName: String): String? = suspendCancellableCoroutine { continuation ->
        val allWindows = windows
        val appWindows = allWindows.filter { 
            val root = try { it.root } catch (e: Exception) { null }
            root?.packageName?.toString() == rootPackageName 
        }
        
        Log.d("Agent", "captureVH: total windows=${allWindows.size}, app windows=${appWindows.size} for $rootPackageName")
        
        ByteArrayOutputStream().use { baos ->
            JsonFactory().createGenerator(baos, JsonEncoding.UTF8).use { writer ->
                writer.writeStartObject()
                writer.writeStringField("class_name", "android.view.View")
                writer.writeStringField("package_name", rootPackageName)
                writer.writeBooleanField("visibility", true)
                writer.writeStringField("id", "virtual_root")
                
                val displayMetrics = appContext.resources.displayMetrics
                val screenBounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
                writer.writeStringField("bounds_in_screen", screenBounds.flattenToString())
                
                writer.writeFieldName("children")
                writer.writeStartArray()

                if (appWindows.isNotEmpty()) {
                    for (window in appWindows) {
                        val root = try { window.root } catch (e: Exception) { null }
                        if (root != null) {
                            parseVHToJson(root, writer, "android.view.View")
                        }
                    }
                } else {
                    rootInActiveWindow?.let { parseVHToJson(it, writer, "android.view.View") }
                }

                writer.writeEndArray()
                writer.writeEndObject()
                writer.flush()
                
                val vh = baos.toString("UTF-8")
                continuation.resume(if (vh.isNotEmpty()) vh else null)
            }
        }
    }

    fun updateOverlayState(isRunning: Boolean) {
        serviceScope.launch(Dispatchers.Main) {
            if (isRunning) {
                overlayLayout?.setBackgroundResource(R.drawable.agent_running_border)
                // Start pulsing glow
                glowAnimator?.cancel()
                glowAnimator = ValueAnimator.ofFloat(0.4f, 1.0f).apply {
                    duration = 1000
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { animator ->
                        overlayLayout?.alpha = animator.animatedValue as Float
                    }
                    start()
                }
            } else {
                glowAnimator?.cancel()
                glowAnimator = null
                overlayLayout?.alpha = 1.0f
                overlayLayout?.background = null
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        // Create the service
        instance = this
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
        overlayLayout = FrameLayout(appContext)
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
        windowManager!!.addView(overlayLayout, params)
        // record screenshot and view hierarchy when screen touch is detected with separate coroutines
        overlayLayout!!.setOnTouchListener { _, _ ->
            // Skip passive recording if agent is running
            if (agentController.isRunning.value) return@setOnTouchListener false

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
            
            // Phase 2: Capture all windows belonging to the target application
            serviceScope.launch(Dispatchers.Main) {
                val tempEventLabel = "${getInteractionTime()}$DELIM${getString(R.string.type_unknown)}"
                if (rootPackageName != lastTouchPackageName) {
                    isNewTrace = true
                }
                val traceLabel = getCurrentTraceLabel(isNewTrace, rootPackageName, tempEventLabel) ?: return@launch
                
                val state = captureAndSaveState(traceLabel, tempEventLabel)
                if (state == null) {
                    Log.e("edu.illinois.odim", "Failed to capture state on touch")
                    return@launch
                }
                
                // Update touch time for event pairing (Passive Recording)
                currTouchTime = tempEventLabel.substringBefore(DELIM)

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

    private fun parseVHToJson(node: AccessibilityNodeInfo, jsonWriter: JsonGenerator, parentClassName: String) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // "visit" the node on tree
            jsonWriter.writeStartObject()
            // write coordinates as string to json
            jsonWriter.writeStringField("bounds_in_screen", bounds.flattenToString())
            // id, parent, and class name
            jsonWriter.writeStringField("id", node.viewIdResourceName)
            jsonWriter.writeStringField("package_name", node.packageName?.toString() ?: "null")
            val thisClassName = node.className?.toString() ?: "null"
            jsonWriter.writeStringField("class_name", thisClassName)
            jsonWriter.writeStringField("parent", parentClassName)
            // add vh element boolean properties
            jsonWriter.writeBooleanField("editable", node.isEditable)
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
            node.contentDescription?.let {
                jsonWriter.writeStringField("content_desc", it.toString())
            }
            node.text?.let {
                jsonWriter.writeStringField("text_field", it.toString())
            }
            // additional accessibility fields for M3E detection
            jsonWriter.writeNumberField("drawing_order", node.drawingOrder)
            node.stateDescription?.let {
                jsonWriter.writeStringField("state_desc", it.toString())
            }
            node.hintText?.let {
                jsonWriter.writeStringField("hint_text", it.toString())
            }
            // serialize extras bundle — Compose semantic properties appear here
            writeBundleAsJson(node.extras, jsonWriter)
            
            val childCount = node.childCount
            jsonWriter.writeNumberField("children_count", childCount)
            
            // add children to json
            if (childCount > 0) {
                jsonWriter.writeFieldName("children")
                jsonWriter.writeStartArray()
                for (i in 0 until childCount) {
                    val currentNode = try { node.getChild(i) } catch (e: Exception) { null }
                    if (currentNode != null) {
                        parseVHToJson(currentNode, jsonWriter, thisClassName)
                        // No recycle() call here - deprecated in API 33+
                    }
                }
                jsonWriter.writeEndArray()
            }
            jsonWriter.writeEndObject()
        } catch (e: IOException) {
            Log.e("edu.illinois.odim", "IOException in parseVHToJson", e)
        }
    }

    private fun writeBundleAsJson(bundle: Bundle, jsonWriter: JsonGenerator) {
        jsonWriter.writeFieldName("extras")
        jsonWriter.writeStartObject()
        for (key in bundle.keySet()) {
            when (val value = bundle.get(key)) {
                is String -> jsonWriter.writeStringField(key, value)
                is Int -> jsonWriter.writeNumberField(key, value)
                is Long -> jsonWriter.writeNumberField(key, value)
                is Float -> jsonWriter.writeNumberField(key, value)
                is Double -> jsonWriter.writeNumberField(key, value)
                is Boolean -> jsonWriter.writeBooleanField(key, value)
                is CharSequence -> jsonWriter.writeStringField(key, value.toString())
                null -> jsonWriter.writeNullField(key)
                else -> jsonWriter.writeStringField(key, value.toString())
            }
        }
        jsonWriter.writeEndObject()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Skip passive event processing if agent is running
        if (agentController.isRunning.value) return

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
        // Parse event description and rename event with proper interaction name
        val gesture = createGestureFromNode(
            isSystemUIBtnPressed,
            className,
            outbounds,
            viewId,
            scrollCoords
        )
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
        return try {
            eventList.last() // trace is sorted in ascending order
        } catch (e: NoSuchElementException) {
            null
        }
    }

    private fun getCurrentTraceLabel(
        isNewTrace: Boolean,
        eventPackageName: String,
        eventLabel: String
    ): String? {
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
        var centerX: Float = -1f
        var centerY: Float = -1f
        var scrollDX = 0F
        var scrollDY = 0F
        var actionType = if (scrollCoords != null) "scroll" else "click"

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
        return Gesture(centerX, centerY, scrollDX, scrollDY, viewId, actionType)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
package edu.illinois.odim.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import edu.illinois.odim.MyAccessibilityService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenshotException(val errorCode: Int) : RuntimeException("takeScreenshot failed: $errorCode")

private val screenshotCounter = AtomicInteger(0)

suspend fun MyAccessibilityService.captureScreenshotSuspending(): Bitmap {
    var lastException: Exception? = null
    repeat(5) { attempt ->
        try {
            return captureScreenshotOnce()
        } catch (e: ScreenshotException) {
            lastException = e
            if (e.errorCode == 3) { // AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                Log.w("Bridge", "Screenshot interval too short, retrying (attempt ${attempt + 1})")
                delay(100L * (attempt + 1))
            } else {
                Log.e("Bridge", "Screenshot failed with error code: ${e.errorCode}")
                throw e
            }
        } catch (e: Exception) {
            Log.e("Bridge", "Screenshot failed with unexpected exception", e)
            throw e
        }
    }
    throw lastException ?: RuntimeException("takeScreenshot failed after retries")
}

private suspend fun MyAccessibilityService.captureScreenshotOnce(): Bitmap = suspendCancellableCoroutine { cont ->
    val count = screenshotCounter.incrementAndGet()
    Log.d("BridgeDebug", "takeScreenshot called (total: $count)")
    takeScreenshot(
        Display.DEFAULT_DISPLAY,
        applicationContext.mainExecutor,
        object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                result.hardwareBuffer.close()
                if (bitmap != null) {
                    cont.resume(bitmap)
                } else {
                    cont.resumeWithException(IllegalStateException("Failed to wrap hardware buffer"))
                }
            }
            override fun onFailure(errorCode: Int) {
                cont.resumeWithException(ScreenshotException(errorCode))
            }
        }
    )
}

suspend fun MyAccessibilityService.dispatchGestureSuspending(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
    val dispatched = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            cont.resume(true)
        }
        override fun onCancelled(gestureDescription: GestureDescription?) {
            cont.resume(false)
        }
    }, null)

    if (!dispatched) {
        cont.resume(false)
    }
}

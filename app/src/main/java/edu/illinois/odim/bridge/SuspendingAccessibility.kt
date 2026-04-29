package edu.illinois.odim.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import edu.illinois.odim.MyAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun MyAccessibilityService.captureScreenshotSuspending(): Bitmap = suspendCancellableCoroutine { cont ->
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
                Log.e("Bridge", "Screenshot failed: $errorCode")
                cont.resumeWithException(RuntimeException("takeScreenshot failed: $errorCode"))
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

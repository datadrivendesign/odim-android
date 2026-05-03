package edu.illinois.odim.bridge

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.eventTypeToString
import kotlinx.coroutines.delay

object SettleSignal {
    @Volatile private var lastEventAtMs: Long = System.currentTimeMillis()

    fun reset() {
        lastEventAtMs = System.currentTimeMillis()
    }

    fun notifyEvent(eventType: Int) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            lastEventAtMs = System.currentTimeMillis()
            android.util.Log.v("SettleSignal", "Activity detected: eventType=${eventTypeToString(eventType)}")
        }
    }

    suspend fun awaitQuiet(quietWindowMs: Long, maxWaitMs: Long, minWaitMs: Long = 450L): SettleResult {
        val start = System.currentTimeMillis()
        val deadline = start + maxWaitMs
        android.util.Log.d("SettleSignal", "Starting awaitQuiet (quietWindowMs=$quietWindowMs, maxWaitMs=$maxWaitMs, minWaitMs=$minWaitMs)")
        while (true) {
            val now = System.currentTimeMillis()
            val sinceLast = now - lastEventAtMs
            val elapsed = now - start

            if (sinceLast >= quietWindowMs && elapsed >= minWaitMs) {
                android.util.Log.d("SettleSignal", "Settled after ${elapsed}ms (quiet for ${sinceLast}ms)")
                return SettleResult(true, elapsed)
            }
            if (now >= deadline) {
                android.util.Log.d("SettleSignal", "Settle timed out after ${elapsed}ms")
                return SettleResult(false, elapsed)
            }

            // Wait for either the next quiet check or the minWait floor
            val nextCheck = if (elapsed < minWaitMs) (minWaitMs - elapsed) else (quietWindowMs - sinceLast)
            delay(nextCheck.coerceAtMost(deadline - now).coerceAtLeast(10L))
        }
    }
}

data class SettleResult(val settled: Boolean, val elapsedMs: Long)

package edu.illinois.odim.bridge

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay

object SettleSignal {
    @Volatile private var lastEventAtMs: Long = System.currentTimeMillis()

    fun notifyEvent(eventType: Int) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEventAtMs = System.currentTimeMillis()
        }
    }

    suspend fun awaitQuiet(quietWindowMs: Long, maxWaitMs: Long): SettleResult {
        val start = System.currentTimeMillis()
        val deadline = start + maxWaitMs
        while (true) {
            val now = System.currentTimeMillis()
            val sinceLast = now - lastEventAtMs
            if (sinceLast >= quietWindowMs) return SettleResult(true, now - start)
            if (now >= deadline)            return SettleResult(false, maxWaitMs)
            delay(minOf(quietWindowMs - sinceLast, deadline - now).coerceAtLeast(10L))
        }
    }
}

data class SettleResult(val settled: Boolean, val elapsedMs: Long)

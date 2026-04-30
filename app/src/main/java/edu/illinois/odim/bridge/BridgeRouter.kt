package edu.illinois.odim.bridge

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.odim.MyAccessibilityService
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.GestureDescription
import android.graphics.Path

class BridgeRouter(private val service: MyAccessibilityService) {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    suspend fun route(session: NanoHTTPD.IHTTPSession): Response {
        return when {
            session.method == NanoHTTPD.Method.GET && session.uri == "/health" -> handleHealth()
            session.method == NanoHTTPD.Method.GET && session.uri == "/observe" -> handleObserve()
            session.method == NanoHTTPD.Method.POST && session.uri == "/dispatch" -> handleDispatch(session)
            session.method == NanoHTTPD.Method.POST && session.uri == "/reset" -> handleReset(session)
            session.method == NanoHTTPD.Method.POST && session.uri == "/settle" -> handleSettle(session)
            else -> jsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Not Found"))
        }
    }

    private fun handleHealth(): Response {
        return jsonResponse(Response.Status.OK, mapOf(
            "ok" to true,
            "service" to "odim-bridge",
            "version" to "0.1.0",
            "pid" to android.os.Process.myPid()
        ))
    }

    private suspend fun handleObserve(): Response {
        val screenshot = try {
            service.captureScreenshotSuspending()
        } catch (e: Exception) {
            Log.e("BridgeRouter", "Failed to capture screenshot", e)
            null
        }

        val vhString = service.captureVH(service.rootInActiveWindow?.packageName?.toString() ?: "")
        val vhJson = if (vhString != null) objectMapper.readTree(vhString) else null

        val displayMetrics = service.resources.displayMetrics

        val response = mutableMapOf<String, Any?>(
            "screenshotPng" to screenshot?.let { encodeBitmapToBase64(it) },
            "viewport" to mapOf(
                "width" to displayMetrics.widthPixels,
                "height" to displayMetrics.heightPixels
            ),
            "vh" to vhJson,
            "package" to service.rootInActiveWindow?.packageName?.toString(),
            "window" to service.rootInActiveWindow?.className?.toString(),
            "capturedAt" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        )

        return jsonResponse(Response.Status.OK, response)
    }

    private suspend fun handleDispatch(session: NanoHTTPD.IHTTPSession): Response {
        val body = readBody(session)
        val json = objectMapper.readTree(body)
        val kind = json.get("kind")?.asText() ?: return jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Missing kind"))

        // For programmatic actions, we manually trigger a state capture to link the action to a screen
        val interactionTime = service.getInteractionTime()

        val success = when (kind) {
            "tap" -> {
                val x = json.get("x").asInt().toFloat()
                val y = json.get("y").asInt().toFloat()

                // Record the intent of this action before dispatching
                service.recordBridgeAction(interactionTime, "click", x, y)

                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    .build()
                service.dispatchGestureSuspending(gesture)
            }
            "scroll" -> {
                val direction = json.get("direction").asText()
                val displayMetrics = service.resources.displayMetrics
                val width = displayMetrics.widthPixels
                val height = displayMetrics.heightPixels

                val startX = json.get("fromX")?.asInt()?.toFloat() ?: (width / 2f)
                val startY = json.get("fromY")?.asInt()?.toFloat() ?: (height / 2f)

                // Record the intent
                service.recordBridgeAction(interactionTime, "scroll", startX, startY)

                var endX = startX
                var endY = startY
                val scrollAmount = height / 4f

                when (direction.lowercase()) {
                    "up" -> endY -= scrollAmount
                    "down" -> endY += scrollAmount
                    "left" -> endX -= width / 4f
                    "right" -> endX += width / 4f
                }

                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                    .build()
                service.dispatchGestureSuspending(gesture)
            }
            "type" -> {
                val text = json.get("text").asText()
                service.recordBridgeAction(interactionTime, "type")
                service.performType(text)
                true
            }
            "back" -> {
                service.recordBridgeAction(interactionTime, "back")
                service.performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "home" -> {
                service.recordBridgeAction(interactionTime, "home")
                service.performGlobalAction(GLOBAL_ACTION_HOME)
            }
            else -> false
        }

        return jsonResponse(Response.Status.OK, mapOf("success" to success))
    }

    private fun handleReset(session: NanoHTTPD.IHTTPSession): Response {
        val body = readBody(session)
        val json = objectMapper.readTree(body)
        val packageName = json.get("package")?.asText()

        service.performGlobalAction(GLOBAL_ACTION_HOME)

        if (packageName != null) {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                service.startActivity(intent)
            }
        }

        return jsonResponse(Response.Status.OK, mapOf("success" to true))
    }

    private suspend fun handleSettle(session: NanoHTTPD.IHTTPSession): Response {
        val body = readBody(session)
        val json = objectMapper.readTree(body)
        val quietWindowMs = json.get("quietWindowMs")?.asLong() ?: 250L
        val maxWaitMs = json.get("maxWaitMs")?.asLong() ?: 5000L

        val result = SettleSignal.awaitQuiet(quietWindowMs, maxWaitMs)
        return jsonResponse(Response.Status.OK, mapOf(
            "settled" to result.settled,
            "elapsedMs" to result.elapsedMs
        ))
    }

    private fun jsonResponse(status: Response.Status, data: Any): Response {
        return newFixedLengthResponse(status, "application/json", objectMapper.writeValueAsString(data))
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String {
        val map = mutableMapOf<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

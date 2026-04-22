package edu.illinois.odim.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.odim.dataclasses.ActionableElement
import edu.illinois.odim.dataclasses.AgentAction
import edu.illinois.odim.dataclasses.AgentResponse
import edu.illinois.odim.dataclasses.PointData
import edu.illinois.odim.dataclasses.RectData
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object AgentUtils {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val sharedClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun parseModelResponse(text: String): AgentResponse? {
        return try {
            val reasonPart = text.substringAfter("Reason:").substringBefore("Reflection:").trim()
            val reflectionPart = text.substringAfter("Reflection:").substringBefore("Action:").trim()
            val actionPart = text.substringAfter("Action:").trim()

            // actionPart should be JSON like {"actionType": "click", "index": 5}
            val action = objectMapper.readValue(actionPart, edu.illinois.odim.dataclasses.AgentAction::class.java)
            if (action != null) {
                AgentResponse(reasonPart, reflectionPart, action)
            } else null
        } catch (e: Exception) {
            Log.e("AgentUtils", "Failed to parse model response: $text", e)
            null
        }
    }

    /**
     * Resizes a bitmap by a scale factor to maintain aspect ratio.
     */
    fun resizeBitmap(source: Bitmap, scale: Float): Bitmap {
        val width = (source.width * scale).toInt()
        val height = (source.height * scale).toInt()
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Flattens the VH JSON into a list of actionable elements.
     */
    fun flattenVH(vhString: String): List<ActionableElement> {
        val root = objectMapper.readTree(vhString)
        val result = mutableListOf<ActionableElement>()
        val counter = Counter()
        
        // The first node is our Virtual Root, we skip it but process its children
        val children = root.get("children")
        if (children != null && children.isArray) {
            for (child in children) {
                traverse(child, result, counter)
            }
        } else {
            // Fallback for non-virtual-root VH
            traverse(root, result, counter)
        }
        
        return result
    }

    private fun traverse(node: JsonNode, result: MutableList<ActionableElement>, counter: Counter) {
        val isVisible = node.get("visibility")?.asBoolean() ?: false
        if (!isVisible) return

        val boundsString = node.get("bounds_in_screen")?.asText() ?: ""
        val rect = parseBounds(boundsString) ?: return

        val isClickable = node.get("clickable")?.asBoolean() ?: false
        val isScrollable = node.get("scrollable")?.asBoolean() ?: false
        val isEditable = node.get("focusable")?.asBoolean() ?: false // focusable often implies editable in this context
        val text = node.get("text_field")?.asText() ?: ""
        val contentDesc = node.get("content_desc")?.asText() ?: ""
        val resourceId = node.get("id")?.asText()

        // Criteria for an "actionable" element in the prompt
        if (isClickable || isScrollable || isEditable || text.isNotEmpty() || contentDesc.isNotEmpty()) {
            val element = ActionableElement(
                index = counter.next(),
                className = node.get("class_name")?.asText() ?: "android.view.View",
                text = text,
                contentDescription = contentDesc,
                resourceId = resourceId,
                bounds = RectData(rect.left, rect.top, rect.right, rect.bottom),
                center = PointData(rect.centerX(), rect.centerY()),
                isClickable = isClickable,
                isScrollable = isScrollable,
                isEditable = isEditable,
                isFocused = node.get("focused")?.asBoolean() ?: false
            )
            result.add(element)
        }

        val children = node.get("children")
        if (children != null && children.isArray) {
            for (child in children) {
                traverse(child, result, counter)
            }
        }
    }

    private fun parseBounds(boundsString: String): Rect? {
        // Format: "[left,top][right,bottom]" or "Rect(left, top - right, bottom)"
        // ODIM uses Rect.flattenToString() which is "left top right bottom"
        return try {
            val parts = boundsString.split(" ")
            if (parts.size == 4) {
                Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private class Counter {
        private var count = 0
        fun next(): Int = ++count
    }
}

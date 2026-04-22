package edu.illinois.odim.network

import android.graphics.Bitmap
import android.util.Log
import edu.illinois.odim.BuildConfig
import edu.illinois.odim.dataclasses.ActionableElement
import edu.illinois.odim.dataclasses.AgentResponse
import edu.illinois.odim.dataclasses.StepRecord
import edu.illinois.odim.utils.AgentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ClaudeClient : AgentBrain {
    private const val TAG = "ClaudeClient"
    private const val MODEL_NAME = "claude-sonnet-4-6"

    override suspend fun getResponse(
        goal: String,
        screenshot: Bitmap,
        lastScreenshot: Bitmap?,
        elements: List<ActionableElement>,
        history: List<StepRecord>
    ): AgentResponse? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.CLAUDE_API_KEY
        if (apiKey.isEmpty()) {
            Log.e(TAG, "CLAUDE_API_KEY is not set")
            return@withContext null
        }

        try {
            val base64Image = AgentUtils.encodeBitmapToBase64(screenshot)
            val base64LastImage = lastScreenshot?.let { AgentUtils.encodeBitmapToBase64(it) }
            
            val userPrompt = AgentPrompts.formatUserPrompt(goal, elements, history)
            val systemPrompt = AgentPrompts.SYSTEM_PROMPT

            val requestBodyJson = JSONObject().apply {
                put("model", MODEL_NAME)
                put("max_tokens", 2048)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            // Add previous screenshot if available
                            base64LastImage?.let {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", "PREVIOUS SCREENSHOT (for reflection):")
                                })
                                put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", "image/png")
                                        put("data", it)
                                    })
                                })
                            }
                            
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "CURRENT SCREENSHOT:")
                            })
                            put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/png")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", userPrompt)
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(BuildConfig.CLAUDE_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            AgentUtils.sharedClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "API call failed: ${response.code} - $responseBody")
                    return@withContext null
                }

                val responseJson = JSONObject(responseBody)
                val contentArray = responseJson.getJSONArray("content")
                if (contentArray.length() > 0) {
                    val textResponse = contentArray.getJSONObject(0).getString("text")
                    return@withContext AgentUtils.parseModelResponse(textResponse)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getResponse", e)
        }
        null
    }
}

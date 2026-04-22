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

object OllamaClient : AgentBrain {
    private const val TAG = "Ollama"
    private const val MODEL_NAME = "qwen3-vl:32b"

    override suspend fun getResponse(
        goal: String,
        screenshot: Bitmap,
        lastScreenshot: Bitmap?,
        elements: List<ActionableElement>,
        history: List<StepRecord>
    ): AgentResponse? = withContext(Dispatchers.IO) {
        try {
            val base64Image = AgentUtils.encodeBitmapToBase64(screenshot)
            val base64LastImage = lastScreenshot?.let { AgentUtils.encodeBitmapToBase64(it) }
            
            val systemPrompt = AgentPrompts.SYSTEM_PROMPT
            val userPrompt = AgentPrompts.formatUserPrompt(goal, elements, history)

            val requestBodyJson = JSONObject().apply {
                put("model", MODEL_NAME)
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                        put("images", JSONArray().apply {
                            base64LastImage?.let { put(it) }
                            put(base64Image)
                        })
                    })
                })
            }

            val request = Request.Builder()
                // TODO: add an api key if there is time
                .url(BuildConfig.OLLAMA_API_URL)
                .addHeader("content-type", "application/json")
                .addHeader("Authorization", "Bearer ${BuildConfig.OLLAMA_API_KEY}")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            AgentUtils.sharedClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, "API call failed: ${response.code} - $responseBody")
                    return@withContext null
                }

                val responseJson = JSONObject(responseBody)
                val message = responseJson.getJSONObject("message")
                val textResponse = message.getString("content")
                return@withContext AgentUtils.parseModelResponse(textResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getResponse", e)
        }
        null
    }
}

package edu.illinois.odim.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.illinois.odim.BuildConfig
import edu.illinois.odim.DELIM
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.dataclasses.CaptureStore
import edu.illinois.odim.dataclasses.Gesture
import edu.illinois.odim.dataclasses.Redaction
import edu.illinois.odim.utils.LocalStorageOps.listEvents
import edu.illinois.odim.utils.LocalStorageOps.loadGesture
import edu.illinois.odim.utils.LocalStorageOps.loadRedactions
import edu.illinois.odim.utils.LocalStorageOps.loadScreenshot
import edu.illinois.odim.utils.LocalStorageOps.loadVH
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.gildor.coroutines.okhttp.await
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger

object UploadDataOps {
    private const val API_URL_PREFIX = BuildConfig.API_URL_PREFIX

    private suspend fun uploadScreenCapture(client: OkHttpClient,
                                            mapper: ObjectMapper,
                                            packageName: String,
                                            traceLabel: String,
                                            captureId: String,
                                            eventLabel: String): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        // get bitmap as bit64 string
        ByteArrayOutputStream().use {
            val bitmap = loadScreenshot(packageName, traceLabel, eventLabel)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            val bitmapBase64 = Base64.encodeToString(it.toByteArray(), Base64.DEFAULT)
            // retrieve vh
            val vhString = loadVH(packageName, traceLabel, eventLabel)
            // retrieve screen timestamp
            val screenCreatedAt = eventLabel.substringBefore(DELIM)
            val screenGestureType = eventLabel.substringAfter(DELIM)
            val screenId = "${screenCreatedAt}_${screenGestureType}"
            val reqBodyJSONObj = mapper.createObjectNode()
            reqBodyJSONObj.put("vh", vhString)
            reqBodyJSONObj.put("img", bitmapBase64)
            reqBodyJSONObj.put("created", screenCreatedAt)
            reqBodyJSONObj.put("id", screenId)
            val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
            // run the POST request
            val screenPostRequest = Request.Builder()
                .url("$API_URL_PREFIX/api/capture/${captureId}/upload/frames")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .header("Connection", "close")
                .post(reqBody.toRequestBody(jsonMediaType))
                .build()
            return client.newCall(screenPostRequest).await()
        }
    }

    private suspend fun uploadCaptureMetadata(client: OkHttpClient,
                                              mapper: ObjectMapper,
                                              packageName: String,
                                              traceLabel: String,
                                              captureId: String,
                                              traceEvents: List<String>): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val reqBodyJSONObj = mapper.createObjectNode()
        // set metadata map subfields
        val screensMetaArr = mapper.createArrayNode()
        val gesturesMetaObj = mapper.createObjectNode()
        val redactionsMetaObj = mapper.createObjectNode()
        // loop through screens to get metadata
        for (eventLabel in traceEvents) {
            // retrieve screen metadata and add
            val screenMetaObj = mapper.createObjectNode()
            val screenCreatedAt = eventLabel.substringBefore(DELIM)
            screenMetaObj.put("timestamp", screenCreatedAt)
            val screenGestureType = eventLabel.substringAfter(DELIM)
            val screenId = "${screenCreatedAt}_${screenGestureType}"
            screenMetaObj.put("id", screenId)
            screensMetaArr.add(screenMetaObj)
            // retrieve gesture for screen
            try {
                val gesture: Gesture = loadGesture(packageName, traceLabel, eventLabel)
                // construct gesture body, need to exclude className
                val gestureJSON = ObjectMapper().createObjectNode()
                gestureJSON.put("x", gesture.centerX)
                gestureJSON.put("y", gesture.centerY)
                gestureJSON.put("scrollDeltaX", gesture.scrollDX)
                gestureJSON.put("scrollDeltaY", gesture.scrollDY)
                gestureJSON.put("type", screenGestureType)
                gesturesMetaObj.set<JsonNode>(screenId, gestureJSON)
            } catch (e: FileNotFoundException) {
                // construct gesture body, need to exclude className
                val gestureJSON = ObjectMapper().createObjectNode()
                gestureJSON.put("x", 0)
                gestureJSON.put("y", 0)
                gestureJSON.put("scrollDeltaX", 0)
                gestureJSON.put("scrollDeltaY", 0)
                gestureJSON.put("type", "")
                gesturesMetaObj.set<JsonNode>(screenId, gestureJSON)
            }
            // retrieve redactions for screen
            try {
                // construct redactions
                val redactions = loadRedactions(packageName, traceLabel, eventLabel)
                val redactionsJson = ObjectMapper().createArrayNode()
                for (redaction: Redaction in redactions) {
                    val redactJson = mapper.valueToTree<ObjectNode>(redaction)
                    redactionsJson.add(redactJson)
                }
                redactionsMetaObj.set<ArrayNode>(screenId, redactionsJson)
            } catch (e: FileNotFoundException) {
                // populate with empty array
                redactionsMetaObj.set<ArrayNode>(screenId, ObjectMapper().createArrayNode())
            }
        }
        reqBodyJSONObj.set<ArrayNode>("screens", screensMetaArr)
        reqBodyJSONObj.set<ObjectNode>("gestures", gesturesMetaObj)
        reqBodyJSONObj.set<ObjectNode>("redactions", redactionsMetaObj)
        val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
        // run the POST request
        val screenPostRequest = Request.Builder()
            .url("$API_URL_PREFIX/api/capture/${captureId}/upload/metadata")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .post(reqBody.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(screenPostRequest).await()
    }

    suspend fun uploadFullCapture(
        packageName: String,
        traceLabel: String,
        capture: CaptureStore,
        onProgress: ((numUploaded: Int, total: Int) -> Unit)? = null
    ): Boolean {
        if (!checkWifiConnected()) {
            return false
        }
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
        val client = OkHttpClient()
        var isSuccessUpload: Boolean
        try {
            // Upload VH file
            val mapper = ObjectMapper()
            val traceEvents: List<String> = listEvents(packageName, traceLabel)
            val total = traceEvents.size
            for ((i, event) in traceEvents.withIndex()) {
                // add POST request for screens
                uploadScreenCapture(client, mapper, packageName, traceLabel, capture.id, event).use {
                    isSuccessUpload = it.isSuccessful
                    Log.d("api", "status: ${it.code} message: ${it.message}, body: ${it.body?.string()}")
                }
                if (!isSuccessUpload) {
                    Log.e("api", "fail upload screenshot")
                    return false
                }
                onProgress?.invoke(i + 1, total)
            }
            // add POST request for metadata
            uploadCaptureMetadata(client, mapper, packageName, traceLabel, capture.id, traceEvents).use {
                isSuccessUpload = it.isSuccessful
                Log.d("api", "status: ${it.code}, message: ${it.message}, body: ${it.body?.string()}")
            }
            if (!isSuccessUpload) {
                Log.e("api", "fail upload trace metadata")
                return false
            }
            Log.d("api", "success upload trace")
        } catch (exception: Exception) {
            Log.e("edu.illinois.odim", "Upload failed", exception)
            return false
        }
        return true
    }

//    private fun getAppVersion(packageName: String): Long {
//        val pInfo: PackageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
//        return PackageInfoCompat.getLongVersionCode(pInfo)
//    }

    private fun checkWiFiConnection(): Boolean {
        val connMgr: ConnectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connMgr.activeNetwork
        if (networkCapabilities == null) {
            Log.e("error upload", "no Wi-Fi connection")
            return false
        }
        val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities)
        if (activeNetwork == null) {
            Log.e("error upload", "no Wi-Fi connection")
            return false
        }
        val isWifiConn = activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!isWifiConn) {
            Log.e("error upload", "no Wi-Fi connection")
            return false
        }
        return true
    }

    private fun checkWifiConnected() : Boolean {
        // try to check network status, found non-deprecated solution from:
        // https://stackoverflow.com/questions/49819923/kotlin-checking-network-status-using-connectivitymanager-returns-null-if-networ
        // specifically answered by @AliSh to check if connected to wifi or mobile
        val isWifiConnected = checkWiFiConnection()
        if (!isWifiConnected) {
            // give notification about not connected to wifi
            Toast.makeText(appContext, "Cannot upload without connection to Wi-Fi!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
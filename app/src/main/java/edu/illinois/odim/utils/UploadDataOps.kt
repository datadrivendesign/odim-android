package edu.illinois.odim.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.illinois.odim.DELIM
import edu.illinois.odim.dataclasses.Gesture
import edu.illinois.odim.utils.LocalStorageOps.listEvents
import edu.illinois.odim.utils.LocalStorageOps.loadGesture
import edu.illinois.odim.utils.LocalStorageOps.loadRedactions
import edu.illinois.odim.utils.LocalStorageOps.loadScreenshot
import edu.illinois.odim.utils.LocalStorageOps.loadVH
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.dataclasses.Redaction
import edu.illinois.odim.workerId
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
    private const val OLD_API_URL_PREFIX = "https://api.denizarsan.com"
    private const val API_URL_PREFIX = "https://overhaul-backend.d1z04mdf4ss7va.amplifyapp.com/"

    private suspend fun uploadRedaction(client: OkHttpClient,
                                        mapper: ObjectMapper,
                                        redaction: Redaction,
                                        screenId: String): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val reqBodyJSONObj = mapper.valueToTree<ObjectNode>(redaction)
        reqBodyJSONObj.put("screen", screenId)
        val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
        val vhPostRequest = Request.Builder()
            .url("$OLD_API_URL_PREFIX/redactions")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .post(reqBody.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(vhPostRequest).await()
    }

    private suspend fun uploadScreen(client: OkHttpClient,
                                     mapper: ObjectMapper,
                                     packageName: String,
                                     traceLabel: String,
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
            // retrieve gesture for screen
            val gesture: Gesture = loadGesture(packageName, traceLabel, eventLabel)
            // construct request body
            val reqBodyJSONObj = mapper.createObjectNode()
            reqBodyJSONObj.put("vh", vhString)
            reqBodyJSONObj.put("img", bitmapBase64)
            reqBodyJSONObj.put("created", screenCreatedAt)
            // construct gesture body, need to exclude className
            val gestureJSON = ObjectMapper().createObjectNode()
            gestureJSON.put("x", gesture.centerX)
            gestureJSON.put("y", gesture.centerY)
            gestureJSON.put("scrollDeltaX", gesture.scrollDX)
            gestureJSON.put("scrollDeltaY", gesture.scrollDY)
            reqBodyJSONObj.set<JsonNode>("gesture", gestureJSON)
            val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
            // run the POST request
            val screenPostRequest = Request.Builder()
                .url("$OLD_API_URL_PREFIX/screens")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .header("Connection", "close")
                .post(reqBody.toRequestBody(jsonMediaType))
                .build()
            return client.newCall(screenPostRequest).await()
        }
    }

    private suspend fun uploadScreenCapture(client: OkHttpClient,
                                            mapper: ObjectMapper,
                                            packageName: String,
                                            traceLabel: String,
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
            // construct request body
            val reqBodyJSONObj = mapper.createObjectNode()
            reqBodyJSONObj.put("vh", vhString)
            reqBodyJSONObj.put("img", bitmapBase64)
            reqBodyJSONObj.put("created", screenCreatedAt)
            // retrieve gesture for screen
            try {
                val gesture: Gesture = loadGesture(packageName, traceLabel, eventLabel)
                // construct gesture body, need to exclude className
                val gestureJSON = ObjectMapper().createObjectNode()
                gestureJSON.put("x", gesture.centerX)
                gestureJSON.put("y", gesture.centerY)
                gestureJSON.put("scrollDeltaX", gesture.scrollDX)
                gestureJSON.put("scrollDeltaY", gesture.scrollDY)
                reqBodyJSONObj.set<JsonNode>("gesture", gestureJSON)
            } catch (e: FileNotFoundException) {
                // construct gesture body, need to exclude className
                val gestureJSON = ObjectMapper().createObjectNode()
                gestureJSON.put("x", 0)
                gestureJSON.put("y", 0)
                gestureJSON.put("scrollDeltaX", 0)
                gestureJSON.put("scrollDeltaY", 0)
                reqBodyJSONObj.set<JsonNode>("gesture", gestureJSON)
            }
            val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
            // run the POST request
            val screenPostRequest = Request.Builder()
                .url("$API_URL_PREFIX/screens")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .header("Connection", "close")
                .post(reqBody.toRequestBody(jsonMediaType))
                .build()
            return client.newCall(screenPostRequest).await()
        }
    }

    private fun getAppVersion(packageName: String): Long {
        val pInfo: PackageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pInfo)
    }

    private suspend fun uploadTrace(
        client: OkHttpClient,
        mapper: ObjectMapper,
        screenIds: List<String>,
        packageName: String,
        traceLabel: String,
        traceDescription: String): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        // construct request body
        val reqBodyJSONObj = mapper.createObjectNode()
        reqBodyJSONObj.putArray("screens").addAll(mapper.valueToTree(screenIds) as ArrayNode)
        reqBodyJSONObj.put("app", packageName)
        reqBodyJSONObj.put("created", traceLabel)
        reqBodyJSONObj.put("description", traceDescription)
        reqBodyJSONObj.put("worker", workerId)
        reqBodyJSONObj.put("version", getAppVersion(packageName))
        val reqBody = mapper.writeValueAsString(reqBodyJSONObj)
        // send post request
        val tracePostRequest = Request.Builder()
            .url("$OLD_API_URL_PREFIX/traces")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .post(reqBody.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(tracePostRequest).await()
    }

    private fun checkWiFiConnection(): Boolean {
        val connMgr: ConnectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connMgr.activeNetwork
        if (networkCapabilities == null) {
            Log.e("error upload", "no Wi-Fi connection")
            return false
        }
        val activeNetwork = connMgr.getNetworkCapabilities(networkCapabilities) //?: return false
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

    private suspend fun updateScreen(
        client: OkHttpClient,
        mapper: ObjectMapper,
        screenBody: ObjectNode,
        traceId: String
    ): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        // construct request body
        screenBody.put("trace", traceId)
        val screenId = screenBody.get("_id").asText()
        val reqBody = mapper.writeValueAsString(screenBody)
        // send post request
        val screenPutRequest = Request.Builder()
            .url("$OLD_API_URL_PREFIX/screens/${screenId}")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .put(reqBody.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(screenPutRequest).await()
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

    suspend fun uploadFullCapture(packageName: String, traceLabel: String): Boolean {
        if (!checkWifiConnected()) {
            return false
        }
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
        val client = OkHttpClient()
        var isSuccessUpload = true
        try {
            // Upload VH file
            val mapper = ObjectMapper()
            val traceEvents: List<String> = listEvents(packageName, traceLabel)
            for (event in traceEvents) {
                // add POST request for screens
                uploadScreenCapture(client, mapper, packageName, traceLabel, event).use {
                    isSuccessUpload = it.isSuccessful
                }
                if (!isSuccessUpload) { //&& screenId.isNotEmpty()) {
                    Log.e("api", "fail upload screenshot")
                    return false
                }
            }
            Log.d("api", "success upload trace")
        } catch (exception: Exception) {
            Log.e("edu.illinois.odim", "Upload failed", exception)
            return false
        }
        return isSuccessUpload
    }

    suspend fun uploadFullTraceContent(
        packageName: String,
        traceLabel: String,
        traceDescription: String
    ) : Boolean {
        if (!checkWifiConnected()) {
            return false
        }
        Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
        val client = OkHttpClient()
        var isSuccessUpload: Boolean
        try {
            // Upload VH file
            val mapper = ObjectMapper()
            val screenBodyData = ArrayList<ObjectNode>()
            val traceEvents: List<String> = listEvents(packageName, traceLabel)
            for (event in traceEvents) {
                // add POST request for screens
                val returnedScreen: ObjectNode
                uploadScreen(client, mapper, packageName, traceLabel, event).use {
                    isSuccessUpload = it.isSuccessful
                    val screenResBodyStr = it.body?.string() ?: "{}"
                    returnedScreen = mapper.readTree(screenResBodyStr).deepCopy()
                }
                val screenId = returnedScreen.get("_id").asText()
                if (isSuccessUpload && screenId.isNotEmpty()) {
                    Log.d("api", "success upload screenshot")
                    screenBodyData.add(returnedScreen)
                } else {
                    Log.e("api", "fail upload screenshot")
                    return false
                }
                // add POST request for redactions
                val redactions = loadRedactions(packageName, traceLabel, event)
                for (redaction: Redaction in redactions) {
                    uploadRedaction(client, mapper, redaction, screenId).use {
                        isSuccessUpload = it.isSuccessful
                    }
                    if (isSuccessUpload) {
                        Log.d("api", "success upload redaction")
                    } else {
                        Log.e("api", "fail upload redaction")
                        return false
                    }
                }
            }
            // add POST request for traces
            val screenIds = screenBodyData.map{ it.get("_id").asText() }
            val traceId: String
            uploadTrace(client, mapper, screenIds, packageName, traceLabel, traceDescription).use {
                isSuccessUpload = it.isSuccessful
                val traceResBodyStr = it.body?.string() ?: "{}"
                traceId = mapper.readTree(traceResBodyStr).get("_id").asText()
            }
            if (isSuccessUpload && traceId.isNotEmpty()) {
                for (screenBody in screenBodyData) {
                    updateScreen(client, mapper, screenBody, traceId).use {
                        isSuccessUpload = it.isSuccessful
                    }
                }
            }
            if (isSuccessUpload) {
                Log.d("api", "success upload trace")
            } else {
                Log.e("api", "fail upload trace")
            }
        } catch (exception: Exception) {
            Log.e("edu.illinois.odim", "Upload failed", exception)
            return false
        }
        return isSuccessUpload
    }
}
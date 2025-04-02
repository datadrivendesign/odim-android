package edu.illinois.odim.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.mlkit.vision.barcode.common.Barcode
import edu.illinois.odim.databinding.ActivityCaptureBinding
import edu.illinois.odim.dataclasses.CaptureTask
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

class CaptureActivity: AppCompatActivity() {
    private val cameraPermission = android.Manifest.permission.CAMERA
    private lateinit var binding: ActivityCaptureBinding
    private lateinit var captureTask: CaptureTask

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startScanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // set layout bindings based on whether capture task data exists
        if (!::captureTask.isInitialized) {
            binding.layoutCaptureInfo.visibility = View.GONE
            binding.buttonOpenScanner.visibility = View.VISIBLE
            binding.textErrorMessage.visibility = View.VISIBLE
        } else {
            populateCaptureLayout()
        }
        binding.buttonOpenScanner.setOnClickListener {
            requestCameraAndStartScanner()
        }
        binding.buttonInstallApp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=${captureTask.capture.appId}")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraAndStartScanner() {
        if (isPermissionGranted(cameraPermission)) {
            startScanner()
        } else {
            requestCameraPermission()
        }
    }

    inline fun Context.cameraPermissionRequest(crossinline positive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("Without accessing the camera it is not possible to SCAN QR Codes...")
            .setPositiveButton("Allow Camera") { dialog                                                                                                                                                                                                                                , which ->
                positive.invoke()
            }.setNegativeButton("Cancel") { dialog, which ->

            }.show()
    }

    fun Context.openPermissionSetting() {
        Intent(ACTION_APPLICATION_DETAILS_SETTINGS).also {
            val uri: Uri = Uri.fromParts("package", packageName, null)
            it.data = uri
            startActivity(it)
        }
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                cameraPermissionRequest(
                    positive = { openPermissionSetting() }
                )
            }
            else -> {
                requestPermissionLauncher.launch(cameraPermission)
            }
        }
    }

    private fun populateCaptureLayout() {
        binding.layoutCaptureInfo.visibility = View.VISIBLE
        binding.textViewAppInstall.text = captureTask.capture.appId
        binding.textViewTaskDescription.text = captureTask.task.description
    }

    private fun makeGetRequest(url: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GET Request", "Failed: ${e.message}")
                runOnUiThread { binding.textErrorMessage.text = "URL call failed" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: "null"
                val mapper: ObjectMapper = jacksonObjectMapper()
                try {
                    Log.d("GET", responseBody)
                    captureTask = mapper.readValue(responseBody)
                    runOnUiThread {
                        binding.textErrorMessage.text = ""
                        populateCaptureLayout()
                    }
                } catch (ex: Exception) {
                    runOnUiThread { binding.textErrorMessage.text = "Response Body is not JSON" }
                    Log.e("GET", ex.message.toString())
                }

            }
        })
    }

    private fun startScanner() {
        ScannerActivity.startScanner(this) { barcodes ->
            barcodes.forEach { barcode ->
                when (barcode.valueType) {
                    Barcode.TYPE_URL -> {
                        makeGetRequest(barcode.url?.url.toString())
                    }
                    else -> {
                        binding.textErrorMessage.text = "Incorrect QR Content: Not URL"
                    }
                }
            }
        }
    }
}
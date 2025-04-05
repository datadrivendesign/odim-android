package edu.illinois.odim.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.mlkit.vision.barcode.common.Barcode
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
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
    private var captureTask: CaptureTask? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // set layout bindings based on whether capture task data exists
        if (captureTask == null) {
            binding.layoutCaptureInfo.visibility = View.GONE
            binding.layoutScanQr.visibility = View.VISIBLE
        } else {
            populateCaptureLayout()
        }
        // set button onClicks in rescan QR UI
        binding.buttonOpenScanner.setOnClickListener {
            requestCameraAndStartScanner()
        }
        binding.buttonQrGoTrace.setOnClickListener {
            val intent = Intent(applicationContext, AppActivity::class.java)
            startActivity(intent)
        }

        // set button onClicks in task instruction UI
        binding.buttonInstallApp.setOnClickListener {
           captureTask?.capture?.appId?.let { appId ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${appId}")
                    setPackage("com.android.vending")
                }
                startActivity(intent)
            }
        }
        binding.buttonNavigateSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.buttonOpenApp.setOnClickListener { btn ->
            captureTask?.capture?.appId?.let { appId ->
                val launchIntent = packageManager.getLaunchIntentForPackage(appId)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(appContext, "App not installed", Toast.LENGTH_SHORT).show()
                    btn.isEnabled = false
                }
            }
        }
        binding.buttonTaskGoTrace.setOnClickListener {
            startActivity(Intent(applicationContext, AppActivity::class.java))
        }
        binding.buttonRescanQr.setOnClickListener {
            binding.layoutScanQr.visibility = View.VISIBLE
            binding.layoutCaptureInfo.visibility = View.GONE
            captureTask = null
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

    private inline fun Context.cameraPermissionRequest(crossinline positive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("Without accessing the camera it is not possible to SCAN QR Codes...")
            .setPositiveButton("Allow Camera") { dialog                                                                                                                                                                                                                                , which ->
                positive.invoke()
            }.setNegativeButton("Cancel") { dialog, which ->

            }.show()
    }

    private fun Context.openPermissionSetting() {
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
        binding.layoutScanQr.visibility = View.GONE
        binding.layoutCaptureInfo.visibility = View.VISIBLE
        val appPackageName = captureTask?.capture?.appId
        val taskDescription = captureTask?.task?.description
        try {
            appPackageName?.let {
                val appIcon = packageManager.getApplicationIcon(it)
                binding.captureAppIcon.setImageDrawable(appIcon)
                val appInfo = packageManager.getApplicationInfo(it, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                binding.textAppName.text = appName
            }
        } catch (nameNotFound: NameNotFoundException) {
            binding.textAppName.text = appPackageName.toString()
        }
        binding.textTaskDescription.text = taskDescription.toString()
    }

    private fun makeGetRequest(url: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GET Request", "Failed: ${e.message}")
                Toast.makeText(appContext, "URL call failed", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: "null"
                val mapper: ObjectMapper = jacksonObjectMapper()
                try {
                    Log.d("GET", responseBody)
                    captureTask = mapper.readValue(responseBody)
                    runOnUiThread {
                        populateCaptureLayout()
                    }
                } catch (ex: Exception) {
                    Log.e("GET", ex.message.toString())
                    Toast.makeText(appContext, "Response Body is not JSON", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(appContext, "Incorrect QR Content: Not URL", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
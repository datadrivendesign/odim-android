package edu.illinois.odim

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.MyAccessibilityService.Companion.isAppContextInitialized
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object LocalStorageOps {
    private const val TRACES_DIR = "TRACES"
    private const val VH_PREFIX = "vh-"
    const val GESTURE_PREFIX = "gesture-"
    private const val REDACT_PREFIX = "redact-"
    private val kotlinMapper = ObjectMapper().registerKotlinModule()

    fun listPackages(): MutableList<String> {
        if (!isAppContextInitialized()) {
            return arrayListOf()
        }
        val packageDir = File(appContext.filesDir, TRACES_DIR)
        return if(packageDir.exists()) {
            packageDir.listFiles()?.filter {
                it.isDirectory
            }?.map {
                it.name
            }?.toMutableList() ?: arrayListOf()
        } else {
            packageDir.mkdir()
            arrayListOf()
        }
    }

    fun listTraces(packageName: String): MutableList<String> {
        val traceDir = File(appContext.filesDir, "$TRACES_DIR/$packageName")
        return if (traceDir.exists()) {
            return traceDir.listFiles()?.filter {
                it.isDirectory
            }?.map {
                it.name
            }?.sortedDescending()?.toMutableList() ?: arrayListOf()
        } else {
            traceDir.mkdirs()
            arrayListOf()
        }
    }

    fun listEvents(packageName: String, trace: String): MutableList<String>  {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace")
        return if (eventDir.exists()) {
            eventDir.listFiles()?.filter {
                it.isDirectory
            }?.map {
                it.name
            }?.sorted()?.toMutableList() ?: arrayListOf()
        } else {
            eventDir.mkdirs()
            arrayListOf()
        }
    }

    fun listEventData(packageName: String, trace: String, event: String): MutableList<String> {
        val eventFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        return if (eventFile.exists()) {
            eventFile.listFiles()?.map {
                it.name
            }?.sorted()?.toMutableList() ?: arrayListOf()
        } else {
            arrayListOf()
        }
    }

    fun renameEvent(packageName: String, trace: String, event: String, newEventName: String): Boolean {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        val newEventDirName = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$newEventName")
        if (!eventDir.exists()) {
            Log.e("FILE", "Directory to rename does not exist: ${eventDir.path}")
            return false
        }
        return if (eventDir.renameTo(newEventDirName)) {
            true
        } else {
            Log.e("FILE", "cannot rename $event directory")
            false
        }
    }

    fun deleteApp(packageName: String): Boolean {
        val traceFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/")
        val result = traceFile.deleteRecursively()
        return if (result) {
            true
        } else {
            Log.e("FILE", "cannot delete $packageName directory")
            false
        }
    }

    fun deleteTrace(packageName: String, trace: String): Boolean {
        val traceFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace")
        val result = traceFile.deleteRecursively()
        return if (result) {
            true
        } else {
            Log.e("FILE", "cannot delete $trace directory")
            false
        }
    }

    fun deleteEvent(packageName: String, trace: String, event: String): Boolean {
        val eventFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        val result = eventFile.deleteRecursively()
        return if (result) {
            true
        } else {
            Log.e("FILE", "cannot delete $trace directory")
            false
        }
    }

    fun loadScreenshot(packageName: String, trace: String, event: String): Bitmap {
        val screenFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$event.png")
        if (screenFile.exists()) {
            val bitmapBytes = screenFile.readBytes()
            return BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.size)
        } else {
            throw FileNotFoundException("Screenshot was not found")
        }
    }

    fun renameScreenshot(packageName: String, trace: String, event: String, newEventName: String): Boolean {
        val screenshotName = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$event.png")
        val newScreenshotName = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$newEventName.png")
        if (!screenshotName.exists()) {
            Log.e("FILE", "Screenshot to rename does not exist: ${screenshotName.path}")
            return false
        }
        return if (screenshotName.renameTo(newScreenshotName)) {
            true
        } else {
            Log.e("FILE", "cannot rename $event screen")
            false
        }
    }

    fun saveScreenshot(packageName: String, trace: String, event: String, screen: Bitmap): Boolean {
        return try {
            val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
            if (!eventDir.exists()) {
                eventDir.mkdirs()
            }
            val fileScreen = File(eventDir, "$event.png")
            FileOutputStream(fileScreen, false).use { stream ->
                if(!screen.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Couldn't save bitmap")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun loadVH(packageName: String, trace: String, event: String): String {
        val jsonFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$event.json")
        if (jsonFile.exists()) {
            return jsonFile.readText(Charsets.UTF_8)
        } else {
            throw FileNotFoundException("VH was not found")
        }
    }

    fun renameVH(packageName: String, trace: String, event: String, newEventName: String): Boolean {
        val vHName = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$event.json")
        val newVHName = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$newEventName.json")
        if (!vHName.exists()) {
            Log.e("FILE", "File to rename does not exist: ${vHName.path}")
            return false
        }
        return if (vHName.renameTo(newVHName)) {
            true
        } else {
            Log.e("FILE", "cannot rename $event VH")
            false
        }
    }

    fun saveVH(packageName: String, trace: String, event: String, vhJsonString: String) : Boolean {
        return try {
            val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
            if (!eventDir.exists()) {
                eventDir.mkdirs()
            }
            val fileVH = File(eventDir, "$VH_PREFIX$event.json")
            FileOutputStream(fileVH, false).use { stream ->
                stream.write(vhJsonString.toByteArray())
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun loadGesture(packageName: String, trace: String, event: String): Gesture {
        val jsonFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$GESTURE_PREFIX$event.json")
        if (jsonFile.exists()) {
            val gestureJsonString = jsonFile.readText(Charsets.UTF_8)
            return kotlinMapper.readValue<Gesture>(gestureJsonString)
        } else {
            throw FileNotFoundException("Gesture was not found")
        }
    }

    fun saveGesture(packageName: String, trace: String, event: String, gesture: Gesture) : Boolean {
        return try {
            val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
            if (!eventDir.exists()) {
                eventDir.mkdirs()
            }
            val fileGestures = File(eventDir, "$GESTURE_PREFIX$event.json")
            FileOutputStream(fileGestures, false).use { stream ->
                val gestureJson = kotlinMapper.writeValueAsBytes(gesture)
                stream.write(gestureJson)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun loadRedactions(packageName: String, trace: String, event: String): MutableSet<Redaction> {
        val redactFile = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$REDACT_PREFIX$event.json")
        return if (redactFile.exists()) {
            val redactJsonString = redactFile.readText(Charsets.UTF_8)
            Log.d("redact string", redactJsonString)
            kotlinMapper.readValue<MutableSet<Redaction>>(redactJsonString)
        } else {
            mutableSetOf()
        }
    }

    fun saveRedactions(packageName: String, trace: String, event: String, redaction: Redaction) : Boolean {
        return try {
            val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
            if (!eventDir.exists()) {
                eventDir.mkdirs()
            }
            val redactions = mutableSetOf<Redaction>()
            val fileRedacts = File(eventDir, "$REDACT_PREFIX$event.json")
            if (fileRedacts.exists()) {
                val currRedacts = loadRedactions(packageName, trace, event)
                redactions.addAll(currRedacts)
            }
            redactions.add(redaction)
            // write redactions to local storage
            FileOutputStream(fileRedacts, false).use { stream ->
                stream.write(kotlinMapper.writeValueAsBytes(redactions))
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
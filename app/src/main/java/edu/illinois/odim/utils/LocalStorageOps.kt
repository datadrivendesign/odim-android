package edu.illinois.odim.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import edu.illinois.odim.dataclasses.Gesture
import edu.illinois.odim.MyAccessibilityService.Companion.appContext
import edu.illinois.odim.MyAccessibilityService.Companion.isAppContextInitialized
import edu.illinois.odim.dataclasses.Redaction
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
            return traceDir.listFiles()?.asSequence()?.filter { it.isDirectory }
                ?.map { it.name }
                ?.sortedDescending()
                ?.toMutableList() ?: arrayListOf()
        } else {
            traceDir.mkdirs()
            arrayListOf()
        }
    }

    fun renameTrace(packageName: String, trace: String, newTrace: String): Boolean {
        val traceDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace")
        val newTraceDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$newTrace")
        if (!traceDir.exists()) {
            Log.e("FILE", "Directory to rename does not exist: ${traceDir.path}")
            return false
        }
        return if (traceDir.renameTo(newTraceDir)) {
            true
        } else {
            Log.e("FILE", "cannot rename directory $trace to $newTrace")
            false
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

    fun renameEvent(packageName: String, trace: String, event: String, newEvent: String): Boolean {
        val eventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event")
        val newEventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$newEvent")
        if (!eventDir.exists()) {
            Log.e("FILE", "Directory to rename does not exist: ${eventDir.path}")
            return false
        }
        return if (eventDir.renameTo(newEventDir)) {
            true
        } else {
            Log.e("FILE", "cannot rename directory $event to $newEvent")
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

    fun renameScreenshot(packageName: String, trace: String, event: String, newEvent: String): Boolean {
        val screenshot = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$event.png")
        val newScreenshot = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$newEvent.png")
        if (!screenshot.exists()) {
            Log.e("FILE", "Screenshot to rename does not exist: ${screenshot.path}")
            return false
        }
        return if (screenshot.renameTo(newScreenshot)) {
            true
        } else {
            Log.e("FILE", "cannot rename screen $event to $newEvent")
            false
        }
    }
    
    fun splitTraceScreenshot(packageName: String, trace: String, newTrace: String, event: String): Boolean {
        val screenshot = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$event.png")
        val newEventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$newTrace/$event")
        if (!newEventDir.exists()) {
            newEventDir.mkdirs()
        }
        val newScreenshot = File(newEventDir, "$event.png")
        if (!screenshot.exists()) {
            Log.e("FILE", "Screenshot to move does not exist: ${screenshot.path}")
            return false
        }
        return if (screenshot.renameTo(newScreenshot)) {
            true
        } else {
            Log.e("FILE", "cannot move screen from $trace to $newTrace")
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

    fun renameVH(packageName: String, trace: String, event: String, newEvent: String): Boolean {
        val vH = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$event.json")
        val newVH = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$newEvent.json")
        if (!vH.exists()) {
            Log.e("FILE", "File to rename does not exist: ${vH.path}")
            return false
        }
        return if (vH.renameTo(newVH)) {
            true
        } else {
            Log.e("FILE", "cannot rename vh $event to $newEvent")
            false
        }
    }

    fun splitTraceVH(packageName: String, trace: String, newTrace: String, event: String): Boolean {
        val vh = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$VH_PREFIX$event.json")
        val newEventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$newTrace/$event")
        if (!newEventDir.exists()) {
            newEventDir.mkdirs()
        }
        val newVH = File(newEventDir, "$VH_PREFIX$event.json")
        if (!vh.exists()) {
            Log.e("FILE", "View hierarchy to move does not exist: ${vh.path}")
            return false
        }
        return if (vh.renameTo(newVH)) {
            true
        } else {
            Log.e("FILE", "cannot move vh from $trace to $newTrace")
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

    fun renameGesture(packageName: String, trace: String, event: String, newEvent: String): Boolean {
        val gesture = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$GESTURE_PREFIX$event.json")
        val newGesture = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$GESTURE_PREFIX$newEvent.json")
        if (!gesture.exists()) {
            Log.e("FILE", "Gesture to rename does not exist: ${gesture.path}")
            return false
        }
        return if (gesture.renameTo(newGesture)) {
            true
        } else {
            Log.e("FILE", "cannot rename gesture $event to ${newEvent}")
            false
        }
    }

    fun splitTraceGesture(packageName: String, trace: String, newTrace: String, event: String): Boolean {
        val gesture = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$GESTURE_PREFIX$event.json")
        val newEventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$newTrace/$event")
        if (!newEventDir.exists()) {
            newEventDir.mkdirs()
        }
        val newGesture = File(newEventDir, "$GESTURE_PREFIX$event.json")
        if (!gesture.exists()) {
            Log.e("FILE", "Gesture to move does not exist: ${gesture.path}")
            return false
        }
        return if (gesture.renameTo(newGesture)) {
            true
        } else {
            Log.e("FILE", "cannot move gesture from $trace to $newTrace")
            false
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

    fun saveRedaction(packageName: String, trace: String, event: String, redaction: Redaction) : Boolean {
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

    fun splitTraceRedactions(packageName: String, trace: String, newTrace: String, event: String): Boolean {
        val redacts = File(appContext.filesDir, "$TRACES_DIR/$packageName/$trace/$event/$REDACT_PREFIX$event.json")
        val newEventDir = File(appContext.filesDir, "$TRACES_DIR/$packageName/$newTrace/$event")
        if (!newEventDir.exists()) {
            newEventDir.mkdirs()
        }
        val newRedacts = File(newEventDir, "$REDACT_PREFIX$event.json")
        if (!redacts.exists()) {
            Log.e("FILE", "Gesture to move does not exist: ${redacts.path}")
            return false
        }
        return if (redacts.renameTo(newRedacts)) {
            true
        } else {
            Log.e("FILE", "cannot move gesture from $trace to $newTrace")
            false
        }
    }
}
package edu.illinois.odim

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService{

    private lateinit var lastPackageName : String

    private val packageList = ArrayList<String>()
    private val packageSet = HashSet<String>()

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        TODO("Not yet implemented")
    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
    }
}
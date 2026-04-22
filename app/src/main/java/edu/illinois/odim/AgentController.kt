package edu.illinois.odim

import android.util.Log
import edu.illinois.odim.dataclasses.AgentAction
import edu.illinois.odim.dataclasses.Gesture
import edu.illinois.odim.dataclasses.StepRecord
import edu.illinois.odim.network.AgentBrain
import edu.illinois.odim.network.ClaudeClient
import edu.illinois.odim.network.OllamaClient
import edu.illinois.odim.utils.AgentUtils
import edu.illinois.odim.utils.LocalStorageOps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

class AgentController(private val service: MyAccessibilityService) {
    private val TAG = "AgentController"
    private var agentJob: Job? = null
    private val history = mutableListOf<StepRecord>()
    private var stepCount = 0
    private val MAX_STEPS = 10

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var currentBrain: AgentBrain = ClaudeClient

    fun setBrain(useOllama: Boolean) {
        currentBrain = if (useOllama) OllamaClient else ClaudeClient
        Log.i(TAG, "Brain switched to: ${if (useOllama) "Ollama" else "Claude"}")
    }

    fun startAgent(goal: String) {
        if (agentJob?.isActive == true) return

        _isRunning.value = true
        service.updateOverlayState(true)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val goalSlug = goal.take(20).replace(" ", "_").lowercase()
        val traceLabel = "agent_${timestamp}_$goalSlug"

        agentJob = service.serviceScope.launch(Dispatchers.Main) {
            Log.i(TAG, "Starting Agent with goal: $goal")
            history.clear()
            stepCount = 0
            var lastResizedScreenshot: android.graphics.Bitmap? = null

            while (stepCount < MAX_STEPS && isActive) {
                Log.d(TAG, "⏳ [WAIT] Settling UI for 2.5s...")
                delay(2500)
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val eventLabel = "$timestamp$DELIM${service.getString(R.string.type_agent)}"

                Log.i(TAG, "------------------ STEP $stepCount ------------------")
                
                // --- PHASE: CAPTURE ---
                Log.d(TAG, "👁️ [CAPTURE] Step $stepCount: Starting state acquisition")
                val state = service.captureAndSaveState(traceLabel, eventLabel, MyAccessibilityService.ODIM_PACKAGE)
                if (state == null) {
                    val errorMsg = "❌ [ERROR] Failed to capture state at step $stepCount"
                    Log.e(TAG, errorMsg)
                    LocalStorageOps.appendAgentLog(MyAccessibilityService.ODIM_PACKAGE, traceLabel, """{"step":$stepCount,"phase":"ERROR","message":"$errorMsg"}""")
                    break
                }

                val elements = AgentUtils.flattenVH(state.vh.toString())

                // --- PHASE: THINK ---
                Log.i(TAG, "🧠 [THINK] Step $stepCount: Sending to ${if (currentBrain is OllamaClient) "Ollama" else "Claude"}...")
                val currentResizedScreenshot = AgentUtils.resizeBitmap(state.screenshot, 0.5f)
                
                val startTime = System.currentTimeMillis()
                val response = currentBrain.getResponse(goal, currentResizedScreenshot, lastResizedScreenshot, elements, history)
                val latencyMs = System.currentTimeMillis() - startTime

                if (response == null) {
                    val errorMsg = "❌ [ERROR] LLM failed to return a response"
                    Log.e(TAG, errorMsg)
                    LocalStorageOps.appendAgentLog(MyAccessibilityService.ODIM_PACKAGE, traceLabel, """{"step":$stepCount,"phase":"ERROR","message":"$errorMsg","latency_ms":$latencyMs}""")
                    break
                }

                // Structured Logging
                val stepLog = """{"step":$stepCount,"phase":"STEP_COMPLETE","action":"${response.action.actionType}","reason":"${response.reason.replace("\"", "\\\"")}","reflection":"${response.reflection.replace("\"", "\\\"")}","actual_package":"${state.packageName}","latency_ms":$latencyMs,"elements_count":${elements.size},"timestamp":${System.currentTimeMillis()}}"""
                Log.i("AGENT_STEP", "📝 $stepLog")
                LocalStorageOps.appendAgentLog(MyAccessibilityService.ODIM_PACKAGE, traceLabel, stepLog)

                Log.i(TAG, "💡 [REASON] ${response.reason}")
                Log.i(TAG, "💭 [REFLECTION] ${response.reflection}")

                LocalStorageOps.saveAgentReasoning(MyAccessibilityService.ODIM_PACKAGE, traceLabel, eventLabel, "${response.reason}\n\nReflection: ${response.reflection}")
                val record = StepRecord(stepCount, response.action, response.reason, response.reflection)
                history.add(record)

                // Update last screenshot for next iteration
                lastResizedScreenshot = currentResizedScreenshot

                if (response.action.actionType == "status") {
                    Log.i(TAG, "🏁 [FINISH] Agent goal reached: ${response.action.goalStatus}")
                    break
                }

                // --- PHASE: ACT ---
                Log.i(TAG, "🎬 [ACT] Step $stepCount: Executing ${response.action.actionType}")
                val gesture = convertActionToGesture(response.action, elements)
                if (gesture != null) {
                    LocalStorageOps.saveGesture(MyAccessibilityService.ODIM_PACKAGE, traceLabel, eventLabel, gesture)
                }
                
                service.executeAction(response.action, elements)
                stepCount++
            }
            Log.i(TAG, "Agent Loop Terminated")
            service.updateOverlayState(false)
            stopAgent()
        }
    }

    fun stopAgent() {
        if (agentJob?.isActive == true) {
            agentJob?.cancel()
        }
        _isRunning.value = false
        service.updateOverlayState(false)
        Log.i(TAG, "Agent Stopped Manually")
    }

    private fun convertActionToGesture(action: AgentAction, elements: List<edu.illinois.odim.dataclasses.ActionableElement>): Gesture? {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        return when (action.actionType.lowercase()) {
            "click" -> {
                val element = elements.find { it.index == action.index }
                if (element != null) {
                    Gesture(
                        centerX = element.center.x.toFloat() / width,
                        centerY = element.center.y.toFloat() / height,
                        scrollDX = 0f,
                        scrollDY = 0f,
                        viewId = element.resourceId,
                        actionType = "click"
                    )
                } else null
            }
            "type" -> {
                Gesture(
                    centerX = 0.5f,
                    centerY = 0.5f,
                    scrollDX = 0f,
                    scrollDY = 0f,
                    viewId = null,
                    actionType = "type",
                    text = action.text
                )
            }
            "scroll" -> {
                val (dx, dy) = when (action.direction?.lowercase()) {
                    "up" -> 0f to -0.2f
                    "down" -> 0f to 0.2f
                    "left" -> -0.2f to 0f
                    "right" -> 0.2f to 0f
                    else -> 0f to 0f
                }
                Gesture(
                    centerX = 0.5f,
                    centerY = 0.5f,
                    scrollDX = dx,
                    scrollDY = dy,
                    viewId = null,
                    actionType = "scroll",
                    text = action.direction
                )
            }
            "navigate_back", "navigate_home" -> {
                Gesture(
                    centerX = 0.5f,
                    centerY = 0.95f,
                    scrollDX = 0f,
                    scrollDY = 0f,
                    viewId = null,
                    actionType = action.actionType.lowercase()
                )
            }
            "status" -> {
                Gesture(
                    centerX = 0.5f,
                    centerY = 0.1f,
                    scrollDX = 0f,
                    scrollDY = 0f,
                    viewId = null,
                    actionType = "status",
                    text = action.goalStatus
                )
            }
            else -> null
        }
    }
}

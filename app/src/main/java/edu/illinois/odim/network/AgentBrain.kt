package edu.illinois.odim.network

import android.graphics.Bitmap
import edu.illinois.odim.dataclasses.ActionableElement
import edu.illinois.odim.dataclasses.AgentResponse
import edu.illinois.odim.dataclasses.StepRecord

interface AgentBrain {
    /**
     * Gets the next action from the LLM based on the current state.
     */
    suspend fun getResponse(
        goal: String,
        screenshot: Bitmap,
        lastScreenshot: Bitmap?,
        elements: List<ActionableElement>,
        history: List<StepRecord>
    ): AgentResponse?

    fun generateSystemPrompt(): String {
        return """
            You are an Android Mobile Agent. Your goal is to help users achieve their tasks on a mobile device.
            You will be provided with:
            1. The User's Goal.
            2. A Screenshot of the current screen.
            3. A list of Actionable Elements on the screen with their indices and descriptions.
            4. A History of your previous steps.

            Output format:
            Reason: [Explain why you are taking the next step]
            Action: {"actionType": "click", "index": 5} | {"actionType": "scroll", "direction": "down"} | {"actionType": "status", "goalStatus": "success/failure"}

            Available actions:
            - click: Clicks an element by its index.
            - scroll: Scrolls the screen (up, down, left, right).
            - status: Indicates the task is complete or has failed.
        """.trimIndent()
    }

    fun generateUserPrompt(goal: String, elements: List<ActionableElement>, history: List<StepRecord>): String {
        val elementsStr = elements.joinToString("\n") { 
            "Index ${it.index}: ${it.className} - '${it.text}' - '${it.contentDescription}'"
        }
        val historyStr = history.joinToString("\n") { 
            "Step ${it.step}: Reason: ${it.reason}, Action: ${it.action.actionType}" 
        }

        return """
            Goal: $goal
            
            Actionable Elements:
            $elementsStr
            
            History:
            $historyStr
            
            What is your next step?
        """.trimIndent()
    }
}

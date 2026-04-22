package edu.illinois.odim.network

import edu.illinois.odim.dataclasses.ActionableElement
import edu.illinois.odim.dataclasses.StepRecord

object AgentPrompts {

    const val SYSTEM_PROMPT = """
        You are a mobile agent that can interact with an Android device.
        You are given a goal, a screenshot of the current screen, and a numbered list of interactive UI elements.
        Decide the next action to take to achieve the goal.

        Available actions:
        - click(index): tap the element at the given index number
        - type(text): type text into the currently focused input field
        - scroll(direction): scroll "up", "down", "left", or "right"
        - navigate_back: press the back button
        - navigate_home: press the home button
        - status(goal_status): report "complete" when the goal is done, or "infeasible" if the goal cannot be accomplished

        Respond in exactly this format:
        Reason: <one sentence explaining your reasoning for the next action>
        Reflection: <If this is the first step, say "Initial step". Otherwise, examine the previous action and the transition from the previous screenshot to the current one. Did the action work? Did the screen change as expected? If you are stuck in a loop, explain why and what to change.>
        Action: {"actionType": "click", "index": 5}
        
        IMPORTANT: DO NOT use "swipe". Use "scroll(direction)" for all scrolling and swiping needs.
        Valid JSON keys for Action: "actionType", "index", "text", "direction", "goalStatus".
        Note: Use camelCase for JSON keys (e.g., "actionType", "goalStatus").
        The Action field MUST be a valid JSON object matching the AgentAction data class.
    """

    fun formatUserPrompt(
        goal: String,
        elements: List<ActionableElement>,
        history: List<StepRecord>
    ): String {
        val elementsText = elements.joinToString("\n") { elem ->
            "[${elem.index}] ${elem.className} \"${elem.text.ifEmpty { elem.contentDescription }}\" (${elem.center.x}, ${elem.center.y}) " +
                    (if (elem.isClickable) "[clickable] " else "") +
                    (if (elem.isScrollable) "[scrollable] " else "") +
                    (if (elem.isEditable) "[editable] " else "") +
                    (if (elem.isFocused) "[focused] " else "")
        }

        val historyText = if (history.isEmpty()) "None" else history.joinToString("\n") { record ->
            "Step ${record.step}: Action=${record.action.actionType}, Reason=${record.reason}${if (record.reflection != null) ", Reflection=${record.reflection}" else ""}"
        }

        val historyHint = if (history.isNotEmpty()) {
            "You have been provided with the current screenshot AND the previous screenshot to help you reflect on the transition."
        } else ""

        return """
            Goal: $goal
            
            Interactive Elements:
            $elementsText
            
            Action History:
            $historyText
            
            $historyHint
            What is the next action?
        """.trimIndent()
    }
}

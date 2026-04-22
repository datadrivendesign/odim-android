# Mobile Agent Architecture: Eyes, Brain, and Hands

The ODIM mobile agent operates in a continuous "Goal -> Think -> Act" loop. This process can be understood through the metaphor of **Eyes**, **Brain**, and **Hands**.

## 1. The Eyes: Perception (Capture)
The agent begins each step by perceiving the current state of the Android device.
*   **Screenshots (Dual-Vision):** It captures a full-resolution screenshot of the current screen. Crucially, it also retains the **previous screenshot** from the last step to provide visual context for the transition.
*   **View Hierarchy (VH):** It captures the underlying accessibility tree, providing structured data about every UI element (text, resource IDs, clickable status, etc.).
*   **VH Flattening:** To make this data digestible for the LLM, the raw JSON tree is "flattened" into a numbered list of interactive elements with their center coordinates.

## 2. The Brain: Reasoning (LLM)
Once the state is captured, it is sent to the "Brain" (Claude or Ollama) for processing.
*   **Transition Context:** The brain receives the user's **Goal**, the **Current Screenshot**, the **Previous Screenshot**, the **Interactive Element List**, and the **Action History**.
*   **Transition Reflection:** Before deciding on a new action, the agent performs a mandatory reflection step. It compares the previous screenshot to the current one to determine if its last action was efficacious (e.g., "Did the click actually open the menu?" or "Am I stuck on the same screen?").
*   **Decision:** Based on this reflection, it outputs a structured `AgentAction` (e.g., `click(index: 5)` or `scroll(direction: "down")`). This self-correction loop prevents the agent from repeating the same failed actions.

## 3. The Hands: Interaction (Execution)
Finally, the agent translates the decision into a physical interaction on the device.
*   **Coordinate-Based Actions:** Using the center coordinates mapped during the "Eyes" phase, the agent uses the `AccessibilityService` to dispatch gestures.
*   **Clicking:** Dispatches a precise touch gesture at the target (X, Y).
*   **Typing:** Identifies the focused input field and uses `ACTION_SET_TEXT` to input the requested string.
*   **Scrolling:** Dispatches a swipe gesture in the specified direction.
*   **Global Actions:** Handles system-level commands like "Back" and "Home".

---

## The Loop
After the "Hands" perform an action, the agent **waits for the UI to settle** (2.5 seconds) and then repeats the cycle. By seeing the visual consequences of its actions through the "Dual-Vision" pipeline, the agent can autonomously navigate complex cross-app workflows and recover from unexpected UI states.

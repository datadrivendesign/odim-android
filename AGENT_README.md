# ODIM Mobile Agent: Architecture & Setup

The ODIM mobile agent is an autonomous system that can navigate Android applications to achieve user-defined goals. It uses a **"Goal -> Think -> Act"** loop, powered by Multimodal Large Language Models (MLLMs).

---

## 🏗 Architecture Overview

The system follows the metaphor of **Eyes**, **Brain**, and **Hands**.

### 1. The Eyes: Perception (Capture)
*   **Role:** Perceives the current state of the device.
*   **Dual-Vision:** Captures the **Current Screenshot** and compares it with the **Previous Screenshot** to understand transitions.
*   **View Hierarchy:** Flattens the accessibility tree into a numbered list of interactive elements.
*   **Key Files:**
    - `MyAccessibilityService.kt`: Handles the raw capture of screenshots and view hierarchies.
    - `AgentUtils.kt`: Resizes images for the API and flattens the VH JSON into a text-based representation for the LLM.

### 2. The Brain: Reasoning (Think)
*   **Role:** Decides the next move based on goals and visual evidence.
*   **Reflection:** A mandatory step where the LLM evaluates the success of the previous action before proposing a new one. This prevents repetitive loops.
*   **Key Files:**
    - `AgentBrain.kt`: The interface defining how the agent "thinks".
    - `ClaudeClient.kt`: Implementation using Anthropic's Claude 3.5 Sonnet (Optimized for reasoning).
    - `OllamaClient.kt`: Implementation for self-hosted Vision-Language Models (e.g., Qwen2-VL).
    - `AgentPrompts.kt`: Contains the system instructions that enforce the "Reflection -> Reason -> Action" structure.

### 3. The Hands: Interaction (Act)
*   **Role:** Executes physical gestures on the Android OS.
*   **Precision:** Uses the coordinates mapped from the View Hierarchy to perform clicks, scrolls, and text entry.
*   - **Key Files:**
    - `MyAccessibilityService.kt`: Implements `executeAction()` which translates LLM decisions into `GestureDescription` strokes.
    - `AgentController.kt`: Orchestrates the loop, managing the sequence of thinking and acting.

---

## 🛠 Setup Instructions

### 1. Environment Configuration
Create or update `local.properties` in the project root. This file is git-ignored to protect your credentials.

```properties
# Claude API
CLAUDE_API_KEY=sk-ant-xxx...
CLAUDE_API_URL=https://api.anthropic.com/v1/messages

# Ollama / Private Server
OLLAMA_API_KEY=your_auth_token
OLLAMA_API_URL=https://your-server.com/api/chat
```

### 2. Enable Accessibility Service
The agent requires deep system integration to see and act:
1.  Build and install the app on a physical device or emulator (API 30+).
2.  Go to **Settings > Accessibility**.
3.  Find **ODIM Accessibility Service** and toggle it **ON**.
4.  (Optional) If using a private server (like the Ollama endpoint), ensure your device is connected to the relevant VPN.

### 3. Running a Task
1.  Open the **ODIM App**.
2.  Navigate to the **Agent** section from the main menu.
3.  Enter a high-level goal (e.g., *"Find a pair of running shoes under $100 in the Amazon app"*).
4.  Select your model (Claude or Ollama) and press **Start Agent**.
5.  The app will background itself and the agent will begin its loop. You will see a **pulsing green glow** around the screen edges while it is active.

---

## 🔄 The Execution Loop

1.  **Settle (2.5s):** Wait for animations to finish.
2.  **Perceive:** Capture Screenshots (Before/After) and View Hierarchy.
3.  **Think:** LLM reflects on the transition, reasons about the goal, and selects an element.
4.  **Act:** The Accessibility Service dispatches a gesture.
5.  **Repeat:** The loop continues for a maximum of 10 steps or until the goal status is "Success".

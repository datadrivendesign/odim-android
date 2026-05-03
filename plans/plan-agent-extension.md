#### Due:
Friday Afternoon April 17th (2 days)

#### Objective:
To extend the UI capture capability of the ODIM app to provide context for a mobile agent to leverage as context to conduct tasks. Specifically, by Friday, I need to create a proof of concept demo on the emulator, showing that a mobile agent can execute simple tasks on the emulator, provided the UI context, interaction history, and other relevant task information.

#### Relevant Code Repositories

- `/Users/carlguo/Research/ranjitha/odim-android` - Android app that leverages AccessibilityService to dynamically capture UI accessibility tree, screenshot, and gesture information on detecting a screen touch. **Primary repository.** [1]
- `/Users/carlguo/Research/ranjitha/android-world-rag-benchmark` - Dynamic GUI agent benchmark with Android emulator integration. Reference for agent architecture, action space design, and prompting strategy. [2]

#### Decision: On-Device Agent (Option A)

**Chosen approach:** The agent loop runs inside the ODIM Android app. ODIM captures UI state via AccessibilityService, sends it to a Claude API endpoint over HTTP, parses the response, and executes actions via AccessibilityService action APIs.

**Key simplification:** All actions use **coordinate-based execution** (`dispatchGesture()` at X,Y coordinates parsed from the VH JSON) rather than holding live `AccessibilityNodeInfo` references. This eliminates the stale-node risk that would otherwise make Option A too risky for a 2-day timeline.

**Why on-device over desktop Python:**
- Significantly more impressive as an "on-device agent" demo
- Uses ODIM's rich VH capture (multi-window, extras bundle, drawing_order)
- Self-contained — works on physical devices, no desktop script dependency
- Higher long-term value — becomes a real feature, not a throwaway POC

---

### Implementation Path

#### 1. The "Eyes": On-Demand State Capture
Refactor `CoreAccessibilityService.kt` to extract capture logic into a callable `suspend` function, decoupled from the touch listener.
- **Input:** None (reads `rootInActiveWindow` + `windows` directly).
- **Output:** `AgentState(screenshot: Bitmap, vh: JsonNode)`.
- **How:** Move the VH serialization + screenshot logic from `onTouchListener` into a reusable function. The touch listener calls it; the agent loop also calls it.
- **Estimate:** ~2 hrs (mostly refactoring existing code).

#### 2. The "Context": VH Flattening
Create a utility to convert the rich VH JSON tree into a prompt-friendly flat list.
- **Logic:** Depth-first traversal of the parsed VH JSON.
- **Filter:** Keep nodes where `clickable`, `scrollable`, or `isEditable` (class_name contains "EditText") is true.
- **Output per element:** Index number, `class_name`, `text` or `content_desc`, center coordinates (computed from `bounds_in_screen`), and flags.
- **Stored mapping:** `Map<Int, Point>` from index → center coordinates, used by the action executor.
- **Estimate:** ~2 hrs.

#### 3. The "Brain": OkHttp LLM Client
Hand-roll a minimal Claude API client using OkHttp (already a dependency).
- **Endpoint:** `POST https://api.anthropic.com/v1/messages`
- **Headers:** `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`
- **Payload:** `messages` array with system prompt (text) + user message containing screenshot (base64 JPEG image block) + element list + action history (text block).
- **Response parsing:** Extract `content[0].text`, parse `Reason:` and `Action:` fields.
- **API key storage:** `BuildConfig.CLAUDE_API_KEY` via `local.properties` (gitignored). Add `buildConfigField` to `app/build.gradle`.
- **Screenshot prep:** Resize to 540x1200, compress as JPEG quality 80, then base64 encode. Cuts payload ~80%.
- **Error handling:** Retry once on timeout (30s). Abort agent loop after 3 consecutive failures. Log raw response on parse failure.
- **Estimate:** ~3 hrs.

#### 4. The "Hands": Action Execution
Coordinate-based actions using AccessibilityService APIs. No live node references needed except for text input.

| Action | Implementation |
|--------|---------------|
| `click(index)` | Look up center coords from index→Point map. Build `GestureDescription` with single `StrokeDescription` at (x,y), duration 50ms. Call `dispatchGesture()`. |
| `type(text)` | `findFocus(FOCUS_INPUT)` to get the currently focused node (always fresh). Call `performAction(ACTION_SET_TEXT, Bundle("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text))`. Note: this *replaces* field content, does not append. |
| `scroll(direction)` | `dispatchGesture()` with swipe stroke. Default start point: screen center. Swipe distance: 1/3 screen height (vertical) or 1/3 screen width (horizontal). Duration: 300ms. |
| `navigate_back` | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| `navigate_home` | `performGlobalAction(GLOBAL_ACTION_HOME)` |
| `status(goal_status)` | Terminates the agent loop. No device action. |

- **Estimate:** ~1-2 hrs (coordinate-based approach is much simpler than node-based).

#### 5. The "Loop": Agent Controller
A coroutine-based loop running in the AccessibilityService.

```
function agent_loop(goal, max_steps=15):
    history = []
    for step in 1..max_steps:
        delay(2000)                       // let UI settle
        state = captureState()            // screenshot + VH (on-demand)
        elements = flattenVH(state.vh)    // interactive elements with indices
        prompt = formatPrompt(goal, elements, history)
        response = callLLM(prompt, state.screenshot)
        action = parseAction(response)

        if action.type == "status":
            log("Agent finished: ${action.goal_status}")
            return action.goal_status

        executeAction(action, elements)
        history.append(StepRecord(step, action, response.reason))
        log("Step $step: ${action.type} — ${response.reason}")

    return "max_steps_reached"
```

- **Trigger:** Floating overlay button or a new Activity with goal text field + "Start Agent" button.
- **Cancellation:** Stop button on the overlay, or max steps reached.
- **Estimate:** ~2 hrs.

---

### Action Space Definition

| Action | JSON Format | Example |
|--------|------------|---------|
| `click(index)` | `{"action_type": "click", "index": 5}` | Tap the 5th interactive element |
| `type(text)` | `{"action_type": "type", "text": "John Doe"}` | Type into focused field |
| `scroll(direction)` | `{"action_type": "scroll", "direction": "down"}` | Scroll down |
| `navigate_back` | `{"action_type": "navigate_back"}` | Press back |
| `navigate_home` | `{"action_type": "navigate_home"}` | Press home |
| `status(goal_status)` | `{"action_type": "status", "goal_status": "complete"}` | End the loop |

### UI Context Sent to LLM Per Step

1. **Screenshot** — base64 JPEG, resized to 540x1200, quality 80
2. **Interactive element list** — flat list filtered from VH:
   ```
   [1] Button "Start" (540, 1500) [clickable]
   [2] EditText "" (540, 800) [editable, focusable]
   [3] ImageView "Settings" (1007, 205) [clickable]
   ```
3. **Action history** — list of previous (step, action, reason) tuples

### Prompt Design

Single-stage action selection (skip M3A's two-stage summarization for the POC):

```
System: You are a mobile agent that can interact with an Android device.
You are given a goal, a screenshot of the current screen, and a numbered
list of interactive UI elements. Decide the next action to take.

Available actions:
- click(index): tap the element at the given index number
- type(text): type text into the currently focused input field
- scroll(direction): scroll "up", "down", "left", or "right"
- navigate_back: press the back button
- navigate_home: press the home button
- status(goal_status): report "complete" when the goal is done,
  or "infeasible" if the goal cannot be accomplished

Respond in exactly this format:
Reason: <one sentence explaining your reasoning>
Action: <JSON object>

User: [goal + element list + action history + screenshot image]
```

### Demo Tasks

1. "Open the Clock app and start the stopwatch"
2. "Open Settings app and turn on 'dark theme' setting"
3. "Open Contacts app and create a new contact named 'John Doe'"

---

### Logging

#### Core Logging (Required)

Structured logcat output after each agent step:

```kotlin
Log.i("AGENT_STEP", """{"step":$step,"action":"${action.type}","reason":"$reason","latency_ms":$latency}""")
```

Plus a JSON-lines log file written to device storage per agent run:

```json
{"step": 1, "timestamp": "...", "goal": "...", "elements_count": 12, "action": {"action_type": "click", "index": 3}, "reason": "Tapping the Clock app icon", "llm_latency_ms": 1850}
{"step": 2, ...}
```

Save screenshots per step alongside the log: `agent_run_<timestamp>/step_001.jpg`, `step_002.jpg`, etc.

Pull and review with:
```bash
adb pull /sdcard/Android/data/edu.illinois.odim/files/agent_logs/ ./logs/
```

#### Stretch Goal: Rich External Dashboard

**Tier 1 — Static HTML Viewer (~1 hr extra):**
A single-file `viewer.html` that reads the pulled JSON log + step screenshots and renders a step-by-step timeline. Open locally in a browser after `adb pull`. No server needed.

**Tier 2 — Local HTTP Server on Device (~2-3 hrs extra):**
Embed a lightweight HTTP server (NanoHTTPD or raw `ServerSocket`) in the ODIM app:
```
http://<device-ip>:8080/agent/status   → current step + running/stopped
http://<device-ip>:8080/agent/steps    → full step history as JSON
http://<device-ip>:8080/agent/step/3   → step 3 details + screenshot
```
Open on a laptop browser while the agent runs. Refresh to see updates. Requires same WiFi network.

**Tier 3 — WebSocket Live Dashboard (~3-4 hrs extra):**
Same as Tier 2 but push-based via WebSocket. Browser auto-updates as each step completes. Most impressive for a live demo — show the agent on the emulator while the dashboard updates beside it.

**Tier 4 — ADB Logcat Streaming (~1-2 hrs extra):**
Stream structured logcat on the laptop and render in a terminal UI or live HTML:
```bash
adb logcat -s AGENT_STEP | python agent_dashboard.py
```
Zero new Android dependencies. Screenshots require separate `adb pull` per step (adds latency).

**Recommendation:** Tier 1 (static HTML viewer) is achievable within the deadline if core work finishes early. Tier 2+ is post-deadline polish.

---

### Implementation Steps

1. **Set up API key** — add `CLAUDE_API_KEY` to `local.properties` and `buildConfigField` in `build.gradle`
2. **Build on-demand capture** — refactor `CoreAccessibilityService` touch-triggered capture into a callable suspend function
3. **Build VH flattener** — parse VH JSON → flat interactive element list with index→coordinate mapping
4. **Build action executor** — `dispatchGesture()` for click/scroll, `performAction` for type, `performGlobalAction` for back/home
5. **Build LLM client** — OkHttp POST to Claude API with multimodal payload, response parsing
6. **Build prompt template** — system prompt + user prompt formatting function
7. **Build agent loop** — coroutine loop with capture → prompt → LLM → parse → execute → log
8. **Build agent trigger UI** — floating overlay button or new Activity with goal input + start/stop
9. **Test on demo tasks** — run 3 demo tasks, iterate on prompt and action timing
10. **Add logging** — JSON-lines log file + step screenshots to device storage

---

### Backup: Option B (Desktop Python Script)

*Kept as fallback if Option A hits a blocking issue (e.g., `dispatchGesture` fails on the emulator).*

A Python script on the host machine connects to the emulator via ADB. Captures state via `adb exec-out screencap` + `adb shell uiautomator dump`, sends to Claude via Anthropic Python SDK, executes actions via `adb shell input`. ~5-8 hours total. Lower risk but less impressive demo and no ODIM integration.

Key commands:
```bash
adb exec-out screencap -p > screenshot.png          # capture screenshot
adb shell uiautomator dump /sdcard/vh.xml           # capture VH
adb shell input tap <x> <y>                          # tap
adb shell input text "<text>"                        # type
adb shell input swipe <x1> <y1> <x2> <y2> <ms>     # scroll
adb shell input keyevent KEYCODE_BACK               # back
adb shell input keyevent KEYCODE_HOME               # home
```

---

#### References

[1] "On-Device Interaction Mining", Arsan & Guo et al., https://openreview.net/pdf?id=o583Zvf84T

[2] "AndroidWorld: A Dynamic Benchmarking Environment for Autonomous Agents", Rawles et al. https://arxiv.org/pdf/2405.14573

# ODIM Kotlin Port — Bridge-Server Handoff

**Owner of this work:** Gemini (Android specialist)
**Coordinator:** Carl Guo (@carlguo2)
**Repo:** `~/Research/ranjitha/dcc` (branch `carl-android-adapter`)
**Module under work:** `adapters/android/odim-android/`
**Read-first:** `adapters/android/odim-android/README.md`, `adapters/android/odim-android/AGENT_README.md`, `plans/android-adapter-port.md`

---

## 1. What you're doing and why

The existing `odim-android` Kotlin app is two things in one:

1. A **passive interaction recorder** — an AccessibilityService that captures (screenshot + view-hierarchy JSON + gesture) on every relevant touch and persists each into `files/TRACES/<pkg>/<trace>/<event>/`. This is the published-paper artifact (`adapters/android/odim-android/README.md:43-51`, citation at `:248-261`). **Do not break this.**
2. An **on-device agent loop** — `AgentController.kt` orchestrates a Reflection→Reason→Action loop, calling `ClaudeClient` or `OllamaClient` for the brain and `MyAccessibilityService` for the hands (`AGENT_README.md:9-34`).

The new DCC engine moves orchestration and brain to host-side TypeScript. So your job is:

- **Delete** the on-device agent loop (controller + brain clients + prompts + agent UI activity).
- **Add** a small embedded HTTP server inside the existing app that exposes the AccessibilityService's capture and dispatch primitives to a host process talking over `adb forward tcp:8765 tcp:8765`.
- **Modify** `MyAccessibilityService` minimally — promote the capture/dispatch helpers to be reachable from the bridge, manage the bridge server's lifecycle from `onServiceConnected()` / `onDestroy()`, and remove the agent-loop coupling.

Net diff should be **negative LoC** on the Android side. You are removing more than you are adding.

The host-side TS adapter (`adapters/android/src/`) is being written in parallel by Carl. You don't need to touch any TS. The two sides communicate only over the HTTP wire format in §6.

---

## 2. Decisions already made (do not re-litigate)

| Decision | Choice | Rationale |
|---|---|---|
| Transport | HTTP/JSON over `adb forward` | See `plans/android-adapter-port.md` §2 |
| Packaging | **Single APK** — bridge lives in the existing `edu.illinois.odim` app | One AccessibilityService permission surface; bridge auto-stops when service is off |
| Bind address | `127.0.0.1` (loopback only) by default | Bridge is unauthenticated; never bind `0.0.0.0` on a phone that may be on hostile Wi-Fi |
| POC override | A `BIND_ALL=true` Gradle property flips to `0.0.0.0` | For local testing only; never ship a release build with this on |
| `type` action semantics | Keep existing `executeAction` behavior (whatever ODIM does today) | Translation to DCC's `Action.type` happens host-side in `dispatch.ts` |
| Server lib | **NanoHTTPD** (`org.nanohttpd:nanohttpd:2.3.1`) | Single ~70 KB jar, no transitive deps, dead simple. Ktor is overkill for 3 endpoints |
| Port | `8765` (loopback) | Fixed for v0; can be a `BuildConfig` field in v1 if needed |

If you have a strong reason to deviate, raise it in the kickoff issue thread before changing — Carl's TS side is built against these.

---

## 3. What to PRESERVE (do not touch unless explicitly noted)

These are the passive-capture surface. Touching them risks regressing the published paper.

**Activities — keep all except `AgentActivity`:**
- `activities/AppActivity.kt`
- `activities/CaptureActivity.kt`
- `activities/ScannerActivity.kt`
- `activities/TraceActivity.kt`
- `activities/EventActivity.kt`
- `activities/ScreenShotActivity.kt`
- `activities/IncompleteScreenActivity.kt`

**Adapters / Fragments — keep all:**
- `adapters/AppAdapter.kt`, `EventAdapter.kt`, `TraceAdapter.kt`, `VHAdapter.kt`
- `fragments/IncompleteScreenCanvasOverlay.kt`, `MovableFloatingActionButton.kt`, `ScrubbingScreenshotOverlay.kt`

**Dataclasses — keep all except `AgentModels.kt`:**
- `dataclasses/AppItem.kt`, `CaptureData.kt`, `CaptureStore.kt`, `Gesture.kt`, `GestureCandidate.kt`, `Redaction.kt`, `ScreenShotPreview.kt`, `TraceItem.kt`, `VHItem.kt`

**Utils — keep `LocalStorageOps.kt`, `ScreenDimensionsOps.kt`, `UploadDataOps.kt`. `AgentUtils.kt` is a special case — see §4.**

**Inside `MyAccessibilityService` (the file is `CoreAccessibilityService.kt` despite the filename mismatch):**
- The entire `onAccessibilityEvent(event)` flow (line ~500) that fires on user touches and persists captures via `LocalStorageOps`. Do **not** change which event types it observes; do **not** change the persistence side effects. Passive capture is preserved by **not touching this code**, only by removing the `agentController.isRunning.value` gating checks (see §5).
- `captureScreenshot()` (lines ~237–253) — uses `takeScreenshot(DEFAULT_DISPLAY, mainExecutor, TakeScreenshotCallback)` with `ScreenshotResult.hardwareBuffer`. Bridge will call the same code path; just promote visibility (§5).
- `captureVH()` (lines ~255–298) — manual `AccessibilityNodeInfo` traversal serialized via Jackson `JsonGenerator`. **Do not change the JSON shape.** Host-side normalization to DCC's `ActionableElement` happens in TS; if you change the shape, you break Carl's observer.
- The gesture dispatch helpers `performClick(...)` (around line 122–125) and `performScroll(...)` (around line 185–188) — both build a `GestureDescription` via `GestureDescription.Builder` + `StrokeDescription` and call `dispatchGesture(gesture, callback)`. Promote visibility (§5); behavior unchanged.

**Manifest — keep:**
- `<service android:name=".MyAccessibilityService">` block (`AndroidManifest.xml:24-35`) and its `BIND_ACCESSIBILITY_SERVICE` permission.
- `<uses-permission android:name="android.permission.INTERNET" />` and `ACCESS_NETWORK_STATE` — needed for the existing upload flow (`UploadDataOps`). The bridge's loopback HTTP also relies on `INTERNET`.

**Resources — keep:**
- `app/src/main/res/xml/accessibility_service_config.xml` — `canTakeScreenshot="true"` and `canPerformGestures="true"` are mandatory for the bridge to work.

---

## 4. What to DELETE

Confirmed safe-to-delete files (grep audit done by Carl; full referrer list in `plans/android-adapter-port.md` §sources):

- `app/src/main/java/edu/illinois/odim/AgentController.kt`
- `app/src/main/java/edu/illinois/odim/network/AgentBrain.kt`
- `app/src/main/java/edu/illinois/odim/network/AgentPrompts.kt`
- `app/src/main/java/edu/illinois/odim/network/ClaudeClient.kt`
- `app/src/main/java/edu/illinois/odim/network/OllamaClient.kt` *(also resolves the `qwen3-vl:32b` deprecation at line 20 — see `docs/kickoff.md:68`)*
- `app/src/main/java/edu/illinois/odim/activities/AgentActivity.kt`
- `app/src/main/java/edu/illinois/odim/dataclasses/AgentModels.kt` *(holds `AgentResponse` etc. used only by the deleted clients — verify with one grep before removing)*

**Special case — `app/src/main/java/edu/illinois/odim/utils/AgentUtils.kt`:**
This file holds (a) bitmap-resize-for-LLM helpers used only by the deleted brain clients, but also (b) a shared OkHttp client that may be referenced by `UploadDataOps`. **Check before deletion.** Run:

    grep -rn "AgentUtils" app/src/main/java/

If only the deleted files reference it, delete the whole file. Otherwise, delete only the LLM-specific helpers and leave the shared client in place.

**Manifest:** remove the `<activity android:name=".activities.AgentActivity">` declaration (look in `AndroidManifest.xml:37-54`). Leave every other activity intact.

**`app/build.gradle`:** delete the `BuildConfig` field declarations for the brain credentials. They live around lines 39–43 and 49–52 (debug/release blocks). The strings to remove:
- `CLAUDE_API_KEY`
- `CLAUDE_API_URL`
- `OLLAMA_API_KEY`
- `OLLAMA_API_URL`

Keep `API_URL_PREFIX` — the upload flow still needs it.

**`local.properties`:** the `CLAUDE_*` and `OLLAMA_*` keys are no longer read; document removal in the bridge README. (Don't worry about whether the user removes them locally — they just become inert.)

---

## 5. Surgical changes inside `CoreAccessibilityService.kt`

**Visibility promotions** (so `BridgeRouter` can call the existing helpers):
- `captureScreenshot()`, `captureVH()`, `performClick(...)`, `performScroll(...)`, and any `executeAction(...)` method should change from `private`/file-private to `internal`. Do not change their signatures or behavior.

**Removals:**
- Line ~76: delete `val agentController = AgentController(this)`.
- Lines ~355 and ~502: delete the `agentController.isRunning.value` guards. After this change, the `onAccessibilityEvent` handler captures touches unconditionally — which is the original passive-recorder behavior, restored.

**Additions — bridge lifecycle:**
- Override `onServiceConnected()` (or augment the existing override): instantiate and `start()` a `BridgeServer`. Keep a `private var bridgeServer: BridgeServer? = null` field so `onDestroy()` can stop it.
- Override `onDestroy()`: call `bridgeServer?.stop()` and null it out. This guarantees the server's lifetime exactly matches the AccessibilityService's lifetime — when the user disables the service, the bridge dies with it. That is the right safety property.

**Additions — settle signal (see §8):**
- In `onAccessibilityEvent(event)`, after the existing capture logic, also call `SettleSignal.notifyEvent(event.eventType)` so the bridge can observe quiet windows. `SettleSignal` is a small object in the new `bridge/SettleSignal.kt` (§8).

That's it for `CoreAccessibilityService.kt`. ~5–10 lines added, ~5 lines deleted, no behavior change to passive capture.

---

## 6. Bridge HTTP protocol (the contract)

Tiny HTTP/JSON. NanoHTTPD on `127.0.0.1:8765`. No auth (loopback only). All responses are `Content-Type: application/json` unless noted.

### 6.1 `GET /health`

Liveness probe used by the host between actions to detect process death (§9).

Response 200:

    { "ok": true, "service": "odim-bridge", "version": "0.1.0", "pid": 12345 }

Returns immediately; no AccessibilityService calls.

### 6.2 `GET /observe`

Captures a single frame.

Response 200:

    {
      "screenshotPng": "<base64>",
      "viewport": { "width": 1080, "height": 2400 },
      "vh": { /* raw VH JSON from captureVH() — DO NOT change shape */ },
      "package": "com.example.app",
      "window": "com.example.app/.MainActivity",
      "capturedAt": "2026-04-28T15:00:00Z"
    }

Implementation: `suspendCancellableCoroutine` wrap of `service.captureScreenshot()` (the callback gives you the bitmap), encode to PNG bytes, base64 the bytes, run `service.captureVH()` for the JSON, assemble.

**Do not normalize the VH** to a flat element list. That happens host-side in TS so we can iterate the schema without rebuilding the APK.

### 6.3 `POST /dispatch`

Single endpoint, discriminated by `kind`:

    { "kind": "tap",    "x": 540, "y": 1200 }
    { "kind": "scroll", "direction": "down" | "up" | "left" | "right", "fromX"?: 540, "fromY"?: 1200 }
    { "kind": "type",   "text": "hello world" }
    { "kind": "key",    "keycode": 4 }
    { "kind": "back" }
    { "kind": "home" }

Coordinates are **absolute pixels** (host already resolves `target.by === "index"` against its `pointsByIndex` side-table before posting). Do not normalize, do not flip y, do not scale.

Response 200 — happy path:

    { "success": true }

Response 200 — handled failure (gesture dispatched but did not complete):

    { "success": false, "error": "GestureResultCallback.onCancelled" }

Use HTTP 200 even for `success: false`; reserve 4xx/5xx for malformed requests / bridge bugs.

Mappings:
- `tap` → `performClick(x, y)` (existing helper).
- `scroll` → `performScroll(direction, fromX?, fromY?)` (existing helper; add a default-center variant if not present).
- `type` → existing ODIM `executeAction` `type` codepath. **Whatever it does today, keep it** (per §2 decision). If the focused editable changes, the host's next `/observe` will see it.
- `key` → `performGlobalAction(keycode)` for global keys; `dispatchKeyEvent` per-char for arbitrary keys. Match existing `executeAction` if there's already a path.
- `back` → `service.performGlobalAction(GLOBAL_ACTION_BACK)`.
- `home` → `service.performGlobalAction(GLOBAL_ACTION_HOME)`.

### 6.4 `POST /reset`

Request — both fields optional:

    { "package": "com.example.app" }

Response:

    { "success": true }

Implementation: `performGlobalAction(GLOBAL_ACTION_HOME)`. If `package` is present, also `am start`-equivalent (`Intent` with `LAUNCHER` category for the package's main activity, fired via `service.startActivity(intent)` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`).

### 6.5 `POST /settle` (optional but recommended — see §8)

Request:

    { "quietWindowMs": 250, "maxWaitMs": 5000 }

Response:

    { "settled": true,  "elapsedMs": 312 }
    { "settled": false, "elapsedMs": 5000 }

Implements the event-debounced settle described in §8. Returns when no `TYPE_WINDOW_CONTENT_CHANGED` (and `TYPE_WINDOW_STATE_CHANGED`) event has fired for `quietWindowMs`, or `maxWaitMs` has elapsed.

If implementing this is too much for the sprint window, omit it — host can fall back to polling `/observe` and comparing two consecutive VH dumps. Document which you implemented.

---

## 7. Concurrency / threading

NanoHTTPD spawns a worker thread per request. AccessibilityService's `takeScreenshot` and `dispatchGesture` are both **callback-based async APIs that need a `Handler` or `Executor`**. The standard pattern:

```kotlin
suspend fun captureScreenshotSuspending(): Bitmap = suspendCancellableCoroutine { cont ->
    takeScreenshot(
        Display.DEFAULT_DISPLAY,
        appContext.mainExecutor,
        object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?: return cont.resumeWithException(IllegalStateException("null bitmap"))
                cont.resume(bitmap)
            }
            override fun onFailure(errorCode: Int) {
                cont.resumeWithException(RuntimeException("takeScreenshot failed: $errorCode"))
            }
        }
    )
}
```

In the NanoHTTPD route handler (which runs on a worker thread, not the main thread), bridge to coroutines with `runBlocking`:

```kotlin
override fun serve(session: IHTTPSession): Response {
    return when {
        session.method == Method.GET && session.uri == "/observe" -> runBlocking {
            handleObserve(session)
        }
        // ...
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"not found"}""")
    }
}
```

`runBlocking` on a worker thread is fine — you're not blocking the main thread or the AccessibilityService callbacks. The `suspendCancellableCoroutine` wrappers will resume on the executor you pass to the AccessibilityService API (`mainExecutor` is correct for screenshot; gesture dispatch is documented as needing the main thread for `dispatchGesture`).

**Do not** call AccessibilityService methods directly from a NanoHTTPD worker thread without going through the suspend wrapper — `dispatchGesture` requires the main looper.

---

## 8. Settle endpoint — event-driven brainstorm

Current ODIM agent loop uses a fixed `delay(2500)` (`AGENT_README.md:71`); the prior plan proposed shrinking to ~250 ms. Both are fixed waits. We can do better with the AccessibilityService event stream.

**Idea: event-debounced settle.**

The AccessibilityService already receives `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_WINDOW_STATE_CHANGED` for every UI mutation. Maintain a `SettleSignal` object that:

1. Records the wall-clock timestamp every time `onAccessibilityEvent` fires for those event types.
2. Exposes `suspend fun awaitQuiet(quietWindowMs: Long, maxWaitMs: Long): SettleResult` that returns once `now() - lastEventTimestamp >= quietWindowMs`, or `maxWaitMs` elapses.

```kotlin
object SettleSignal {
    @Volatile private var lastEventAtMs: Long = System.currentTimeMillis()

    fun notifyEvent(eventType: Int) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEventAtMs = System.currentTimeMillis()
        }
    }

    suspend fun awaitQuiet(quietWindowMs: Long, maxWaitMs: Long): SettleResult {
        val start = System.currentTimeMillis()
        val deadline = start + maxWaitMs
        while (true) {
            val now = System.currentTimeMillis()
            val sinceLast = now - lastEventAtMs
            if (sinceLast >= quietWindowMs) return SettleResult(true, now - start)
            if (now >= deadline)            return SettleResult(false, maxWaitMs)
            delay(minOf(quietWindowMs - sinceLast, deadline - now).coerceAtLeast(10L))
        }
    }
}

data class SettleResult(val settled: Boolean, val elapsedMs: Long)
```

**Why this is better than a fixed delay:**
- Returns as soon as the UI quiets, often <300 ms instead of 2.5 s.
- Caps at `maxWaitMs` so a chatty UI (animated splash, ad refresh) doesn't hang the loop.
- Uses the same event stream the AccessibilityService is already listening to — no extra system load.

**Why it's still a heuristic:**
- A UI can fire events without visually changing (background re-layouts) and conversely settle visually without firing events (Compose pre-`usesAccessibilityTraversal`, hardware-accelerated animations on a separate render thread). Mitigation: the orchestrator's reflection step catches stale-frame actions (`docs/architecture.md:80`).
- **Recommendation:** implement `/settle` if time permits. If not, ship without it — host falls back to polling `/observe` and structural-equal-VH check. Both approaches converge on roughly the same correctness; the event-driven version is just faster on average.

---

## 9. Process-death notification

When the AccessibilityService dies (user disabled it in Settings, OS killed the process, app crashed), the host needs to know.

**Two-layer approach:**

1. **Implicit (free):** when `MyAccessibilityService.onDestroy()` fires, it calls `bridgeServer?.stop()`. The next host-side `fetch('http://127.0.0.1:8765/...')` returns `ECONNREFUSED`. The host adapter surfaces this to the orchestrator as a recoverable error: "Android bridge unreachable — re-enable ODIM Accessibility Service."

2. **Explicit (better UX):** the host polls `GET /health` between steps. If `/health` returns 200, all good; if it errors, surface the same message. This catches process death faster than waiting for the next `/observe`.

**Optional v1.1:** an SSE or long-polled `/events` channel that emits `{type: "service_destroyed"}` on `onDestroy()`. Not needed for the sprint — the implicit layer is sufficient.

For the v0 sprint, **just make sure `bridgeServer.stop()` runs in `onDestroy()`** so the implicit detection works. The host side will handle the surface.

---

## 10. Screenshot serialization — b64 vs raw binary

Default for v0: **base64 PNG inside the `/observe` JSON response.** Simpler client code, single round-trip, matches the contract in §6.2.

**Cost:** PNG-encoding a 1080×2400 frame is ~50–150 ms; base64 inflates payload ~33% and adds CPU on both sides. Over `adb forward` (USB 2.0+) the bytes are cheap; the encode is the dominant cost.

**Optimization path if observe latency becomes a bottleneck:**

Split into two endpoints:

- `GET /observe/state` → JSON with `vh`, `viewport`, `package`, `window`, `capturedAt` (no screenshot).
- `GET /observe/screenshot` → `Content-Type: image/png`, raw PNG bytes in body.

Saves the b64 cost (~33% bytes, ~30–50 ms CPU). Costs an extra round-trip but `adb forward` round-trip is sub-ms over USB.

**Decision rule:** ship `/observe` as one endpoint with b64. If we measure observe latency >500 ms median against an emulator, switch to the split-endpoint variant. Don't pre-optimize.

---

## 11. Build configuration

`app/build.gradle`:

**Add dependency:**

```groovy
implementation "org.nanohttpd:nanohttpd:2.3.1"
```

**Optional — bind-all toggle** (POC testing only):

```groovy
buildConfigField "boolean", "BRIDGE_BIND_ALL",
    project.findProperty("BRIDGE_BIND_ALL") ?: "false"
```

Then in `BridgeServer`:

```kotlin
val host = if (BuildConfig.BRIDGE_BIND_ALL) "0.0.0.0" else "127.0.0.1"
```

Build with `./gradlew assembleDebug -PBRIDGE_BIND_ALL=true` only when intentionally testing over the network. **Never** in release builds.

**Remove BuildConfig fields** (no longer used after the deletes):
- `CLAUDE_API_KEY`, `CLAUDE_API_URL`, `OLLAMA_API_KEY`, `OLLAMA_API_URL`

Keep `API_URL_PREFIX` (upload flow still uses it).

`AndroidManifest.xml`:

- Remove the `<activity android:name=".activities.AgentActivity" ... />` block.
- No new permissions needed. Loopback HTTP works under existing `INTERNET`.
- No new `<service>` declarations — the bridge is a plain class started from `MyAccessibilityService.onServiceConnected()`, not its own service.

`local.properties`:

- The `CLAUDE_*` and `OLLAMA_*` keys documented in `AGENT_README.md:42-50` are no longer read. Note this in the new `adapters/android/odim-android/AGENT_README.md` rewrite (or delete `AGENT_README.md` entirely — it's about the deleted agent loop).

---

## 12. New files to add

### `app/src/main/java/edu/illinois/odim/bridge/BridgeServer.kt`
- Subclass of `NanoHTTPD`.
- Constructor: `BridgeServer(private val service: MyAccessibilityService, host: String, port: Int)`.
- `start()`: `super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)` (daemon thread = false so the bridge survives the JVM finalization quirks; or `true` if you confirm AccessibilityService keeps it alive).
- `serve(session)`: dispatches to `BridgeRouter` based on method + URI.

### `app/src/main/java/edu/illinois/odim/bridge/BridgeRouter.kt`
- Pure routing + JSON parsing. Holds a reference to `MyAccessibilityService`.
- One handler function per endpoint (`handleHealth`, `handleObserve`, `handleDispatch`, `handleReset`, `handleSettle?`).
- Use Jackson (already in deps at `jackson-module-kotlin:2.16.1`) for JSON. Don't add a second JSON lib.

### `app/src/main/java/edu/illinois/odim/bridge/SettleSignal.kt` (if implementing §8)
- Object holding the last-event timestamp + the `awaitQuiet` suspend function.
- Called from `MyAccessibilityService.onAccessibilityEvent(event)`.

### `app/src/main/java/edu/illinois/odim/bridge/SuspendingAccessibility.kt` (recommended)
- Extension functions on `MyAccessibilityService` that wrap the callback APIs in `suspendCancellableCoroutine` (see §7 sketch).
- `suspend fun captureScreenshotSuspending(): Bitmap`
- `suspend fun dispatchGestureSuspending(gesture: GestureDescription): Boolean`

Keeping these in a separate file makes the routing code clean and the suspend wrappers individually unit-testable.

---

## 13. Verification (manual)

There is no on-device CI in v0. Verify against an emulator before handing back:

1. Build the APK: `./gradlew :app:assembleDebug` from `adapters/android/odim-android/`.
2. Install on an emulator (API 30+).
3. Settings → Accessibility → enable **ODIM Accessibility Service**.
4. From the host, run `adb forward tcp:8765 tcp:8765`.
5. Sanity-check each endpoint with `curl`:
   - `curl http://127.0.0.1:8765/health` → `{"ok": true, ...}`
   - `curl http://127.0.0.1:8765/observe | jq '.viewport'` → `{"width": ..., "height": ...}`
   - `curl -X POST http://127.0.0.1:8765/dispatch -d '{"kind":"home"}' -H 'Content-Type: application/json'` → `{"success": true}` and observe the emulator returns to launcher.
   - `curl -X POST http://127.0.0.1:8765/dispatch -d '{"kind":"tap","x":540,"y":1200}' -H 'Content-Type: application/json'` → tap fires.
6. Confirm passive capture still works: open an installed third-party app, tap around, check `files/TRACES/<pkg>/...` populates as before.
7. Disable the AccessibilityService in Settings; confirm `curl /health` returns connection-refused within ~1 s.

If all 7 pass, hand back to Carl. Carl's TS adapter will exercise the same endpoints from `AndroidDriver`.

---

## 14. Sources / cross-references

- `plans/android-adapter-port.md` — the parent plan; this doc is the Kotlin slice of it.
- `adapters/android/odim-android/README.md` — the published-paper artifact you're preserving.
- `adapters/android/odim-android/AGENT_README.md` — the Eyes/Brain/Hands description you're removing on-device.
- `docs/kickoff.md:64-71` — Carl's sprint scope.
- `docs/kickoff.md:68` — the `qwen3-vl:32b` deprecation note (resolved by deleting `OllamaClient.kt`).
- `docs/architecture.md:76-90` — keep / revise / drop list for the Android source.
- `docs/architecture.md:80` — orchestrator's reflection step catches stale-frame actions (relied on in §8).
- `docs/porting-guide.md:48` — "raw `rawTreeBlob` — don't normalize" (relied on in §6.2).
- Android `AccessibilityService` reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android `GestureDescription`: https://developer.android.com/reference/android/accessibilityservice/GestureDescription
- NanoHTTPD: https://github.com/NanoHttpd/nanohttpd

---

## Appendix A — File-by-file change matrix

| Path | Action |
|---|---|
| `app/src/main/java/edu/illinois/odim/AgentController.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/network/AgentBrain.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/network/AgentPrompts.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/network/ClaudeClient.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/network/OllamaClient.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/activities/AgentActivity.kt` | DELETE |
| `app/src/main/java/edu/illinois/odim/dataclasses/AgentModels.kt` | DELETE (verify with grep first) |
| `app/src/main/java/edu/illinois/odim/utils/AgentUtils.kt` | CONDITIONAL DELETE (see §4) |
| `app/src/main/java/edu/illinois/odim/CoreAccessibilityService.kt` | MODIFY (§5) |
| `app/build.gradle` | MODIFY (§11) |
| `app/src/main/AndroidManifest.xml` | MODIFY — remove AgentActivity entry |
| `app/src/main/java/edu/illinois/odim/bridge/BridgeServer.kt` | NEW (§12) |
| `app/src/main/java/edu/illinois/odim/bridge/BridgeRouter.kt` | NEW (§12) |
| `app/src/main/java/edu/illinois/odim/bridge/SuspendingAccessibility.kt` | NEW (§12) |
| `app/src/main/java/edu/illinois/odim/bridge/SettleSignal.kt` | NEW if implementing §8 |
| `adapters/android/odim-android/AGENT_README.md` | DELETE or rewrite to describe the bridge |

Everything not listed above: **do not touch.**

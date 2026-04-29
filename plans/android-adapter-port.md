# Android Adapter Port — Plan

**Owner:** Carl Guo (@carlguo2)
**Sprint window:** Mon 2026-04-27 → Wed 2026-04-29 (`docs/kickoff.md:64`)
**Today:** 2026-04-28 (Tuesday — middle of the sprint)
**Branch:** `carl-android-adapter`

---

## 1. Context

The DCC engine is a TypeScript monorepo (`pnpm-workspace.yaml`, `core/`, `adapters/web/`). The `Driver` contract that adapters implement lives in TS at `core/src/interfaces/driver.ts:22-33`:

```ts
export interface Driver {
  readonly platform: Frame["platform"];
  observe(opts?: DriverOptions): Promise<Frame>;
  dispatch(action: Action, frame: Frame, opts?: DriverOptions): Promise<DispatchResult>;
  settle(opts?: DriverOptions): Promise<void>;
  reset(opts?: DriverOptions): Promise<void>;
}
```

The orchestrator that calls this interface is also TS (`core/src/orchestrator/`). `"android"` is already in the `Platform` union (`core/src/types/frame.ts:3`), so no core change is required to land the adapter.

The existing Android agent we are porting from is a Kotlin Android Studio project (`adapters/android/odim-android/`, untracked per `git status`). It has:

- `CoreAccessibilityService.kt` — AccessibilityService that captures screenshots + view hierarchy, dispatches `GestureDescription` strokes (`adapters/android/odim-android/AGENT_README.md:14-33`).
- `AgentController.kt` — orchestrates the Eyes/Brain/Hands loop on-device.
- `network/AgentBrain.kt`, `network/ClaudeClient.kt`, `network/OllamaClient.kt`, `network/AgentPrompts.kt` — brain implementations and prompt library.
- All other capture/curation/upload UI (the published ODIM paper artifact, `adapters/android/odim-android/README.md:244-260`).

Kickoff agreement on what to keep / drop / fix (`docs/architecture.md:76-90`, `docs/kickoff.md:65-71`):

- **Keep** the Eyes/Brain/Hands skeleton, mandatory Reflection in prompts, indexed `ActionableElement` representation, passive-capture coexistence.
- **Revise** `MAX_STEPS = 10` and `delay(2500)` settle (orchestrator owns budgets and event-driven settle).
- **Drop** hardcoded `MODEL_NAME = "claude-sonnet-4-6"` in `ClaudeClient.kt:22` — config now.
- **Fix bug** `OllamaClient.kt:22` uses `qwen3-vl:32b`; the `qwen3-vl` lineage is deprecated per `~/.claude/model-playbook.md` and `docs/kickoff.md:68`. Migrate to `qwen3.5:*`.

---

## 2. The architectural question

The `Driver` is a TS class. The Kotlin AccessibilityService runs on-device (the only place from which screenshot/VH/gesture APIs are reachable on stock Android). So the adapter is *not* a Kotlin → Kotlin lift; it is a host-side TS adapter that talks to an on-device agent over some transport.

Three viable transports:

| Option | How it works | Pros | Cons |
|---|---|---|---|
| **A. On-device HTTP bridge** | Strip the agent loop from `odim-android`, keep the AccessibilityService, add a tiny `NanoHTTPD`/`Ktor` server in the same app exposing `/observe`, `/dispatch`, `/reset`. Host TS adapter talks to it via `adb forward tcp:PORT tcp:PORT`. | Reuses ODIM's mature capture infra (rich VH, screenshot pipeline). Preserves passive capture (kickoff hard requirement). Fast (~50–200 ms/observe over USB). | ~150 LoC of Kotlin to add. |
| **B. Pure ADB shell-out** | Host TS shells `adb exec-out screencap -p`, `adb exec-out uiautomator dump /dev/tty`, `adb shell input tap X Y`. Zero on-device app needed. | No on-device code change. Works on any Android. | `uiautomator dump` is slow (1–2 s per call), gives a less rich VH than AccessibilityService, and is fundamentally separate from the ODIM AccessibilityService — passive capture and agent capture would be from different sources, breaking the "shared service" invariant in `docs/architecture.md:84`. |
| **C. Appium UiAutomator2 driver** | Drive Appium server from TS via the WebDriver protocol; Appium handles screenshot/VH/gesture. | Off-the-shelf, well-documented, server-side battle-tested. | Heavyweight (Java + Appium server install), still doesn't share the AccessibilityService with passive capture, brings a second action vocabulary we'd have to translate. |

**Recommendation: Option A (on-device HTTP bridge).** It is the only option that preserves passive capture inside the same app (the kickoff non-negotiable) and the only one that reuses the AccessibilityService capture pipeline that ODIM has been hardening for the published paper. The marginal Kotlin work is small and the steady-state observe latency stays well under the orchestrator's per-step budget.

The one piece of friction worth confirming with Jerry before we commit: `docs/kickoff.md:72` says "the Kotlin → TS translation is mechanical" — phrasing that could mean "rewrite everything in TS, talk over ADB only." Worth a 2-line check in the kickoff issue thread before writing the bridge.

---

## 3. Proposed file layout

Mirror `adapters/web/src/` (`docs/porting-guide.md:74-85`). Five TS files + tests on the host side; a small Kotlin server module on the device side.

```
adapters/android/
  package.json                      # @dcc/adapters-android, deps: @dcc/core (workspace:*)
  tsconfig.json                     # extends ../../tsconfig.base.json
  vitest.config.ts
  src/
    android-handle.ts               # narrow interface: observe/dispatch/reset over the bridge
    bridge-handle.ts                # default impl: HTTP client over adb-forwarded port
    observer.ts                     # pure: AndroidHandle → ObservationResult (Frame + pointsByIndex)
    dispatch.ts                     # pure: switch over Action.type; tap/scroll/type/key/back/home
    android-driver.ts               # AndroidDriver implements Driver
    index.ts                        # barrel
  tests/
    dispatch.test.ts                # fake-handle dispatch tests at minimum
    observer.test.ts                # VH JSON fixture → ActionableElement[] tests
  odim-android/                     # the existing Android Studio project (kept in-tree)
    app/src/main/java/edu/illinois/odim/
      bridge/
        BridgeServer.kt             # NEW: NanoHTTPD server exposing /observe, /dispatch, /reset
        BridgeRouter.kt             # NEW: request → AccessibilityService calls
      CoreAccessibilityService.kt   # KEEP, expose observe()/dispatch() as public for BridgeRouter
      AgentController.kt            # DELETE (orchestrator lives in TS now)
      network/
        AgentBrain.kt               # DELETE (TS QwenBrain owns this)
        ClaudeClient.kt             # DELETE
        OllamaClient.kt             # DELETE (also fixes the qwen3-vl deprecation bug)
        AgentPrompts.kt             # DELETE (prompts move to core/src/brains/qwen.ts)
      activities/AgentActivity.kt   # if it exists, DELETE; capture/upload activities stay
```

`docs/porting-guide.md:357` requires adding the platform string to the `Platform` union "in the same PR as the adapter." `"android"` is already present in `core/src/types/frame.ts:3`, so no core change needed.

---

## 4. Bridge protocol (host TS ↔ on-device Kotlin)

Tiny HTTP/JSON over `adb forward tcp:8765 tcp:8765`. Three endpoints. No auth (loopback over USB only; do not bind 0.0.0.0).

| Method + path | Request body | Response body |
|---|---|---|
| `GET /observe` | — | `{ screenshotPng: base64, viewport: {w,h}, vh: <raw VH JSON>, pkg: string, window: string, capturedAt: ISO8601 }` |
| `POST /dispatch` | `{ kind: "tap" \| "scroll" \| "type" \| "key" \| "back" \| "home", ... }` | `{ success: boolean, error?: string }` |
| `POST /reset` | — | `{ success: boolean, error?: string }` |

Why not gRPC / WebSocket / protobuf: scope. HTTP/JSON is one `OkHttp` import on the host and one `NanoHTTPD` dependency on device, and the per-step traffic is small (single screenshot ~100–500 KB).

**VH JSON shape stays exactly what `CoreAccessibilityService.kt` already serializes today.** Normalization to `ActionableElement` happens host-side in `observer.ts`. This keeps the Kotlin diff tiny and matches `docs/porting-guide.md:48` ("`rawTreeBlob` — Don't normalize it; persist it raw").

---

## 5. Observer — VH JSON → `ActionableElement[]`

Following the worked example in `docs/porting-guide.md:131-186`:

- 1-based, stable-within-frame index (`docs/porting-guide.md:325`).
- `center` computed once from VH bounds; the brain may be wrong about pixel coords but the bounds we extracted from the AccessibilityService are trusted (`docs/porting-guide.md:191`, `docs/architecture.md:83`).
- `flags`: map AccessibilityNodeInfo's `isClickable / isScrollable / isEditable / isFocused` directly (`docs/architecture.md:82`).
- `resourceId`: pass through `viewIdResourceName` when present.
- Side-table: `pointsByIndex: Map<number, PixelPoint>` returned alongside the `Frame`, mirroring web's `selectorsByIndex`. `dispatch.ts` resolves `target.by === "index"` against this map.

Filter policy to document in `adapters/android/README.md` per `docs/porting-guide.md:332`: drop nodes with zero-area bounds, drop nodes outside the screen viewport, drop password-masked fields' text (keep the node, redact `label`).

---

## 6. Dispatch — `Action` switch

`adapters/android/src/dispatch.ts` is a pure function, mirroring `adapters/web/src/dispatch.ts`. One switch on `Action.type` (`core/src/types/action.ts`):

| Action | Bridge call |
|---|---|
| `click` | `POST /dispatch { kind: "tap", x, y }` (resolve `target` to point via `pointsByIndex` first) |
| `type` | optionally tap target first, then `POST /dispatch { kind: "type", text }` (server does `setText` on the focused editable, falling back to `dispatchKeyEvent` per char) |
| `key` | `POST /dispatch { kind: "key", keycode }` |
| `scroll` | `POST /dispatch { kind: "scroll", direction }` (server synthesizes a `GestureDescription` swipe centered on the viewport) |
| `navigate_back` | `POST /dispatch { kind: "back" }` (`performGlobalAction(GLOBAL_ACTION_BACK)`) |
| `navigate_home` | `POST /dispatch { kind: "home" }` (`performGlobalAction(GLOBAL_ACTION_HOME)`) |
| `wait` | no-op, return `{ success: true }` (`docs/porting-guide.md:60`) |
| `finding`, `done` | `{ success: false, error: "terminal action ... should not be dispatched" }` (`docs/porting-guide.md:225-229`) |
| `target.by === "description"` | `{ success: false, error: "...orchestrator should ground first" }` (`docs/porting-guide.md:251-253`) |

Errors are caught and returned, never thrown (`docs/porting-guide.md:57`).

---

## 7. Driver glue

`AndroidDriver implements Driver` mirrors `adapters/web/src/web-driver.ts:53-199`:

- Holds the `AndroidHandle`, holds the `pointsByIndex` side-table between `observe()` and `dispatch()`.
- `platform = "android" as const` (the literal must match `Frame.platform` per `docs/porting-guide.md:330`).
- `observe(opts)` honors `opts.signal` by passing an `AbortSignal` into the underlying `fetch` (`docs/porting-guide.md:331`).
- `settle(opts)`: poll `/observe` until two consecutive VH dumps are structurally equal (or N ms elapsed). Event-driven approximation — Android has no clean "idle" signal; this is the closest equivalent and matches the porting guide's "platform's idle signal with a timeout, never `await sleep()`" rule (`docs/porting-guide.md:62`, `docs/porting-guide.md:325`). Default timeout 5000 ms.
- `reset(opts)`: `POST /reset` — server returns to launcher (`performGlobalAction(GLOBAL_ACTION_HOME)`) and optionally restarts a configured target package via `am start`.
- Constructor takes either an `AndroidDriverOptions` (default factory builds an `HttpAndroidHandle` against `http://127.0.0.1:8765`) or an injected `AndroidHandle` for tests — same factory pattern as `WebDriver`'s `pageFactory`.

No `allowedOrigins` equivalent for v0 — Android doesn't have a `navigate(url)` action in our vocabulary. If we add one later (deep-link intents), gate it the same way `WebDriver.isNavigationAllowed()` does (`docs/porting-guide.md:333`).

---

## 8. On-device bridge — Kotlin

Two new files in `app/src/main/java/edu/illinois/odim/bridge/`:

- `BridgeServer.kt` — `NanoHTTPD` subclass listening on `127.0.0.1:8765`, started from `CoreAccessibilityService.onServiceConnected()` and stopped from `onUnbind()`. Lifetime tied to the AccessibilityService — no server when the service is off, which is the right safety property.
- `BridgeRouter.kt` — request handler that calls into `CoreAccessibilityService` for screenshot/VH and dispatches gestures via the existing `executeAction()`-style code path.

Existing modifications to `CoreAccessibilityService.kt`: expose `takeScreenshot()`, `dumpViewHierarchy()`, and the gesture dispatch path as `internal` methods (today they live behind the agent loop). No behavior change for passive capture.

`local.properties` keys (`adapters/android/odim-android/AGENT_README.md:42-50`) for `CLAUDE_API_KEY` / `OLLAMA_API_URL` are dropped — credentials live host-side now.

---

## 9. Testing

Per `docs/porting-guide.md:309-317`:

- `tests/dispatch.test.ts` — hand-rolled fake `AndroidHandle` that records every `dispatch` call. Cover every `Action.type`, plus the two failure modes (`target.by === "description"` and terminal actions reaching dispatch).
- `tests/observer.test.ts` — feed canned VH JSON fixtures (capture two or three real ones from a running device early Wed) into `observeFoo`-equivalent and assert the resulting `ActionableElement[]`.
- **No emulator in CI for v0.** A single live acceptance test (gated behind `RUN_LIVE_ANDROID=1` à la web's `RUN_LIVE_BRAIN`, `README.md:66-75`) drives one fixture scenario against an emulator on the dev machine. This is the kickoff "acceptance test" deliverable (`docs/kickoff.md:70`).

---

## 10. Sequencing for the remaining sprint window

Today is Tue 2026-04-28. Wed 2026-04-29 EoD is the acceptance-test deadline (`docs/kickoff.md:70`).

**Tue (today):**
1. Post the 2-line check in the kickoff issue: "going with on-device HTTP bridge so passive capture stays inside the same app — flag if you want pure-ADB instead." Don't block on the answer; it's the right call either way.
2. Scaffold `adapters/android/` package (TS package.json, tsconfig, vitest config, empty `src/` files matching the layout above).
3. Write `android-handle.ts` (interface) and `bridge-handle.ts` (HTTP client stub against a fake server in tests).
4. Write `observer.ts` against canned VH JSON fixtures pulled from `~/odim-android` test data (or a fresh emulator dump). Land tests.
5. Write `dispatch.ts` + tests with a fake handle. Land.

**Wed:**
1. Kotlin bridge: `BridgeServer` + `BridgeRouter`, expose existing capture/dispatch internals. Build the APK.
2. `AndroidDriver` glue + the live acceptance test. `adb forward`, run one LookOutUX-equivalent Android scenario end-to-end through `AndroidDriver + QwenBrain + DOMIndexedGrounder + PlanExecuteReflectOrchestrator`.
3. Delete the now-superseded Kotlin agent files (`AgentController.kt`, `network/AgentBrain.kt`, `network/ClaudeClient.kt`, `network/OllamaClient.kt`, `network/AgentPrompts.kt`). The `qwen3-vl:32b` bug (`docs/kickoff.md:68`) is fixed by deletion.
4. `adapters/android/README.md` documenting the bridge protocol, the VH-filter policy (`docs/porting-guide.md:332`), and how to start an emulator + `adb forward` for the live test.

**Thu:** integration with web + iOS-sim through the same engine (`docs/kickoff.md:15`).

---

## 11. Open questions to confirm before writing code

1. **Bridge vs pure-ADB** — flag in the kickoff issue, proceed with bridge unless Jerry pushes back hard. Reason: passive-capture preservation is non-negotiable per `docs/kickoff.md:69`, and a pure-ADB adapter wouldn't share the AccessibilityService with the passive-capture flow.
2. **Single APK or two?** Recommend single APK (the bridge server lives inside the existing ODIM app, conditionally started). Two APKs splits the AccessibilityService permission surface and makes UX worse for testers.
3. **Network binding** — bind only `127.0.0.1` (loopback over USB), do not bind `0.0.0.0`. The bridge is an unauthenticated HTTP server on a phone that may be on a hostile Wi-Fi.
4. **`type` action semantics** — IME `setText` on the focused editable vs per-character `dispatchKeyEvent`. ODIM's existing `executeAction` already chooses; preserve that choice unless tests show it's broken.

---

## 12. Risks

- **Settle heuristic is approximate.** Two consecutive identical VH dumps is a proxy for "UI is stable"; Android animations can produce stable-looking trees mid-transition. Mitigation: 250 ms minimum between polls, 5 s cap, and rely on the orchestrator's reflection step to catch a stale-frame action (`docs/architecture.md:80`).
- **Bridge process death.** If the AccessibilityService is killed by the OS, the bridge dies silently. Mitigation: host-side `/observe` returns a connection error; the orchestrator surfaces it; the human re-enables the service. Document in the adapter README.
- **Per-step latency.** Screenshot serialization to base64 over USB is the dominant cost (~100–300 ms for a 1080×1920 PNG). If this becomes a bottleneck, switch the screenshot endpoint to a raw binary `Content-Type: image/png` response — half the bytes, no base64 cost. Defer until it's actually a problem.
- **Scope creep on the Kotlin side.** Goal is to *delete* Kotlin files net. Resist adding agent-loop logic on-device; everything new should be a thin adapter into existing capture/dispatch primitives.

---

## Sources

Local authority (this repo unless noted):
- `core/src/interfaces/driver.ts:22-33` — the `Driver` contract.
- `core/src/types/frame.ts:3` — `Platform` union (already includes `"android"`).
- `adapters/web/src/web-driver.ts:53-199` — reference adapter; mirror this shape.
- `adapters/web/src/{page,observer,dispatch}.ts` — reference five-file split.
- `docs/architecture.md:76-90` — keep / revise / drop list for Android source.
- `docs/architecture.md:82-84` — `ActionableElement` semantic-frame contract.
- `docs/kickoff.md:64-71` — Carl's scope and acceptance test.
- `docs/kickoff.md:69` — passive-capture preservation requirement.
- `docs/kickoff.md:68` — `qwen3-vl:32b` deprecation fix.
- `docs/porting-guide.md` (full) — methodology and invariants for any new `Driver`.
- `docs/porting-guide.md:323-334` — invariants and pitfalls (1-based stable index, normalized coords, event-driven settle, raw `rawTreeBlob`, `signal` honoring, document semantic-tree filters).
- `tasks.md:50-58` — sprint punch-list rows for Android.
- `adapters/android/odim-android/README.md` — ODIM app overview, capture pipeline, storage layout.
- `adapters/android/odim-android/AGENT_README.md` — current Eyes/Brain/Hands implementation we are deleting.
- `adapters/android/odim-android/app/src/main/java/edu/illinois/odim/network/OllamaClient.kt:22` — the `qwen3-vl:32b` line that disappears with the file.

External authority (operational, off-repo):
- `~/.claude/model-playbook.md` — model selection rules; cited by `docs/kickoff.md:27` and `docs/architecture.md:25`.

# AGENTS.md
## Project Context
Android app for on-device interaction mining — capturing, curating, and uploading user interaction traces (screenshots, view hierarchies, gestures) for research.
- **Stack:** Kotlin 1.7.x, AndroidX, Material Design, ViewBinding, Coroutines, OkHttp, Jackson, Kotlinx Serialization, CameraX + ML Kit.
- **Concurrency:** Kotlinx Coroutines only. No new threading primitives or reactive libraries.
- **UI:** AndroidX + Material Design only (Activities, Fragments, RecyclerView, ConstraintLayout, ViewBinding). No Jetpack Compose.
- **Priority:** Data integrity and trace correctness. Preserve existing capture, curation, and upload behavior.
- **Conflict Rule:** If repo code conflicts with this file, **trust the repo** and note the discrepancy in your plan.

## Source of Truth
Before changing logic or data format, verify:
- `app/src/main/java/edu/illinois/odim/dataclasses/` – source of truth for all data shapes (`Gesture`, `Redaction`, `CaptureStore`, `CaptureData`, `GestureCandidate`, etc.).
- `utils/LocalStorageOps.kt` – canonical file I/O: storage layout, file naming conventions, and artifact paths under `files/TRACES/<pkg>/<trace>/<event>/`.
- `utils/UploadDataOps.kt` – upload logic and endpoint contracts (`POST /api/capture/{captureId}/upload/frames` and `.../metadata`).
- `CoreAccessibilityService.kt` – capture pipeline; changes here affect all trace recording behavior.
- `app/src/main/AndroidManifest.xml` – declared permissions, service registration, and manifest placeholders.
- `app/build.gradle` – `BuildConfig` fields (e.g., `API_URL_PREFIX`), SDK versions, and dependency versions.

## Project Structure
- `activities/` – `CaptureActivity`, `ScannerActivity`, `AppActivity`, `TraceActivity`, `EventActivity`, `ScreenShotActivity`, `IncompleteScreenActivity`
- `adapters/` – `AppAdapter`, `TraceAdapter`, `EventAdapter`, `VHAdapter`
- `dataclasses/` – data models for gestures, redactions, captures, VH items, and UI list items
- `fragments/` – overlays and floating widgets (`ScrubbingScreenshotOverlay`, `MovableFloatingActionButton`, `IncompleteScreenCanvasOverlay`)
- `utils/` – `LocalStorageOps` (file I/O), `UploadDataOps` (network), `ScreenDimensionsOps` (display)
- `CoreAccessibilityService.kt` – `MyAccessibilityService`, the core capture service
- `app/src/main/res/` – layouts, drawables, strings, menus, themes, accessibility service config

---

## 1. Mandatory Plan-First Protocol
### Plan Before Code
Before any changes, provide:
1. A checklist of steps.
2. Exact file list with action: **Create / Modify / Delete**.
3. Labels for: **Breaking, Risky, Service, or Data Format** changes.
4. Known failure points and edge cases.

**Wait for explicit approval.** Only proceed when the user says:
- `APPROVE PLAN`: Proceed with implementation.
- `APPROVE BREAKING CHANGE`: Authorize data format/storage layout/upload API shifts.

### Scope Changes
If work reveals a necessary deviation: **Stop immediately**, explain the deviation, and wait for renewed approval.

---

## 2. Technical Standards
- **Kotlin:** No `!!` without a comment explaining why it cannot be null. Prefer `?.let`, `?:`, and safe casts.
- **Data classes:** All persisted data shapes live in `dataclasses/`. Use Jackson (`ObjectMapper().registerKotlinModule()`) for local JSON I/O and Kotlinx Serialization for API payloads — do not mix them arbitrarily.
- **Storage layout:** Never change the `TRACES/<pkg>/<trace>/<event>/` directory structure or file naming conventions (`<event>.png`, `vh-<event>.json`, `gesture-<event>.json`, `redact-<event>.json`, `task.json`) without an `APPROVE BREAKING CHANGE`. Existing on-device data will become unreadable.
- **Accessibility service:** Changes to `CoreAccessibilityService.kt` affect all active capture sessions. Be conservative — avoid adding blocking I/O or slow operations on the event callbacks.
- **UI/UX:** All long-running operations (uploads, file I/O, image processing) must run off the main thread via coroutines. Show progress/loading states; disable action buttons during execution to prevent double-submission.
- **Networking:** Base URL comes from `BuildConfig.API_URL_PREFIX` — never hardcode URLs. Validate HTTP responses before processing. Handle timeouts and partial uploads with clear failure messaging.
- **Permissions:** Do not add new permissions without explicit discussion. Current set: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `QUERY_ALL_PACKAGES`.
- **Privacy:** Screenshots and VH are only ever captured for non-ODIM, non-launcher, non-system-UI packages. Do not weaken this filter.

---

## 3. Execution & Verification
- Implement in small, independently reviewable steps. No unrelated refactors.
- **Change Summary:** After each step, group changes by: **UI, Service/Capture, Storage, Network, Data Model, and Dependencies.**
- **Manual Test Checklist:** Provide a custom Markdown checkbox list derived from the diff:
  - **Happy Path:** One end-to-end success scenario (e.g., capture → browse → upload).
  - **Edge Cases:** Empty trace, missing gesture file, interrupted upload, device rotation mid-activity.
  - **Double-Submit:** Verify upload/save buttons disable during execution.
  - **Persistence:** Verify data survives app restart (files present and correctly loaded).
  - **Environment:** Label tests `[Emulator]` or `[Physical Device]` when behavior may differ (e.g., screenshot capture requires a real accessibility service context on a physical device).

---

## 4. Non-Goals
**Do not, under any circumstances:**
- Change the on-device storage layout or JSON data formats without explicit `APPROVE BREAKING CHANGE`.
- Modify the `CoreAccessibilityService` capture filter (package exclusion logic) without explicit `APPROVE BREAKING CHANGE`.
- Add new dependencies or external services without explicit `APPROVE BREAKING CHANGE`.
- Perform unrelated refactors alongside a targeted fix or feature.

---

## Agent Principle
Be conservative. Minimize side effects. Correctness over elegance. **Ask when uncertain.**
A wrong assumption caught before implementation costs nothing; after, it costs corrupted on-device trace data.

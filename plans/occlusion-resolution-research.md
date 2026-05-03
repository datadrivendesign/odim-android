# Android Occlusion Resolution Guide

This document outlines the logic that should be implemented in the host-side TypeScript `AndroidDriver` to correctly handle element occlusion using the metadata provided by the ODIM bridge.

## 1. Data Schema Overview
The `/observe` response provides a View Hierarchy (VH) with the following key fields:

### Root Level
- `windows`: `Array<{id: number, layer: number, type: number, bounds: string}>`
  - **layer**: The Z-order of the window. Higher numbers are drawn on top.
  - **type**: 1 = Application, 2 = Input Method (Keyboard), etc.

### Node Level
- `window_id`: Associates the node with a window in the root `windows` array.
- `drawing_order`: The Z-order of the node relative to its siblings within the same window.
- `important`: (`isImportantForAccessibility`) Boolean indicating if the node is a primary semantic element.
- `visibility`: Boolean indicating if the system considers the node visible.
- `bounds_in_screen`: Absolute pixel coordinates `[left, top, right, bottom]`.

---

## 2. The Resolution Algorithm

To ensure the LLM only interacts with "top-most" visible elements, follow these steps:

### Step 1: Map Window Layers
Create a lookup table of window layers from the root `windows` array.
- **Rule:** A higher `layer` value always draws on top of a lower `layer` value.
- **Note:** The Keyboard (IME) typically has a higher layer than the application window.

### Step 2: Pre-Filter Nodes
Discard nodes that are definitively not actionable:
1. `visibility !== true`
2. `important !== true` (Filters out background decorators and non-semantic containers).
3. `bounds_in_screen` has zero area or is entirely outside the `viewport`.
4. Node is not `clickable`, `scrollable`, `long-clickable`, or `editable`.

### Step 3: Spatial Occlusion Check (The "Point-Winner" Logic)
When generating the final element list for the LLM:
1. For each candidate element, calculate its center point $(x, y)$.
2. Identify **all** elements in the tree whose `bounds_in_screen` contain that center point.
3. Sort this list of overlapping elements using the **Z-Order Priority**:
    - **Priority 1 (Window Layer):** Higher `window.layer` wins.
    - **Priority 2 (Drawing Order):** If in the same window, higher `drawing_order` wins.
    - **Priority 3 (Hierarchy Depth):** If drawing order is equal, the child (deeper node) wins.
4. An element is considered **Visible/Actionable** only if it is the "Point Winner" (the top-most item) for its own center coordinate.

---

## 3. Chrome Example: Search Bar Overlap
When the Chrome search results list is open:
1. **Search Results Window:** Map to `window_id` with Layer 5.
2. **Background Page Window:** Map to `window_id` with Layer 1.
3. If an "Article" link in the background is at the same $(x, y)$ as a "Search Suggestion" item:
   - The logic sees `Suggestion (Layer 5)` vs `Article (Layer 1)`.
   - **Result:** The Suggestion wins. The Article is hidden from the LLM's view.

---

## 4. Implementation Reference (Pseudo-Code)

```typescript
function isElementOccluded(targetEl, allElements, windowMap) {
  const center = targetEl.getCenter();
  
  const competitors = allElements.filter(el => 
    el.visibility && 
    el.bounds.contains(center.x, center.y)
  );

  const topMost = competitors.sort((a, b) => {
    // 1. Compare Window Layers
    const layerA = windowMap.get(a.windowId).layer;
    const layerB = windowMap.get(b.windowId).layer;
    if (layerA !== layerB) return layerB - layerA;

    // 2. Compare Drawing Order within same window
    if (a.drawingOrder !== b.drawingOrder) return b.drawingOrder - a.drawingOrder;

    // 3. Deeper in tree is usually on top
    return b.depth - a.depth;
  })[0];

  return topMost.id !== targetEl.id;
}
```

## 5. Summary of Best Practices
- **Respect `important`:** Android often reports "invisible" layout containers as `visible: true`. The `important` flag is the best filter for elements that actually matter.
- **Keyboard Awareness:** Use `window.type === 2` to identify the keyboard. This allows you to explicitly ignore or handle actions that would be blocked by the soft keyboard.
- **Z-First:** Always resolve Z-order before returning elements to the brain to prevent "phantom" clicks on obscured content.

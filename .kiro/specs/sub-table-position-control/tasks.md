# Implementation Plan: Sub-Table Position Control
    
## Overview

Introduce a `subTable` placeholder field type that `FormRenderer` renders inline at the correct position, while preserving the existing bottom-render fallback for forms that have no placeholders. Changes span `FormRenderer.vue`, the three parent views, and `FormDesigner.vue`.

## Tasks

- [x] 1. Extend `FormField` interface and `extractFieldsRecursive` in `FormRenderer.vue`
  - Add optional `_bindingId?: number` to the `FormField` interface
  - Update `extractFieldsRecursive` to emit a `FormField` with `type: "subTable"` and `_bindingId` when it encounters a rule with `type === "subTable"` and a non-null `_bindingId`
  - Apply the same guard inside the tab-pane loop in `parseFormConfig` so tab-scoped placeholders are included in the correct tab's `fields` array
  - _Requirements: 1.1, 1.2, 1.3, 6.2_

  - [x] 1.1 Write property test for `parseFormConfig` round-trip (Property 1)
    - **Property 1: parseFormConfig round-trip for subTable rules**
    - **Validates: Requirements 1.1, 1.2**

  - [x] 1.2 Write property test for tab-pane subTable placeholder (Property 6)
    - **Property 6: Tab-pane subTable placeholder produces FormField in correct tab**
    - **Validates: Requirements 6.1, 6.2**

- [x] 2. Add `subTableBindings` prop and inline rendering to `FormRenderer.vue`
  - Add `subTableBindings?: SubTableBinding[]` prop (defaults to `[]`)
  - Add `update:subTableData` emit signature
  - Implement `bindingMap` computed and `resolveBinding` helper
  - In the flat-mode field loop, add a `v-if="field.type === 'subTable'"` branch that renders `SubTableField` inline (skipping the `el-col`/`el-form-item` wrapper) and a silent skip when no matching binding is found
  - Apply the same branch inside the tab-mode field loop
  - Wire `@update:model-value` on the inline `SubTableField` to emit `update:subTableData`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.3, 6.1_

  - [x] 2.1 Write property test for inline editable prop (Property 2)
    - **Property 2: Inline sub-table editable prop reflects mode**
    - **Validates: Requirements 2.4, 2.5**

  - [x] 2.2 Write property test for `update:subTableData` emit (Property 3)
    - **Property 3: update:subTableData emitted on inline row change**
    - **Validates: Requirements 2.6, 5.1**

- [x] 3. Checkpoint — Ensure all tests pass, ask the user if questions arise.

- [x] 4. Update parent views (`start.vue`, `detail.vue`, `applications/detail.vue`) for backward-compatible bottom-render
  - Add `placedBindingIds` computed that collects `_bindingId` values from `formFields` and all tab `fields`
  - Add `bottomSubTableBindings` computed that filters out placed bindings
  - Pass full `subTableBindings` array to `FormRenderer` via the new prop
  - Handle `update:subTableData` on `FormRenderer` to update `subTableBindings[i].data` in local state
  - Change the bottom-render loop to iterate `bottomSubTableBindings` instead of `subTableBindings`
  - _Requirements: 3.1, 3.2, 5.1, 5.2, 5.3_

  - [x] 4.1 Write property test for bottom-render fallback set (Property 4)
    - **Property 4: Bottom-render fallback contains exactly unplaced bindings**
    - **Validates: Requirements 3.1, 3.2**

  - [x] 4.2 Write property test for submission payload completeness (Property 5)
    - **Property 5: Submission payload includes all sub-table data regardless of placement**
    - **Validates: Requirements 5.2**

- [x] 5. Checkpoint — Ensure all tests pass, ask the user if questions arise.

- [x] 6. Add Sub-Table palette item and config panel to `FormDesigner.vue`
  - Register a `SubTablePlaceholderWidget` render component for the canvas preview
  - Add a `subTable` group to `designerConfig.menu` with a palette item that inserts `{ type: "subTable", _bindingId: null, title: "Sub-Table", props: {} }` into the rule array
  - Populate the `_bindingId` select options reactively from `designerSubBindings`
  - In `handleSaveForm`, scan the rule for `subTable` entries with a falsy `_bindingId`; show `ElMessage.error` and return early if any are found
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 6.1 Write property test for FormDesigner save serialization (Property 7)
    - **Property 7: FormDesigner save serializes _bindingId into rule**
    - **Validates: Requirements 4.4**

- [x] 7. Final checkpoint — Ensure all tests pass, ask the user if questions arise.

- [x] 8. Add subTable placeholders for Procurement Workflow's RequestItems and RequestAttachments sub-tables into the Request Form's rule array in `03-form-table-bindings.sql`, then re-run the SQL against the dev database.
  - Use the `jsonb_set` + `||` pattern from the design document to append (or insert at the correct index) a `{ "type": "subTable", "_bindingId": <id>, "title": "Sub-Table", "props": {} }` entry for each of the two bindings
  - _Requirements: 1.1, 2.1_

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use `fast-check` (already installed in both `user-portal` and `developer-workstation`)
- The bottom-render fallback requires zero migration — forms without `subTable` rules are unaffected

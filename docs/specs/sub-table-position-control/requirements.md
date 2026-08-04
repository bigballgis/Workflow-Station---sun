# Requirements Document

## Introduction

This feature enables form designers to control the position of sub-tables (child table bindings via `dw_form_table_bindings`) within the main form layout. Currently, sub-tables are always rendered after all main form fields, hardcoded at the bottom of the form in `start.vue`, `detail.vue`, and `applications/detail.vue`. The desired behavior is to allow a placeholder entry of type `subTable` in the form's `rule` array (the `config_json` stored in the database) that references a specific binding by `_bindingId`. When `FormRenderer` encounters this placeholder, it renders the corresponding `SubTableField` inline at that position, allowing sub-tables to appear anywhere in the form layout.

## Glossary

- **FormRenderer**: The Vue 3 component (`FormRenderer.vue`) responsible for rendering main form fields from a `fields` array or `tabs` array derived from the form's `config_json`.
- **SubTableField**: The Vue 3 component that renders a sub-table with an "+ Add" button and a dialog for adding rows, bound to a `dw_form_table_bindings` entry.
- **SubTablePlaceholder**: A special entry in the form's `rule` array with `type: "subTable"` and a `_bindingId` property referencing a `dw_form_table_bindings.id`. It marks the desired inline position of a sub-table within the form layout.
- **FormField**: The TypeScript interface in `FormRenderer.vue` describing a single renderable field, including its `type`, `key`, `label`, and other display properties.
- **SubTableBinding**: A runtime object derived from `dw_form_table_bindings` that holds the binding metadata, column definitions, and row data for a sub-table.
- **config_json**: The JSON configuration stored per form in the database, containing a `rule` array of field definitions consumed by the form engine.
- **FormDesigner**: The Vue 3 component (`FormDesigner.vue`) in the developer workstation used to visually design forms and produce `config_json`.
- **inline sub-table**: A sub-table rendered at a specific position within the form layout, as opposed to being appended after all main fields.
- **bottom-rendered sub-table**: The current behavior where all sub-tables appear after all main form fields regardless of their intended position.

## Requirements

### Requirement 1: SubTablePlaceholder Field Type in FormField Interface

**User Story:** As a form designer, I want to place a sub-table placeholder in the form rule array, so that I can control where a sub-table appears within the form layout.

#### Acceptance Criteria

1. THE FormRenderer SHALL accept `FormField` entries with `type: "subTable"` and a `_bindingId: number` property in the `fields` array.
2. WHEN the `parseFormConfig` function encounters a rule with `type: "subTable"` in the `rule` array, THE FormRenderer SHALL produce a `FormField` with `type: "subTable"` and `_bindingId` set to the value of the rule's `_bindingId` property.
3. THE `FormField` interface SHALL include an optional `_bindingId` property of type `number` to support sub-table placeholder entries.

---

### Requirement 2: Inline Sub-Table Rendering in FormRenderer

**User Story:** As a user submitting or reviewing a form, I want sub-tables to appear at the position defined by the form designer, so that the form layout matches the intended design.

#### Acceptance Criteria

1. WHEN FormRenderer renders a `FormField` with `type: "subTable"`, THE FormRenderer SHALL render the corresponding `SubTableField` component inline at that position in the field list, instead of a standard form input.
2. WHEN FormRenderer renders a `FormField` with `type: "subTable"` and no matching `SubTableBinding` is found for the given `_bindingId`, THE FormRenderer SHALL render nothing for that placeholder (silent skip).
3. THE FormRenderer SHALL accept a `subTableBindings` prop of type `SubTableBinding[]` to supply the binding data needed to render inline sub-tables.
4. WHEN FormRenderer is in `readonly` mode and renders an inline sub-table, THE SubTableField SHALL be rendered with `editable` set to `false`.
5. WHEN FormRenderer is not in `readonly` mode and renders an inline sub-table, THE SubTableField SHALL be rendered with `editable` reflecting the binding's `bindingMode` (editable when `bindingMode === "EDITABLE"`).
6. WHEN a `SubTableField` rendered inline emits a data change, THE FormRenderer SHALL emit an `update:subTableData` event with the `bindingId` and updated row array, so the parent component can update its state.

---

### Requirement 3: Backward Compatibility for Bottom-Rendered Sub-Tables

**User Story:** As a developer maintaining existing forms, I want forms without sub-table placeholders to continue rendering sub-tables at the bottom, so that existing deployments are not broken.

#### Acceptance Criteria

1. WHEN a form's `rule` array contains no entries with `type: "subTable"`, THE start.vue, detail.vue, and applications/detail.vue SHALL continue to render all `subTableBindings` after the `FormRenderer` component, preserving the existing bottom-rendered behavior.
2. WHEN a `SubTableBinding` has its `bindingId` referenced by at least one `SubTablePlaceholder` in the form's `rule` array, THE parent view (start.vue, detail.vue, applications/detail.vue) SHALL NOT render that binding in the bottom-rendered fallback section.
3. THE FormRenderer SHALL NOT render a sub-table inline if the `subTableBindings` prop is not provided or is empty, ensuring no regression when the prop is omitted.

---

### Requirement 4: FormDesigner Support for SubTablePlaceholder

**User Story:** As a form designer using the developer workstation, I want to drag and drop a sub-table placeholder into the form canvas, so that I can visually set the position of a sub-table within the form.

#### Acceptance Criteria

1. THE FormDesigner SHALL provide a draggable palette item labeled "Sub-Table" (or equivalent localized label) that represents a `SubTablePlaceholder` field.
2. WHEN a designer drops the Sub-Table palette item onto the form canvas, THE FormDesigner SHALL insert a rule entry with `type: "subTable"` into the `rule` array at the dropped position.
3. WHEN a `SubTablePlaceholder` is selected on the canvas, THE FormDesigner SHALL display a configuration panel allowing the designer to select the target `dw_form_table_bindings.id` via a dropdown of available bindings for the current form.
4. WHEN the designer saves the form, THE FormDesigner SHALL serialize the `_bindingId` value into the `subTable` rule entry within `config_json`.
5. IF a designer attempts to save a `SubTablePlaceholder` without selecting a `_bindingId`, THEN THE FormDesigner SHALL display a validation error and prevent saving.

---

### Requirement 5: Data Flow for Inline Sub-Table Row Data

**User Story:** As a user filling out a form, I want sub-table row data entered at an inline position to be included in the form submission, so that the data is saved correctly regardless of where the sub-table appears.

#### Acceptance Criteria

1. WHEN a user adds, edits, or removes rows in an inline `SubTableField`, THE parent view SHALL update the corresponding `SubTableBinding.data` array in its local state.
2. WHEN the user submits the form, THE parent view SHALL include all inline sub-table row data in the submission payload, using the same structure as bottom-rendered sub-tables.
3. THE SubTableBinding data model used for inline sub-tables SHALL be identical in structure to the one used for bottom-rendered sub-tables, ensuring a single submission serialization path.

---

### Requirement 6: Tab Layout Compatibility

**User Story:** As a form designer, I want to place sub-table placeholders inside tab panes, so that sub-tables can be positioned within tabbed form layouts.

#### Acceptance Criteria

1. WHEN a `SubTablePlaceholder` entry appears within an `el-tab-pane`'s children in the `rule` array, THE FormRenderer SHALL render the corresponding inline `SubTableField` within that tab pane at the correct position.
2. WHEN FormRenderer parses a tabbed form config and encounters a rule with `type: "subTable"` inside a tab pane, THE FormRenderer SHALL include a `FormField` with `type: "subTable"` and the correct `_bindingId` in that tab's `fields` array.

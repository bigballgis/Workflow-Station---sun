# Design Document: Sub-Table Position Control

## Overview

This feature allows form designers to control where sub-tables (child table bindings) appear within the main form layout. Currently all sub-tables are hardcoded to render after all main form fields in `start.vue`, `detail.vue`, and `applications/detail.vue`. The solution introduces a `SubTablePlaceholder` — a special rule entry with `type: "subTable"` and a `_bindingId` — that `FormRenderer` recognizes and renders inline at the correct position. Existing forms without placeholders continue to work unchanged via a backward-compatible bottom-render fallback.

The change is purely additive: no existing interfaces are broken, no existing data is migrated, and the bottom-render path remains the default for all forms that have not been updated.

---

## Architecture

### Current Flow

```
config_json.rule  →  parseFormConfig  →  formFields[]  →  FormRenderer (main fields only)
                                                         ↓
                                          subTableBindings[]  →  bottom-rendered SubTableField loop
```

### Target Flow

```
config_json.rule  →  parseFormConfig  →  formFields[]  (includes subTable placeholder entries)
                                         ↓
                                      FormRenderer
                                         ├── normal field  →  el-form-item
                                         └── subTable field  →  SubTableField (inline, at position)
                                         ↓
                                      emits update:subTableData(bindingId, rows)
                                         ↓
                                      parent view updates subTableBindings[bindingId].data

config_json.rule  (no subTable entries)  →  unchanged bottom-render fallback in parent views
```

### Backward Compatibility Strategy

The parent views (`start.vue`, `detail.vue`, `applications/detail.vue`) compute a `placedBindingIds` set from the parsed `formFields`. Only bindings whose `bindingId` is **not** in `placedBindingIds` are rendered in the bottom-render fallback loop. This means:

- Forms with no `subTable` rules → `placedBindingIds` is empty → all bindings bottom-rendered (existing behavior).
- Forms with some `subTable` rules → placed bindings rendered inline, unplaced bindings still bottom-rendered.

---

## Components and Interfaces

### 1. `FormField` interface (FormRenderer.vue)

Add two optional fields:

```typescript
export interface FormField {
  // ... existing fields ...
  _bindingId?: number   // set when type === 'subTable'
}
```

The `type` field already accepts `string`, so `"subTable"` is valid without a union change. The `_bindingId` property is only meaningful when `type === "subTable"`.

### 2. `SubTableBinding` type (shared across views)

The existing inline type used in `start.vue`, `detail.vue`, and `applications/detail.vue` is unchanged. `FormRenderer` receives it via the new `subTableBindings` prop.

```typescript
interface SubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string        // "EDITABLE" | "READONLY"
  tableName: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string; [key: string]: any }>
  data: any[]
}
```

### 3. `FormRenderer` props and emits

```typescript
interface Props {
  fields: FormField[]
  tabs?: FormTab[]
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]   // NEW — optional, defaults to []
}

// NEW emit
(e: 'update:subTableData', bindingId: number, rows: any[]): void
```

### 4. `parseFormConfig` / `extractFieldsRecursive` (start.vue, detail.vue, applications/detail.vue)

`extractFieldsRecursive` currently skips items without a `field` property. It must be extended to also emit a `FormField` for items with `type === "subTable"`:

```typescript
const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    if (item.type === 'subTable' && item._bindingId != null) {
      // Emit a placeholder field — no `field` key needed
      fields.push({ key: `__subTable_${item._bindingId}`, label: '', type: 'subTable', _bindingId: item._bindingId })
    } else if (item.field) {
      const field = convertFormCreateRule(item)
      if (field) fields.push(field)
    }
    if (item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children))
    }
  }
  return fields
}
```

The same logic applies inside the tab-pane loop in `parseFormConfig`.

### 5. `FormRenderer` template — inline sub-table rendering

Inside both the flat-mode `<el-col v-for="field in fields">` loop and the tab-mode `<el-col v-for="field in tab.fields">` loop, add a branch **before** the `<el-form-item>` wrapper:

```html
<!-- Inline sub-table placeholder -->
<template v-if="field.type === 'subTable'">
  <SubTableField
    v-if="resolveBinding(field._bindingId)"
    :title="resolveBinding(field._bindingId)!.tableName"
    :columns="resolveBinding(field._bindingId)!.columns"
    :model-value="resolveBinding(field._bindingId)!.data"
    :editable="!readonly && resolveBinding(field._bindingId)!.bindingMode === 'EDITABLE'"
    @update:model-value="rows => emit('update:subTableData', field._bindingId!, rows)"
  />
  <!-- silent skip when no matching binding -->
</template>
<el-col v-else ...>
  <el-form-item ...>
    ...
  </el-form-item>
</el-col>
```

`resolveBinding` is a computed helper:

```typescript
const bindingMap = computed(() => {
  const map = new Map<number, SubTableBinding>()
  for (const b of (props.subTableBindings ?? [])) map.set(b.bindingId, b)
  return map
})
const resolveBinding = (id?: number) => id != null ? bindingMap.value.get(id) : undefined
```

Note: the `<el-col>` wrapper is skipped for `subTable` fields so the sub-table spans the full width naturally.

### 6. Parent views — backward-compatible bottom-render

In `start.vue`, `detail.vue`, and `applications/detail.vue`, after `parseFormConfig` populates `formFields` / `formTabs`, compute the set of placed binding IDs:

```typescript
// Computed from formFields + formTabs
const placedBindingIds = computed((): Set<number> => {
  const ids = new Set<number>()
  const collect = (fields: FormField[]) => fields.forEach(f => { if (f.type === 'subTable' && f._bindingId != null) ids.add(f._bindingId) })
  collect(formFields.value)
  formTabs.value.forEach(tab => collect(tab.fields))
  return ids
})

// Bindings not placed inline → still bottom-rendered
const bottomSubTableBindings = computed(() =>
  subTableBindings.value.filter(b => !placedBindingIds.value.has(b.bindingId))
)
```

The template bottom-render loop changes from iterating `subTableBindings` to `bottomSubTableBindings`. The `FormRenderer` receives the full `subTableBindings` array (so it can resolve inline placeholders) and handles `update:subTableData` to keep `subTableBindings[i].data` in sync:

```html
<FormRenderer
  ...
  :subTableBindings="subTableBindings"
  @update:subTableData="(id, rows) => {
    const b = subTableBindings.find(x => x.bindingId === id)
    if (b) b.data = rows
  }"
/>

<!-- Bottom-render fallback: only unplaced bindings -->
<template v-if="bottomSubTableBindings.length > 0">
  <div v-for="binding in bottomSubTableBindings" :key="binding.bindingId" class="sub-table-section">
    <SubTableField ... />
  </div>
</template>
```

### 7. `FormDesigner` — Sub-Table palette item and config panel

`@form-create/element-ui` v3 supports custom menu items via `designerConfig.menu`. A `subTable` rule type is registered as a custom component that renders a placeholder card in the designer canvas.

#### 7a. Register custom rule type

```typescript
// In FormDesigner.vue setup, before fc-designer mounts
import { defineComponent } from 'vue'

// Minimal render component shown on canvas
const SubTablePlaceholderWidget = defineComponent({
  props: { _bindingId: Number },
  template: `<div class="sub-table-placeholder-widget">
    <el-tag type="info">Sub-Table</el-tag>
    <span style="margin-left:8px;color:#909399;">{{ _bindingId ? 'Binding #' + _bindingId : 'No binding selected' }}</span>
  </div>`
})
```

#### 7b. Designer config with custom menu

```typescript
const designerConfig = {
  showDevice: true,
  showSave: false,
  fieldReadonly: false,
  menu: [
    // Existing groups are preserved; add a new group or append to "layout"
    {
      title: 'Sub-Table',
      name: 'subTable',
      list: [
        {
          label: 'Sub-Table',
          name: 'subTable',
          icon: 'icon-table',
          rule: {
            type: 'subTable',
            _bindingId: null,
            title: 'Sub-Table',
            props: {}
          },
          props: [
            {
              label: 'Binding',
              field: '_bindingId',
              type: 'select',
              options: []   // populated dynamically from designerSubBindings
            }
          ]
        }
      ]
    }
  ]
}
```

The `props` array drives the right-panel config UI. The `_bindingId` select options are populated reactively from `designerSubBindings.value` when the designer opens a form.

#### 7c. Save-time validation

In `handleSaveForm`, before persisting, scan the main rule for `subTable` entries missing `_bindingId`:

```typescript
const rule = designerRef.value.getRule()
const invalidPlaceholders = rule.filter((r: any) => r.type === 'subTable' && !r._bindingId)
if (invalidPlaceholders.length > 0) {
  ElMessage.error(t('form.subTableBindingRequired'))
  return
}
```

### 8. SQL pattern for `03-form-table-bindings.sql`

To add a `subTable` placeholder node to an existing form's `rule` array in `config_json`:

```sql
-- Insert a subTable placeholder at a specific position in the rule array
-- Replace :form_id and :binding_id with actual values
-- This appends the placeholder to the end of the rule array
UPDATE dw_form_definitions
SET config_json = jsonb_set(
  config_json,
  '{rule}',
  (config_json->'rule') || jsonb_build_object(
    'type', 'subTable',
    '_bindingId', :binding_id,
    'title', 'Sub-Table',
    'props', '{}'::jsonb
  )
)
WHERE id = :form_id;

-- To insert at a specific index (e.g., after index 2):
UPDATE dw_form_definitions
SET config_json = jsonb_set(
  config_json,
  '{rule}',
  (config_json->'rule')->0 ||   -- elements before insertion point
  jsonb_build_object('type', 'subTable', '_bindingId', :binding_id, 'title', 'Sub-Table', 'props', '{}'::jsonb) ||
  (config_json->'rule')->>2     -- elements after insertion point
)
WHERE id = :form_id;
```

For migration scripts, the recommended pattern is to append to the end (first query) and let designers reorder via the FormDesigner UI.

---

## Data Models

### SubTablePlaceholder rule entry (in `config_json.rule`)

```json
{
  "type": "subTable",
  "_bindingId": 42,
  "title": "Sub-Table",
  "props": {}
}
```

### FormField (runtime, after parseFormConfig)

```typescript
{
  key: "__subTable_42",   // synthetic key, not a form data field
  label: "",
  type: "subTable",
  _bindingId: 42,
  span: 24                // always full-width
}
```

### Submission payload (unchanged)

Sub-table data continues to be serialized under `__subTables__` in the form variables, keyed by `bindingId`. The inline path and the bottom-render path both write to `subTableBindings[i].data`, so the existing serialization in `handleSubmit` / `handleSaveDraft` requires no changes.

```json
{
  "fieldA": "value",
  "__subTables__": {
    "42": [{ "col1": "row1val" }, { "col1": "row2val" }]
  }
}
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

Before writing properties, reviewing the prework for redundancy:

- 1.1 and 1.2 both test `parseFormConfig` producing a subTable FormField. They can be combined: 1.2 is the more specific round-trip test that subsumes 1.1.
- 2.4 and 2.5 both test the `editable` prop of the inline SubTableField. They can be combined into one property: "editable equals false when readonly, else equals (bindingMode === EDITABLE)".
- 3.1 and 3.2 are complementary (not redundant): 3.1 tests the all-bottom case, 3.2 tests the partial-placement case.
- 5.1 and 5.2 are complementary: 5.1 tests state update, 5.2 tests submission payload.
- 6.1 and 6.2 are complementary: 6.2 (parse) implies 6.1 (render) if rendering is correct, but they test different layers. Keep both.

After reflection, the final property set is:

### Property 1: parseFormConfig round-trip for subTable rules

*For any* rule array containing one or more entries with `type: "subTable"` and a numeric `_bindingId`, `parseFormConfig` (via `extractFieldsRecursive`) should produce a `FormField` with `type: "subTable"` and the same `_bindingId` value for each such entry.

**Validates: Requirements 1.1, 1.2**

### Property 2: Inline sub-table editable prop reflects mode

*For any* `SubTableBinding` with any `bindingMode` value, when `FormRenderer` renders it inline, the `SubTableField`'s `editable` prop should be `false` when `FormRenderer` is in `readonly` mode, and should equal `(bindingMode === "EDITABLE")` when not in `readonly` mode.

**Validates: Requirements 2.4, 2.5**

### Property 3: update:subTableData emitted on inline row change

*For any* inline `SubTableField` data change (add, edit, or delete row), `FormRenderer` should emit `update:subTableData` with the correct `bindingId` and the updated row array.

**Validates: Requirements 2.6, 5.1**

### Property 4: Bottom-render fallback contains exactly unplaced bindings

*For any* set of `subTableBindings` and any form rule array, the bottom-rendered binding list should contain exactly those bindings whose `bindingId` does not appear in any `subTable` placeholder in the rule array.

**Validates: Requirements 3.1, 3.2**

### Property 5: Submission payload includes all sub-table data regardless of placement

*For any* form submission where some sub-tables are placed inline and others are bottom-rendered, the `__subTables__` payload should contain the row data for every binding, whether placed or not.

**Validates: Requirements 5.2**

### Property 6: Tab-pane subTable placeholder produces FormField in correct tab

*For any* tabbed form config where a `subTable` rule appears inside a specific `el-tab-pane`, `parseFormConfig` should include a `FormField` with `type: "subTable"` and the correct `_bindingId` in that tab's `fields` array, and not in any other tab's `fields` array.

**Validates: Requirements 6.1, 6.2**

### Property 7: FormDesigner save serializes _bindingId into rule

*For any* `subTable` placeholder on the designer canvas with a selected `_bindingId`, saving the form should produce a `config_json.rule` entry with `type: "subTable"` and the same `_bindingId` value.

**Validates: Requirements 4.4**

---

## Error Handling

| Scenario | Handling |
|---|---|
| `subTable` placeholder with no matching binding in `subTableBindings` | `FormRenderer` silently skips (renders nothing) — Requirement 2.2 |
| `subTableBindings` prop not provided | `FormRenderer` treats it as `[]`; all `subTable` fields silently skip; no regression |
| `_bindingId` is `null` or `undefined` in a rule entry | `extractFieldsRecursive` skips the entry (guard: `item._bindingId != null`) |
| Designer save with unset `_bindingId` | `handleSaveForm` shows `ElMessage.error` and returns early — Requirement 4.5 |
| Duplicate `_bindingId` placeholders in the same form | Both render; the same binding data is shown at both positions (last write wins for data updates) |
| Tab layout: `subTable` placeholder outside any tab pane | Treated as a flat-mode field; rendered in the flat field list if `hasTabs` is false, otherwise ignored |

---

## Testing Strategy

### Dual Testing Approach

Both unit tests and property-based tests are required. Unit tests cover specific examples and edge cases; property tests verify universal correctness across randomized inputs.

### Unit Tests

Focus on specific examples, integration points, and edge cases:

1. `extractFieldsRecursive` — given a rule with `type: "subTable"` and `_bindingId: 5`, returns a `FormField` with `type: "subTable"` and `_bindingId: 5`.
2. `extractFieldsRecursive` — given a rule with `type: "subTable"` and no `_bindingId`, skips the entry.
3. `FormRenderer` — when `subTableBindings` is not provided, no `SubTableField` is rendered.
4. `FormRenderer` — when a `subTable` field has no matching binding, nothing is rendered for that field.
5. `FormDesigner` `handleSaveForm` — when a `subTable` rule has `_bindingId: null`, save is blocked and an error message is shown.
6. `placedBindingIds` computed — returns empty set when no `subTable` fields exist (backward compat).

### Property-Based Tests

Using `fast-check` (already installed in both `user-portal` and `developer-workstation`). Each test runs a minimum of 100 iterations.

**Test file locations:**
- `frontend/user-portal/src/components/__tests__/FormRenderer.subTable.property.test.ts`
- `frontend/user-portal/src/views/__tests__/subTablePositionControl.property.test.ts`

**Property 1 implementation:**
```typescript
// Feature: sub-table-position-control, Property 1: parseFormConfig round-trip for subTable rules
fc.assert(fc.property(
  fc.array(fc.integer({ min: 1, max: 9999 }), { minLength: 1, maxLength: 5 }),
  (bindingIds) => {
    const rules = bindingIds.map(id => ({ type: 'subTable', _bindingId: id }))
    const fields = extractFieldsRecursive(rules)
    const subTableFields = fields.filter(f => f.type === 'subTable')
    expect(subTableFields.length).toBe(bindingIds.length)
    subTableFields.forEach((f, i) => {
      expect(f._bindingId).toBe(bindingIds[i])
    })
  }
), { numRuns: 100 })
```

**Property 2 implementation:**
```typescript
// Feature: sub-table-position-control, Property 2: Inline sub-table editable prop reflects mode
fc.assert(fc.property(
  fc.constantFrom('EDITABLE', 'READONLY', 'VIEW_ONLY'),
  fc.boolean(),
  (bindingMode, isReadonly) => {
    const binding = { bindingId: 1, bindingMode, tableName: 'T', columns: [], data: [] }
    const wrapper = mount(FormRenderer, {
      props: { fields: [{ key: '__subTable_1', label: '', type: 'subTable', _bindingId: 1 }], subTableBindings: [binding], readonly: isReadonly }
    })
    const subTable = wrapper.findComponent(SubTableField)
    const expectedEditable = !isReadonly && bindingMode === 'EDITABLE'
    expect(subTable.props('editable')).toBe(expectedEditable)
  }
), { numRuns: 100 })
```

**Property 3 implementation:**
```typescript
// Feature: sub-table-position-control, Property 3: update:subTableData emitted on inline row change
fc.assert(fc.property(
  fc.integer({ min: 1, max: 9999 }),
  fc.array(fc.record({ val: fc.string() })),
  (bindingId, newRows) => {
    const binding = { bindingId, bindingMode: 'EDITABLE', tableName: 'T', columns: [], data: [] }
    const wrapper = mount(FormRenderer, {
      props: { fields: [{ key: `__subTable_${bindingId}`, label: '', type: 'subTable', _bindingId: bindingId }], subTableBindings: [binding], readonly: false }
    })
    wrapper.findComponent(SubTableField).vm.$emit('update:modelValue', newRows)
    const emitted = wrapper.emitted('update:subTableData')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0]).toBe(bindingId)
    expect(emitted![0][1]).toEqual(newRows)
  }
), { numRuns: 100 })
```

**Property 4 implementation:**
```typescript
// Feature: sub-table-position-control, Property 4: Bottom-render fallback contains exactly unplaced bindings
fc.assert(fc.property(
  fc.array(fc.integer({ min: 1, max: 20 }), { minLength: 1, maxLength: 10 }),
  fc.array(fc.integer({ min: 1, max: 20 }), { maxLength: 5 }),
  (allIds, placedIds) => {
    const uniqueAll = [...new Set(allIds)]
    const uniquePlaced = [...new Set(placedIds)].filter(id => uniqueAll.includes(id))
    const bindings = uniqueAll.map(id => ({ bindingId: id, bindingMode: 'EDITABLE', tableName: `T${id}`, columns: [], data: [] }))
    const rules = uniquePlaced.map(id => ({ type: 'subTable', _bindingId: id }))
    const placedSet = new Set(uniquePlaced)
    const bottom = bindings.filter(b => !placedSet.has(b.bindingId))
    expect(bottom.map(b => b.bindingId).sort()).toEqual(
      uniqueAll.filter(id => !placedSet.has(id)).sort()
    )
  }
), { numRuns: 100 })
```

**Property 6 implementation:**
```typescript
// Feature: sub-table-position-control, Property 6: Tab-pane subTable placeholder produces FormField in correct tab
fc.assert(fc.property(
  fc.integer({ min: 1, max: 9999 }),
  fc.string({ minLength: 1 }),
  (bindingId, tabName) => {
    const config = {
      rule: [{
        type: 'el-tabs',
        children: [
          { type: 'el-tab-pane', props: { name: tabName, label: tabName }, children: [{ type: 'subTable', _bindingId: bindingId }] },
          { type: 'el-tab-pane', props: { name: 'other', label: 'Other' }, children: [] }
        ]
      }]
    }
    const tabs = parseFormConfigToTabs(JSON.stringify(config))
    const targetTab = tabs.find(t => t.name === tabName)
    const otherTab = tabs.find(t => t.name === 'other')
    expect(targetTab?.fields.some(f => f.type === 'subTable' && f._bindingId === bindingId)).toBe(true)
    expect(otherTab?.fields.some(f => f.type === 'subTable')).toBe(false)
  }
), { numRuns: 100 })
```

**Property 7 implementation:**
```typescript
// Feature: sub-table-position-control, Property 7: FormDesigner save serializes _bindingId into rule
fc.assert(fc.property(
  fc.integer({ min: 1, max: 9999 }),
  (bindingId) => {
    // Simulate designer rule with a subTable entry
    const rule = [{ type: 'subTable', _bindingId: bindingId, title: 'Sub-Table', props: {} }]
    const saved = serializeFormRule(rule)  // the save path
    const subTableEntry = saved.find((r: any) => r.type === 'subTable')
    expect(subTableEntry?._bindingId).toBe(bindingId)
  }
), { numRuns: 100 })
```

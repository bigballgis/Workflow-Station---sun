<script setup lang="ts">
/**
 * Renders a flat FormField list with parity to FormRenderer / Designer preview:
 * subTable → SubTableField, card → recursive, primitives → FieldRenderer.
 * Used by SubTableInlineForm and SubTableField Link Form dialog (read-only or editable).
 */
import FieldRenderer from './FieldRenderer.vue'
import SubTableField from './SubTableField.vue'
import { computed, provide, ref, watch } from 'vue'
import type { FormField } from './formRendererHelpers'
import {
  filterLinkOnlyStandaloneSubTableFields,
  isDisplayOnlyLayoutField,
  mergeNestedSubTableRowsIntoSto,
} from './formRendererHelpers'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/shared'
import { createLookupCascadeHandlers } from '@/composables/formRenderer/useFormLookupCascade'
import { INLINE_LOOKUP_CASCADE_CTX } from '@/composables/formRenderer/inlineFormLookupCascadeContext'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

export interface PortalSubTableBindingLite {
  bindingId: number
  tableName?: string
  physicalTableName?: string
  tableId?: number | null
  columns: Array<{ field: string; label: string; type?: string; props?: Record<string, unknown> }>
  /** Form-design canvas columns for the Add/Edit dialog. */
  dialogColumns?: Array<{ field: string; label: string; type?: string; props?: Record<string, unknown> }>
  /** This binding's own form-design fields — nested subTable widgets render inside its Add/Edit dialog. */
  formFields?: FormField[]
  /** Sub-form Form Design options — Add/Edit dialog Form-level onCreated / onMounted. */
  formOptions?: Record<string, unknown> | null
  /** BPMN-derived MI assignment contract; absent means no Assignment Mode behavior. */
  assignmentConfig?: AssignmentConfig
  data: unknown[]
  primaryKeyFields?: string[]
  /** Field FK/PK metadata from tableBindings — drives auto PK allocation and FK seeding. */
  fieldDefinitions?: BindingFieldDefinition[]
}

const props = withDefaults(
  defineProps<{
    fields: FormField[]
    model: Record<string, unknown>
    readonly?: boolean
    editable?: boolean
    subTableBindings?: PortalSubTableBindingLite[]
    linkedSubTableBindings?: PortalSubTableBindingLite[]
    /** Parent MI / link-form row — nested {@code __subTables__} resolved from here first. */
    parentRow?: Record<string, unknown> | null
    showLinkFormDialogFooter?: boolean
    compactLookupCells?: boolean
    /** My Request: omit link-form target sub-tables from inline / modal field lists. */
    suppressLinkOnlyStandaloneSubTables?: boolean
    /** Inside fcRow — render fcCol children as grid columns. */
    rowColumns?: boolean
    /** Inside fcCol — stack fields vertically. */
    inColumn?: boolean
    /**
     * FK/PK runtime context of the row being edited here. Nested sub-tables need it to allocate
     * their own auto primary key and to seed the structural FK back to this row.
     */
    hostTableId?: number | null
    hostFieldDefinitions?: BindingFieldDefinition[]
    hostFunctionUnitId?: string
    hostTaskId?: string
    hostPrimaryFormData?: Record<string, unknown>
    hostPrimaryTableId?: number | null
    /**
     * Event-runtime visibility (form-below-table / dialog scripts).
     * Unset → render every leaf (Link Form dialog unchanged).
     */
    isFieldVisible?: (fieldKey: string) => boolean
  }>(),
  {
    readonly: false,
    editable: false,
    showLinkFormDialogFooter: false,
    compactLookupCells: false,
    suppressLinkOnlyStandaloneSubTables: false,
    rowColumns: false,
    inColumn: false,
  },
)

const emit = defineEmits<{
  (e: 'update:field', key: string, value: unknown): void
  (e: 'field-blur', key: string): void
}>()

defineOptions({ name: 'PortalFormFields' })

const rowModelRef = ref<Record<string, unknown>>({})
const inlineLookupSelectedData = ref<Record<string, Record<string, unknown>>>({})

watch(
  () => props.model,
  (model) => {
    rowModelRef.value = model != null && typeof model === 'object' ? { ...model } : {}
  },
  { immediate: true, deep: true },
)

const displayFields = computed(() => {
  if (!props.suppressLinkOnlyStandaloneSubTables) return props.fields
  const pool = [...(props.linkedSubTableBindings ?? []), ...(props.subTableBindings ?? [])]
  return filterLinkOnlyStandaloneSubTableFields(props.fields, pool, [])
})

function resolveBinding(bindingId?: number): PortalSubTableBindingLite | undefined {
  if (bindingId == null) return undefined
  const pools = [...(props.linkedSubTableBindings ?? []), ...(props.subTableBindings ?? [])]
  return pools.find(b => Number(b.bindingId) === Number(bindingId))
}

function resolveSubTableRows(binding: PortalSubTableBindingLite): unknown[] {
  // Model first: it carries local __subTables__ edits before the host round-trips them
  // into parentRow (SubTableInlineForm rowModel vs. currentRow).
  for (const parent of [props.model, props.parentRow]) {
    if (!parent || typeof parent !== 'object') continue
    const nested = pullNestedRowsForBindingFromParentRows(
      {
        bindingId: binding.bindingId,
        tableName: binding.tableName ?? '',
        physicalTableName: binding.physicalTableName,
        tableId: binding.tableId ?? null,
      },
      [parent],
    )
    if (nested.length > 0) return nested
  }
  return Array.isArray(binding.data) ? binding.data : []
}

function isSubTableEditable(): boolean {
  return props.editable && !props.readonly
}

function onFieldUpdate(key: string, val: unknown) {
  emit('update:field', key, val)
}

function onFieldBlur(key: string) {
  emit('field-blur', key)
}

const LAYOUT_CONTAINER_TYPES = new Set([
  'subTable',
  'tabs',
  'row',
  'col',
  'collapse',
  'card',
  'miAssignment',
  'recordNote',
])

/** Layout containers stay mounted so scripts can reveal hidden children in place. */
function shouldRenderLeafField(field: FormField): boolean {
  if (isDisplayOnlyLayoutField(field)) return true
  if (LAYOUT_CONTAINER_TYPES.has(field.type)) return true
  if (!props.isFieldVisible) return true
  return props.isFieldVisible(field.key)
}

const inlineLookupCascade = createLookupCascadeHandlers({
  allFields: () => props.fields,
  formData: rowModelRef,
  lookupSelectedData: inlineLookupSelectedData,
  onFieldChange: (key, value) => onFieldUpdate(key, value),
})

provide(INLINE_LOOKUP_CASCADE_CTX, inlineLookupCascade)

/**
 * Nested sub-table rows changed (add/edit/delete in SubTableField). Persist them under the
 * host row's `__subTables__` and emit as a field update so the host (SubTableInlineForm →
 * handleInlineFormUpdate, or the Link Form dialog → linkedFormData) carries them to save.
 */
function onNestedSubTableRowsUpdate(field: FormField, rows: unknown[]) {
  const binding = resolveBinding(field._bindingId)
  if (!binding) return
  const sto = mergeNestedSubTableRowsIntoSto(
    [props.parentRow, props.model],
    { bindingId: binding.bindingId, tableName: binding.tableName },
    rows,
  )
  // The host mirrors these onto the nested binding's own top-level slice (SubTableInlineForm →
  // handleInlineFormUpdate → syncNestedSubTableBindings); that flat slice is what wins on save.
  emit('update:field', '__subTables__', sto)
}

/** Host row's own table joins the ancestor pool so a nested FK to it can be auto-filled. */
const nestedParentTablesById = computed(() => {
  if (props.hostTableId == null || !props.hostFieldDefinitions?.length) return undefined
  return { [Number(props.hostTableId)]: { fieldDefinitions: props.hostFieldDefinitions } }
})

/**
 * Saving a nested row forces this row's auto PK to be allocated early (the child's FK needs it).
 * Adopt it as a field update so the host persists the row under the key the child references.
 */
function onNestedParentRowPatch(patch: Record<string, unknown>) {
  for (const [key, value] of Object.entries(patch)) {
    if (key === '__subTables__') continue
    if (value == null || String(value).trim() === '') continue
    const current = props.model?.[key]
    if (current != null && String(current).trim() !== '') continue
    emit('update:field', key, value)
  }
}
</script>

<template>
  <template
    v-for="field in displayFields"
    :key="field.key"
  >
    <el-col
      v-if="field.type === 'subTable'"
      :span="field.span || 24"
      style="padding: 0;"
    >
      <SubTableField
        v-if="resolveBinding(field._bindingId)"
        :title="resolveBinding(field._bindingId)!.tableName || ''"
        :columns="resolveBinding(field._bindingId)!.columns"
        :dialog-columns="resolveBinding(field._bindingId)!.dialogColumns"
        :form-fields="resolveBinding(field._bindingId)!.formFields"
        :form-options="resolveBinding(field._bindingId)!.formOptions"
        :assignment-config="resolveBinding(field._bindingId)!.assignmentConfig"
        :model-value="resolveSubTableRows(resolveBinding(field._bindingId)!)"
        :editable="isSubTableEditable()"
        :allow-add="field.allowAdd"
        :allow-edit="field.allowEdit"
        :allow-delete="field.allowDelete"
        :linked-sub-table-bindings="linkedSubTableBindings ?? subTableBindings"
        :show-link-form-dialog-footer="showLinkFormDialogFooter"
        :compact-lookup-cells="compactLookupCells"
        :primary-key-fields="resolveBinding(field._bindingId)?.primaryKeyFields"
        :table-id="resolveBinding(field._bindingId)?.tableId ?? null"
        :field-definitions="resolveBinding(field._bindingId)?.fieldDefinitions"
        :function-unit-id="hostFunctionUnitId"
        :task-id="hostTaskId"
        :parent-row="model"
        :parent-table-id="hostTableId ?? null"
        :parent-tables-by-id="nestedParentTablesById"
        :primary-form-data="hostPrimaryFormData"
        :primary-table-id="hostPrimaryTableId ?? null"
        style="margin-bottom: 16px;"
        @update:model-value="(rows: any[]) => onNestedSubTableRowsUpdate(field, rows)"
        @update:parent-row="onNestedParentRowPatch"
      />
    </el-col>
    <el-col
      v-else-if="field.type === 'tabs' && field.tabs?.length"
      :span="24"
    >
      <el-tabs class="portal-form-nested-tabs">
        <el-tab-pane
          v-for="(tab, tabIdx) in field.tabs"
          :key="`${field.key}-tab-${tabIdx}-${String(tab.name)}`"
          :label="tab.label"
          :name="tab.name"
        >
          <PortalFormFields
            :fields="tab.fields || []"
            :model="model"
            :readonly="readonly"
            :editable="editable"
            :sub-table-bindings="subTableBindings"
            :linked-sub-table-bindings="linkedSubTableBindings"
            :parent-row="parentRow"
            :show-link-form-dialog-footer="showLinkFormDialogFooter"
            :compact-lookup-cells="compactLookupCells"
            :is-field-visible="isFieldVisible"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @field-blur="onFieldBlur"
          />
        </el-tab-pane>
      </el-tabs>
    </el-col>
    <el-col
      v-else-if="isDisplayOnlyLayoutField(field)"
      :span="field.span || 24"
      class="portal-form-display-only"
    >
      <FieldRenderer
        :field="field"
        :model-value="model[field.key]"
        :form-data="model"
        :readonly="true"
      />
    </el-col>
    <el-col
      v-else-if="field.type === 'row'"
      :span="24"
    >
      <el-row :gutter="field.gutter ?? 20">
        <PortalFormFields
          :fields="field.children || []"
          :model="model"
          :readonly="readonly"
          :editable="editable"
          :sub-table-bindings="subTableBindings"
          :linked-sub-table-bindings="linkedSubTableBindings"
          :parent-row="parentRow"
          :show-link-form-dialog-footer="showLinkFormDialogFooter"
          :compact-lookup-cells="compactLookupCells"
          :is-field-visible="isFieldVisible"
          row-columns
          @update:field="(k, v) => onFieldUpdate(k, v)"
          @field-blur="onFieldBlur"
        />
      </el-row>
    </el-col>
    <el-col
      v-else-if="rowColumns && field.type === 'col'"
      :span="field.span || 12"
    >
      <PortalFormFields
        :fields="field.children || []"
        :model="model"
        :readonly="readonly"
        :editable="editable"
        :sub-table-bindings="subTableBindings"
        :linked-sub-table-bindings="linkedSubTableBindings"
        :parent-row="parentRow"
        :show-link-form-dialog-footer="showLinkFormDialogFooter"
        :compact-lookup-cells="compactLookupCells"
        :is-field-visible="isFieldVisible"
        in-column
        @update:field="(k, v) => onFieldUpdate(k, v)"
        @field-blur="onFieldBlur"
      />
    </el-col>
    <el-col
      v-else-if="field.type === 'collapse' && field.collapsePanels?.length"
      :span="24"
    >
      <el-collapse class="portal-form-nested-collapse">
        <el-collapse-item
          v-for="(panel, panelIdx) in field.collapsePanels"
          :key="`${field.key}-collapse-${panelIdx}-${String(panel.name)}`"
          :title="panel.label"
          :name="panel.name"
        >
          <PortalFormFields
            :fields="panel.fields || []"
            :model="model"
            :readonly="readonly"
            :editable="editable"
            :sub-table-bindings="subTableBindings"
            :linked-sub-table-bindings="linkedSubTableBindings"
            :parent-row="parentRow"
            :show-link-form-dialog-footer="showLinkFormDialogFooter"
            :compact-lookup-cells="compactLookupCells"
            :is-field-visible="isFieldVisible"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @field-blur="onFieldBlur"
          />
        </el-collapse-item>
      </el-collapse>
    </el-col>
    <el-col
      v-else-if="field.type === 'card'"
      :span="field.span || 24"
    >
      <el-card
        shadow="never"
        class="portal-form-fields-card"
      >
        <template
          v-if="field.label"
          #header
        >
          <span>{{ field.label }}</span>
        </template>
        <el-row :gutter="20">
          <PortalFormFields
            :fields="field.children || []"
            :model="model"
            :readonly="readonly"
            :editable="editable"
            :sub-table-bindings="subTableBindings"
            :linked-sub-table-bindings="linkedSubTableBindings"
            :parent-row="parentRow"
            :show-link-form-dialog-footer="showLinkFormDialogFooter"
            :compact-lookup-cells="compactLookupCells"
            :is-field-visible="isFieldVisible"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @field-blur="onFieldBlur"
          />
        </el-row>
      </el-card>
    </el-col>
    <el-col
      v-else-if="!inColumn && shouldRenderLeafField(field)"
      :span="field.span || 24"
    >
      <el-form-item
        :label="field.label"
        :prop="field.key"
        :required="field.required"
      >
        <FieldRenderer
          :field="field"
          :model-value="model[field.key]"
          :form-data="model"
          :readonly="readonly || field.readonly === true || !editable"
          @update:model-value="(val: unknown) => onFieldUpdate(field.key, val)"
          @field-blur="onFieldBlur"
        />
      </el-form-item>
    </el-col>
    <div
      v-else-if="inColumn && shouldRenderLeafField(field)"
      class="portal-form-col-field"
    >
      <el-form-item
        :label="field.label"
        :prop="field.key"
        :required="field.required"
      >
        <FieldRenderer
          :field="field"
          :model-value="model[field.key]"
          :form-data="model"
          :readonly="readonly || field.readonly === true || !editable"
          @update:model-value="(val: unknown) => onFieldUpdate(field.key, val)"
          @field-blur="onFieldBlur"
        />
      </el-form-item>
    </div>
  </template>
</template>

<style scoped>
.portal-form-fields-card {
  width: 100%;
  margin-bottom: 10px;
}

.portal-form-col-field {
  width: 100%;
}

.portal-form-display-only {
  margin-bottom: 8px;
}

.portal-form-nested-tabs {
  width: 100%;
  margin-bottom: 12px;
}

.portal-form-nested-collapse {
  width: 100%;
  margin-bottom: 12px;
}
</style>

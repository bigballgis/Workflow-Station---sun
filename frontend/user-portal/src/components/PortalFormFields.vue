<script setup lang="ts">
/**
 * Renders a flat FormField list with parity to FormRenderer / Designer preview:
 * subTable → SubTableField, card → recursive, primitives → FieldRenderer.
 * Used by SubTableInlineForm and SubTableField Link Form dialog (read-only or editable).
 */
import FieldRenderer from './FieldRenderer.vue'
import SubTableField from './SubTableField.vue'
import { computed } from 'vue'
import type { FormField } from './formRendererHelpers'
import {
  filterLinkOnlyStandaloneSubTableFields,
  isDisplayOnlyLayoutField,
  mergeNestedSubTableRowsIntoSto,
} from './formRendererHelpers'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/shared'

export interface PortalSubTableBindingLite {
  bindingId: number
  tableName?: string
  physicalTableName?: string
  tableId?: number | null
  columns: Array<{ field: string; label: string; type?: string; props?: Record<string, unknown> }>
  data: unknown[]
  primaryKeyFields?: string[]
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
}>()

defineOptions({ name: 'PortalFormFields' })

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
  emit('update:field', '__subTables__', sto)
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
        :model-value="resolveSubTableRows(resolveBinding(field._bindingId)!)"
        :editable="isSubTableEditable()"
        :allow-add="field.allowAdd"
        :allow-edit="field.allowEdit"
        :allow-delete="field.allowDelete"
        :linked-sub-table-bindings="linkedSubTableBindings ?? subTableBindings"
        :show-link-form-dialog-footer="showLinkFormDialogFooter"
        :compact-lookup-cells="compactLookupCells"
        :primary-key-fields="resolveBinding(field._bindingId)?.primaryKeyFields"
        style="margin-bottom: 16px;"
        @update:model-value="(rows: any[]) => onNestedSubTableRowsUpdate(field, rows)"
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
            @update:field="(k, v) => onFieldUpdate(k, v)"
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
          row-columns
          @update:field="(k, v) => onFieldUpdate(k, v)"
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
        in-column
        @update:field="(k, v) => onFieldUpdate(k, v)"
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
            @update:field="(k, v) => onFieldUpdate(k, v)"
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
            @update:field="(k, v) => onFieldUpdate(k, v)"
          />
        </el-row>
      </el-card>
    </el-col>
    <el-col
      v-else-if="!inColumn"
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
        />
      </el-form-item>
    </el-col>
    <div
      v-else
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
        />
      </el-form-item>
    </div>
  </template>
</template>

<style scoped>
.portal-form-fields-card {
  margin-bottom: 16px;
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

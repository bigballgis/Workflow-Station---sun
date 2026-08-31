<script setup lang="ts">
/**
 * Renders a flat FormField list with parity to FormRenderer / Designer preview:
 * subTable → SubTableField, card → recursive, primitives → FieldRenderer.
 * Used by SubTableInlineForm and SubTableField Link Form dialog (read-only or editable).
 */
import FieldRenderer from './FieldRenderer.vue'
import SubTableField from './SubTableField.vue'
import { computed, defineAsyncComponent, provide, ref, watch } from 'vue'
import type { FormField } from './formRendererHelpers'
import {
  filterLinkOnlyStandaloneSubTableFields,
  isDisplayOnlyLayoutField,
  mergeNestedSubTableRowsIntoSto,
} from './formRendererHelpers'
import {
  pullNestedRowsForBindingFromParentRows,
  scopeLinkChildRowsToMiHostRow,
  hostRowIsMiParticipant,
  isMiParticipantScopedSubTableBinding,
} from '@/composables/tasks/shared'
import { createLookupCascadeHandlers } from '@/composables/formRenderer/useFormLookupCascade'
import { INLINE_LOOKUP_CASCADE_CTX } from '@/composables/formRenderer/inlineFormLookupCascadeContext'
import { useInlineSubFormComponent } from '@/composables/formRenderer/useInlineSubFormComponent'
import type { SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

// Lazily required to avoid a module-load cycle: SubTableInlineForm imports PortalFormFields
// itself (nested subTable widgets inside the inline form use PortalFormFields recursively).
const SubTableInlineForm = defineAsyncComponent(() => import('./SubTableInlineForm.vue'))

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
  /** SUB / ACTION / RELATED — ACTION bindings render permanently read-only regardless of allow* props. */
  bindingType?: string | null
  /** EDITABLE / READONLY, from Table Design — gates whether the inline form can be edited at all. */
  bindingMode?: string | null
  /**
   * FK column linking a row back to its parent. Distinguishes a participant-scoped child table
   * (People.sub_task_id / FK `id` → the MI participant row) from a shared process-level one
   * (attachment.main_id → the main record), which decides whether rows get scoped to the host row.
   */
  foreignKeyField?: string | null
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
     * inlineSubForm bindingIds already resolved along this render path (this component's own
     * ancestor chain, not just its direct parent) — accumulated by
     * {@link resolveInlineSubFormFields} and threaded back down when an inlineSubForm field is
     * itself nested inside another inlineSubForm's own resolved fields, so an indirect cycle
     * (A embeds one pointing at B, whose form embeds one pointing back at A) is pruned instead of
     * recursing forever. Absent/undefined at the top level (fresh render, nothing visited yet).
     */
    visitedInlineSubFormBindingIds?: ReadonlySet<number>
    /**
     * Task-node field permissions (`TaskFormData.fieldPermissions`); composite `${bindingId}:${fieldName}`
     * entries mark individual inlineSubForm fields READONLY, same as SubTableField's Add/Edit dialog.
     * Absent here (undefined) → no field-level enforcement, same as if the FU never configured any.
     */
    fieldPermissions?: Record<string, string> | null
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

/**
 * Rows for a sub-table rendered inside this form.
 *
 * <p>Rows nested under the row this form edits (`__subTables__`) are authoritative. Failing that,
 * the binding's own flat `data` is used — but ONLY when this form is not editing a single MI
 * participant row.
 *
 * <p><b>Why the flat fallback is cut off for an MI participant host.</b> `binding.data` is the
 * cross-participant pool: it holds every sub-task's rows for that table. For a sub-table nested
 * inside an Inline Form bound to an MI collection (FU 50005's Sub task form: Inline Form on
 * `subtable` containing a nested `People` grid), the form edits exactly ONE participant, so
 * "this participant has no rows of their own" must render an empty grid — never the pool. Falling
 * through showed, and allowed editing of, a sibling sub-task's rows.
 *
 * <p>The fallback is kept for every other host, where it is the only row source and removing it
 * renders those grids empty: plain link-form / nested-dialog hosts (nothing is nested under them —
 * see {@code useSubTableLinkFormDialog}'s own `binding.data`-based resolution) and shared
 * process-level tables whose rows belong to the main record rather than a participant
 * (e.g. `attachment.main_id`), which every sub-task is supposed to see.
 */
function resolveSubTableRows(binding: PortalSubTableBindingLite): unknown[] {
  const hostRow = (props.model && typeof props.model === 'object' ? props.model : props.parentRow) ?? null
  const participantScoped =
    isMiParticipantScopedSubTableBinding(binding) && hostRowIsMiParticipant(hostRow)
  const scope = (rows: unknown[]) =>
    participantScoped ? scopeLinkChildRowsToMiHostRow(hostRow, rows) : rows

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
    if (nested.length > 0) {
      const scoped = scope(nested)
      // A nested slice that scopes to nothing held only sibling rows — keep looking rather than
      // reporting "this participant has rows" falsely.
      if (scoped.length > 0) return scoped
    }
  }
  // MI participant host: no fallback. Nothing nested means this participant owns no rows.
  if (participantScoped) return []
  return Array.isArray(binding.data) ? binding.data : []
}

function isSubTableEditable(): boolean {
  return props.editable && !props.readonly
}

/**
 * Ancestry to pass to a nested inlineSubForm's own PortalFormFields render: this component's
 * incoming visited set plus the binding the field currently being rendered points at (added here
 * rather than left to the child, so the child's OWN self-check also sees the parent already
 * on the path — matches resolveInlineSubFormFields' own accumulation behavior).
 */
function nextVisitedInlineSubFormBindingIds(bindingId: number | undefined): ReadonlySet<number> {
  const next = new Set(props.visitedInlineSubFormBindingIds ?? [])
  if (bindingId != null) next.add(Number(bindingId))
  return next
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
 * host row's `__subTables__` and emit as a field update so the host (the `inlineSubForm`
 * widget's handleInlineSubFormUpdate, or the Link Form dialog → linkedFormData) carries them
 * to save.
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

/**
 * Merges an inlineSubForm edit back into the bound sub-table's rows and persists them the
 * same way onNestedSubTableRowsUpdate does — the nested-in-a-dialog and inline-on-the-canvas
 * paths both ultimately write through __subTables__ on the same host row.
 */
function onInlineSubFormRowUpdate(bindingId: number, rows: unknown[]) {
  const binding = resolveBinding(bindingId)
  if (!binding) return
  const sto = mergeNestedSubTableRowsIntoSto(
    [props.parentRow, props.model],
    { bindingId: binding.bindingId, tableName: binding.tableName },
    rows,
  )
  emit('update:field', '__subTables__', sto)
}

// PortalSubTableBindingLite and SubTableBinding are intentionally loose sibling types over the
// same upstream binding data (see useSubTableBindings.ts's own comment) — useInlineSubFormComponent
// only reads the overlapping subset (bindingId/tableName/formFields/fieldDefinitions/data/bindingMode).
const inlineSubForm = useInlineSubFormComponent({
  readonly: () => props.readonly || !props.editable,
  resolveBinding: (id) => resolveBinding(id) as unknown as SubTableBinding | undefined,
  isBindingModeEditable: (mode) => String(mode ?? '').trim().toUpperCase() === 'EDITABLE',
  getSavedRowsForBinding: (binding) =>
    resolveSubTableRows(binding as unknown as PortalSubTableBindingLite) as Record<string, unknown>[],
  handleSubTableUpdate: (bindingId, rows) => onInlineSubFormRowUpdate(bindingId, rows),
  fieldPermissions: () => props.fieldPermissions,
})

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
        :binding-type="resolveBinding(field._bindingId)?.bindingType"
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
      v-else-if="field.type === 'inlineSubForm'"
      :span="24"
      style="padding: 0;"
    >
      <SubTableInlineForm
        v-if="resolveBinding(field._bindingId)"
        :title="inlineSubForm.resolveInlineSubFormTitle(field)"
        :fields="inlineSubForm.resolveInlineSubFormFields(field, visitedInlineSubFormBindingIds)"
        :current-row="inlineSubForm.resolveInlineSubFormRow(field)"
        :readonly="inlineSubForm.inlineSubFormReadonly(field)"
        hide-save-button
        framed
        :bordered="false"
        :sub-table-bindings="subTableBindings"
        :linked-sub-table-bindings="linkedSubTableBindings ?? subTableBindings"
        :host-table-id="resolveBinding(field._bindingId)?.tableId ?? null"
        :host-field-definitions="resolveBinding(field._bindingId)?.fieldDefinitions"
        :host-function-unit-id="hostFunctionUnitId"
        :host-task-id="hostTaskId"
        :host-primary-form-data="hostPrimaryFormData"
        :host-primary-table-id="hostPrimaryTableId ?? null"
        :visited-inline-sub-form-binding-ids="nextVisitedInlineSubFormBindingIds(field._bindingId)"
        :field-permissions="fieldPermissions"
        :form-options="resolveBinding(field._bindingId)!.formOptions"
        :dialog-columns="resolveBinding(field._bindingId)!.dialogColumns"
        style="margin-bottom: 16px;"
        @update:row="(row: Record<string, any>) => inlineSubForm.handleInlineSubFormUpdate(field, row)"
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
            :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
            :field-permissions="fieldPermissions"
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
          :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
          :field-permissions="fieldPermissions"
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
        :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
        :field-permissions="fieldPermissions"
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
            :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
            :field-permissions="fieldPermissions"
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
            :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
            :field-permissions="fieldPermissions"
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

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
  miChildFkConfigOfBinding,
  mergeSubTableRowsByRowId,
  resolveMiChildStructuralParentFk,
} from '@/composables/tasks/shared'
import { bindingDeclaresMiParticipantRow } from '@/composables/tasks/miBindingKindFromConfig'
import { createLookupCascadeHandlers } from '@/composables/formRenderer/useFormLookupCascade'
import { INLINE_LOOKUP_CASCADE_CTX } from '@/composables/formRenderer/inlineFormLookupCascadeContext'
import { useInlineSubFormComponent } from '@/composables/formRenderer/useInlineSubFormComponent'
import type { SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import { isAssignmentConfigured, type AssignmentConfig } from '@/utils/miAssignmentConfig'
import MiAssignmentModeBlock from './MiAssignmentModeBlock.vue'

// Lazily required to avoid a module-load cycle: SubTableInlineForm imports PortalFormFields
// itself (nested subTable widgets inside the inline form use PortalFormFields recursively).
const SubTableInlineForm = defineAsyncComponent(() => import('./SubTableInlineForm.vue'))

export interface PortalSubTableBindingLite {
  bindingId: number
  tableName?: string
  designerTableName?: string
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
   * `structuralFk` / `miParticipantRow`, from the designer's Manage Table Bindings — a DIFFERENT
   * field from {@link bindingMode} (which is only EDITABLE/READONLY). It tells a nested sub-table
   * how to link a new row back to its parent, so PK allocation and FK seeding need it.
   */
  bindingLinkMode?: string | null
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
    /**
     * Event-runtime required overlay. Unset → designer `field.required` only.
     */
    isFieldRequired?: (field: FormField) => boolean
    /**
     * BPMN-derived MI assignment contract for the sub-table this form edits; absent means no
     * Assignment Mode behavior. Supplies the block's CONTENT (which modes, which fields) while
     * the designer's `miAssignment` marker decides where it renders — same split as
     * SubTableAddDialog, so the Link Form dialog and the Inline Form widget match the grid's
     * Add/Edit dialog and DW Form Preview.
     */
    assignmentConfig?: AssignmentConfig
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
  /**
   * 子表行集发生变化（增 / 改 / 删）。
   *
   * <p><b>为什么必须有这个 emit。</b>这个组件此前只把行集写进**宿主行的 `__subTables__`**，
   * 从不通知外层，于是 `binding.data` 永远不会更新 —— 保存时读的是 `binding.data`，
   * 用户在 inline 表格里的删除因此从未进入 payload（实测 task 9c46d613：删除写进了
   * Participants row 0，而 `syncMainSubTableRows` 触发次数恒为 0）。
   *
   * <p>补上它之后两份数据由同一次事件同时更新，不再需要「哪一份是权威」的猜测。
   */
  (e: 'update:sub-table-data', bindingId: number, rows: unknown[]): void
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
 * 宿主行的参与者标识 —— 取自 **MI collection 的设计器主键**（`collectionPrimaryKeyFields`），
 * 不猜列名。解析不出返回 null，调用方据此放弃补行（保守侧）。
 */
function resolveMiHostParticipantKey(hostRow: Record<string, unknown> | null): string | null {
  if (!hostRow) return null
  for (const pk of miKindContext.value.collectionPrimaryKeyFields ?? []) {
    const name = String(pk ?? '').trim()
    if (!name) continue
    const v = hostRow[name]
    if (v == null) continue
    const s = String(v).trim()
    if (s !== '') return s
  }
  return null
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
    isMiParticipantScopedSubTableBinding(binding, miKindContext.value)
    && hostRowIsMiParticipant(hostRow, miKindContext.value.collectionPrimaryKeyFields)
  const scope = (rows: unknown[]) =>
    participantScoped
      ? scopeLinkChildRowsToMiHostRow(
          hostRow,
          rows,
          miChildFkConfigOfBinding(binding as never),
          miKindContext.value.collectionPrimaryKeyFields,
        )
      : rows

  // Model first: it carries local __subTables__ edits before the host round-trips them
  // into parentRow (SubTableInlineForm rowModel vs. currentRow).
  for (const parent of [props.model, props.parentRow]) {
    if (!parent || typeof parent !== 'object') continue
    const nested = pullNestedRowsForBindingFromParentRows(
      {
        bindingId: binding.bindingId,
        tableName: binding.tableName ?? '',
        designerTableName: binding.designerTableName,
        tableId: binding.tableId ?? null,
      },
      [parent],
    )
    if (nested.length > 0) {
      const scoped = scope(nested)
      // A nested slice that scopes to nothing held only sibling rows — keep looking rather than
      // reporting "this participant has rows" falsely.
      if (scoped.length > 0) {
        // 嵌套那份是**派生缓存**，只在宿主行往返（保存 / 重新加载）时更新；`binding.data` 才是
        // 刚发生的编辑的第一现场。直接 return 嵌套会让"新增一行"在下一次渲染被打回原形 ——
        // 实测 `binding.data` 已有 2 行、嵌套仍是 1 行，表格于是永远只画 1 行
        //（ATM Correspondence 加不进第二条的直接原因）。
        //
        // **只补「明确属于本宿主行」的行**：判据是行上的结构外键实际指向当前宿主行，
        // 不是"scope 过滤后还剩下"。两者不等价 ——
        // scope 会保留尚未 seed 外键的行（新行还没 seed 时不能丢），而那种行同样可能是
        // **兄弟参与者**刚加的、也还没 seed 的行，靠身份区分不开。放进来就会串参与者，
        // 这正是 portalFormFieldsNestedSubTableMiScope 那两条用例锁定的行为。
        const hostKey = resolveMiHostParticipantKey(hostRow)
        const fkConfig = miChildFkConfigOfBinding(binding as never)
        const owned = hostKey == null
          ? []
          : (Array.isArray(binding.data) ? binding.data : []).filter(
              row =>
                row != null
                && typeof row === 'object'
                && resolveMiChildStructuralParentFk(row as Record<string, unknown>, fkConfig) === hostKey,
            )
        if (owned.length > scoped.length) {
          return mergeSubTableRowsByRowId(scoped, owned, binding.primaryKeyFields ?? null)
        }
        return scoped
      }
    }
  }
  // MI 参与者宿主：**不走兜底**。`binding.data` 是跨参与者的池子，且里面尚未 seed 外键的行
  // 与本参与者刚加的新行靠身份区分不开 —— 放行会把兄弟参与者的行显示进来。
  // 新增行的可见性由上面「按结构外键补本宿主行的行」解决，那条路径要求外键明确指向本宿主行。
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

function fieldShowsRequired(field: FormField): boolean {
  if (props.isFieldRequired) return props.isFieldRequired(field)
  return field.required === true
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
    {
      bindingId: binding.bindingId,
      tableName: binding.tableName,
      // 必须传设计器表名（以及关联表名），否则 key 会退化成展示名，和读取端分叉。
      designerTableName: binding.designerTableName,
      relationTableName: (binding as { relationTableName?: string | null }).relationTableName,
      relationTableId: (binding as { relationTableId?: number | null }).relationTableId,
    },
    rows,
  )
  emit('update:field', '__subTables__', sto)
  // 同一次编辑也要让外层更新 `binding.data`，否则保存时读到的是旧行集。
  emit('update:sub-table-data', Number(binding.bindingId), rows)
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
    {
      bindingId: binding.bindingId,
      tableName: binding.tableName,
      // 必须传设计器表名（以及关联表名），否则 key 会退化成展示名，和读取端分叉。
      designerTableName: binding.designerTableName,
      relationTableName: (binding as { relationTableName?: string | null }).relationTableName,
      relationTableId: (binding as { relationTableId?: number | null }).relationTableId,
    },
    rows,
  )
  emit('update:field', '__subTables__', sto)
  emit('update:sub-table-data', Number(binding.bindingId), rows)
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
  miKindContext: () => miKindContext.value,
})

/**
 * Binding 分类的配置上下文（见 {@code miBindingKindFromConfig}）。collection 的 tableId
 * 读自设计器 Link Mode = "MI Participant Row" 的那个 binding；主表用宿主行所属表。
 */
const miKindContext = computed(() => {
  const pool = [...(props.linkedSubTableBindings ?? []), ...(props.subTableBindings ?? [])]
  const collection = pool.find(b => bindingDeclaresMiParticipantRow(b as never))
  return {
    miCollectionTableId: (collection as { tableId?: number | null } | undefined)?.tableId ?? null,
    primaryTableId: props.hostTableId ?? null,
    // MI collection 的设计器主键：宿主行是不是参与者行、参与者 id 取哪一列，都按它判定。
    collectionPrimaryKeyFields:
      (collection as { primaryKeyFields?: string[] | null } | undefined)?.primaryKeyFields ?? null,
  }
})

/** Host row's own table joins the ancestor pool so a nested FK to it can be auto-filled. */
const nestedParentTablesById = computed(() => {
  if (props.hostTableId == null || !props.hostFieldDefinitions?.length) return undefined
  return { [Number(props.hostTableId)]: { fieldDefinitions: props.hostFieldDefinitions } }
})

/**
 * The designer's `miAssignment` marker owns the assignee / BU / role rules as its CHILDREN.
 * Only render the block when BPMN actually configured the contract — an unconfigured marker
 * must still render its children (flat, as before), never swallow them.
 */
const assignmentBlockConfigured = computed(() => isAssignmentConfigured(props.assignmentConfig))

/**
 * Switching mode blanks the other branch's values so a row never carries both a named
 * assignee and a role pool. Mirrors SubTableAddDialog's onAssignModeChange.
 */
function onAssignmentClearFields(fields: string[]) {
  for (const key of fields) onFieldUpdate(key, '')
}

/** Children the active mode hides — the marker's own subtree only. */
function assignmentVisibleChildren(children: FormField[] | undefined, hidden: Set<string>): FormField[] {
  return (children || []).filter(child => !hidden.has(child.key))
}

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
        :binding-link-mode="resolveBinding(field._bindingId)?.bindingLinkMode"
        :binding-foreign-key-field="resolveBinding(field._bindingId)?.foreignKeyField"
        :binding-id="field._bindingId"
        :field-permissions="fieldPermissions"
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
        :assignment-config="resolveBinding(field._bindingId)!.assignmentConfig"
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
            :is-field-required="isFieldRequired"
            :assignment-config="assignmentConfig"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
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
          :is-field-required="isFieldRequired"
          :assignment-config="assignmentConfig"
          row-columns
          @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
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
        :is-field-required="isFieldRequired"
        :assignment-config="assignmentConfig"
        in-column
        @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
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
            :is-field-required="isFieldRequired"
            :assignment-config="assignmentConfig"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
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
            :is-field-required="isFieldRequired"
            :assignment-config="assignmentConfig"
            @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
            @field-blur="onFieldBlur"
          />
        </el-row>
      </el-card>
    </el-col>
    <!-- Assignment Mode block: routing this row to a named person or to a role pool.
         The marker is a container whose children are the assignee / BU / role rules, so the
         block renders them inside its own frame — without this branch the marker fell through
         to the leaf renderer, drawing an empty label-less box while its children never
         rendered at all. Kept in parity with SubTableAddDialog and DW Form Preview. -->
    <el-col
      v-else-if="field.type === 'miAssignment' && !field.hidden && assignmentBlockConfigured"
      :span="24"
    >
      <MiAssignmentModeBlock
        :config="assignmentConfig"
        :row="model"
        :readonly="readonly || field.readonly === true || !editable"
        @clear-fields="onAssignmentClearFields"
      >
        <template #default="{ hiddenFields }">
          <el-row :gutter="20">
            <PortalFormFields
              :fields="assignmentVisibleChildren(field.children, hiddenFields)"
              :model="model"
              :readonly="readonly || field.readonly === true"
              :editable="editable && field.readonly !== true"
              :sub-table-bindings="subTableBindings"
              :linked-sub-table-bindings="linkedSubTableBindings"
              :parent-row="parentRow"
              :show-link-form-dialog-footer="showLinkFormDialogFooter"
              :compact-lookup-cells="compactLookupCells"
              :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
              :field-permissions="fieldPermissions"
              :is-field-visible="isFieldVisible"
              :is-field-required="isFieldRequired"
              :assignment-config="assignmentConfig"
              @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
              @field-blur="onFieldBlur"
            />
          </el-row>
        </template>
      </MiAssignmentModeBlock>
    </el-col>
    <!-- Designer "Hide" toggle on the block — the whole thing goes, the fields it owns
         included, matching dialogFormLayout's handling for the grid's Add/Edit dialog.
         Rendering the children here instead would leak the very pickers Hide removes. -->
    <template v-else-if="field.type === 'miAssignment' && field.hidden" />
    <!-- Marker present but BPMN configured no assignment contract (or the FU predates it):
         there is no block to draw, but the marker's children are ordinary fields and must
         still render — flat, exactly where the designer placed the container. Rendering
         nothing here is what made those fields vanish. -->
    <el-col
      v-else-if="field.type === 'miAssignment'"
      :span="24"
    >
      <el-row :gutter="20">
        <PortalFormFields
          :fields="field.children || []"
          :model="model"
          :readonly="readonly || field.readonly === true"
          :editable="editable && field.readonly !== true"
          :sub-table-bindings="subTableBindings"
          :linked-sub-table-bindings="linkedSubTableBindings"
          :parent-row="parentRow"
          :show-link-form-dialog-footer="showLinkFormDialogFooter"
          :compact-lookup-cells="compactLookupCells"
          :visited-inline-sub-form-binding-ids="visitedInlineSubFormBindingIds"
          :field-permissions="fieldPermissions"
          :is-field-visible="isFieldVisible"
          :is-field-required="isFieldRequired"
          :assignment-config="assignmentConfig"
          @update:field="(k, v) => onFieldUpdate(k, v)"
            @update:sub-table-data="(bid: number, rows: unknown[]) => emit('update:sub-table-data', bid, rows)"
          @field-blur="onFieldBlur"
        />
      </el-row>
    </el-col>
    <el-col
      v-else-if="!inColumn && shouldRenderLeafField(field)"
      :span="field.span || 24"
    >
      <el-form-item
        :label="field.label"
        :prop="field.key"
        :required="fieldShowsRequired(field)"
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
        :required="fieldShowsRequired(field)"
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

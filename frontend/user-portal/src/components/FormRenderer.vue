<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      class="form-readonly-surface"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :disabled="readonly"
      :size="size"
      :validate-on-rule-change="false"
    >
      <!-- Tab layout: render siblings outside tab panes in designer order -->
      <template v-if="hasTabs">
        <el-row
          v-if="fields.length > 0"
          :gutter="20"
          class="form-fields-before-tabs"
        >
          <FormRendererFields :fields="fields" />
        </el-row>
        <el-tabs
          v-model="activeTab"
          class="form-renderer-tabs"
        >
          <el-tab-pane
            v-for="(tab, tabIdx) in tabs"
            :key="`tab-${tabIdx}-${String(tab.name)}`"
            :label="tab.label"
            :name="tab.name"
          >
            <el-row :gutter="20">
              <FormRendererFields :fields="tab.fields" />
            </el-row>
          </el-tab-pane>
        </el-tabs>
        <el-row
          v-if="fieldsAfterTabs.length > 0"
          :gutter="20"
          class="form-fields-after-tabs"
        >
          <FormRendererFields :fields="fieldsAfterTabs" />
        </el-row>
      </template>

      <!-- Flat layout mode -->
      <template v-else>
        <el-row :gutter="20">
          <FormRendererFields :fields="fields" />
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide, reactive } from 'vue'
import { watchThrottled } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { isEqual } from 'lodash-es'
import { ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import FormRendererFields from './FormRendererFields.vue'
import { FORM_RENDERER_FIELDS_CTX } from './formRendererFieldsContext'
import { BusinessLogicEngine } from './businessLogicEngine'
import { userApi } from '@/api/user'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import type {
  FormField,
  FormTab,
  FormBusinessLogicConfig,
  PortalViewContext,
  SubTablePortalViews
} from './formRendererHelpers'
import {
  extractFieldsRecursive,
  flattenAllFormFieldSegments,
  isFormFieldReadonly,
  mergeSubTablePortalViewsForRuntime,
  resolveSubTableDisplayMode,
  shouldSuppressStandaloneSubTableInInitiatorRequest,
} from './formRendererHelpers'
import {
  mergeSubTableRowsByRowId,
  collectNestedChildRowsFromPeerBindings,
  pullNestedRowsForBindingFromParentRows,
  findMiIsolatedParentRow,
  pickMiLinkChildRowsForParent
} from '@/composables/tasks/shared'
import { createPortalFormApi, createFieldKeyResolver, runFormOnChangeHandler, type PortalFormVisibilityState } from '@/utils/formCreateEventRuntime'
import {
  collectFieldComponentEventsFromRules,
  runAllComponentHookEvents,
  runComponentFieldEvents,
} from '@/utils/formCreateComponentEvents'

export type { FormField, FormTab }

const { t } = useI18n()

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
interface SubTableBinding {
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: any[]
  data: any[]
  formFields?: FormField[]
  formOptions?: Record<string, any>
  /**
   * Per-binding portalViews loaded from form configJson.subTablePortalViews[bindingId].
   * Used as the fallback when a placed `subTable` rule node has no `props.portalViews`,
   * and as the primary source for unplaced bindings (e.g. sub-tables accessed only via Link Form).
   */
  portalViews?: Partial<import('./formRendererHelpers').SubTablePortalViews> | null
  /** dw_field_definitions PK columns (from admin tableBindings). */
  primaryKeyFields?: string[]
}

interface Props {
  fields: FormField[]
  tabs?: FormTab[]
  /** Canvas rules after `el-tabs` (designer siblings below tab widget). */
  fieldsAfterTabs?: FormField[]
  modelValue?: Record<string, any>
  readonly?: boolean
  /** When true, disables form fields driven by PRIMARY table binding READONLY mode.
   *  Does NOT affect sub-table editability (sub-tables use their own bindingMode). */
  primaryReadOnly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]
  linkedSubTableBindings?: SubTableBinding[]
  /** MI / diagram preview: row-picking heuristics only; does not override table binding editability. */
  previewSubTables?: boolean
  uploadUrl?: string
  // Task 7.2: BusinessLogicEngine config
  config?: FormBusinessLogicConfig
  // Task 7.5: Auto-save props
  functionUnitId?: string
  formId?: string
  // Task 16: Real-time sync props
  taskId?: string
  enableSubTablePolling?: boolean
  subTablePollingInterval?: number
  /** When false, hides the sub-table Assign button (only the "Assign Participants" task node allows assignment) */
  allowSubTableAssign?: boolean
  /** In MI todo mode, link-form Details should open blank instead of reusing row-level historical child data. */
  suppressLinkFormInitialData?: boolean
  /** Task To Do only: Link Form field-layout detail shows Cancel/Save (completed / My Request use header close only). */
  showLinkFormDialogFooter?: boolean
  /**
   * Portal view context — drives how subTable nodes are rendered based on their `portalViews` config:
   * - `assigneeTodo`: To Do detail page (办理人待办)
   * - `initiatorRequest`: My Request / process detail page (发起人我的申请)
   * Defaults to `assigneeTodo` for safety; consumers should pass the value matching their route.
   */
  viewContext?: PortalViewContext
  /**
   * When `viewContext` is `initiatorRequest`, Completed Tasks snapshot treats task-status rows
   * like `applicationDetail` (only COMPLETED rows count for Details visibility heuristics).
   */
  initiatorSnapshotMode?: boolean
  /**
   * Current MI participant row id (typically `variables._currentItem.rowId`). When set, the
   * inline form-below-table binds to that row; otherwise it falls back to the first sub-table row.
   */
  currentMiRowId?: number | string | null
  /** Binding ids declared on this form's tableBindings (excludes merge-only link targets). */
  nativeSubTableBindingIds?: number[]
  /** Designer configJson — used to resolve link-form targets from {@code subListViews}. */
  formConfig?: Record<string, unknown> | null
  /** form-create designer options (Form event onChange, labelWidth, etc.). */
  formOptions?: Record<string, unknown> | null
  /** Raw form-create rule tree (for per-component on/_hook events). Falls back to formConfig.rule. */
  formCreateRules?: unknown[] | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  fieldsAfterTabs: () => [],
  readonly: false,
  primaryReadOnly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  size: 'default',
  subTableBindings: () => [],
  linkedSubTableBindings: undefined,
  previewSubTables: false,
  allowSubTableAssign: true,
  suppressLinkFormInitialData: false,
  showLinkFormDialogFooter: false,
  viewContext: 'assigneeTodo',
  initiatorSnapshotMode: false,
  currentMiRowId: null,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
  /** Optional `siblingRows`: that sub-table's row list only (not all bindings). My Request Detail merge uses it. */
  (e: 'viewSubtaskDetail', row: any, siblingRows?: any[]): void
}>()

// ---------------------------------------------------------------------------
// Core refs
// ---------------------------------------------------------------------------
const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
let isInternalUpdate = false

// Department data cache shared via provide/inject (Req 27)
const departmentTreeData = ref<any[]>([])
const departmentTreeLoading = ref(false)
provide('departmentTreeData', departmentTreeData)
provide('departmentTreeLoading', departmentTreeLoading)

const hasTabs = computed(() => props.tabs && props.tabs.length > 0)
const effectiveReadonly = computed(() => props.readonly || props.primaryReadOnly)

function isFieldReadonly(field: FormField): boolean {
  return isFormFieldReadonly(field, effectiveReadonly.value)
}
const activeTab = ref('')

watch(
  () => props.tabs?.map(t => String(t.name)).join('\u0001') ?? '',
  () => {
    const newTabs = props.tabs
    if (!newTabs?.length) {
      activeTab.value = ''
      return
    }
    const names = newTabs.map(t => t.name)
    const current = activeTab.value
    const stillValid =
      current !== '' &&
      current !== undefined &&
      names.some(n => String(n) === String(current))
    if (!stillValid) {
      activeTab.value = names[0]!
    }
  },
  { immediate: true },
)

const bindingMap = computed(() => {
  const map = new Map<number, SubTableBinding>()
  for (const b of (props.subTableBindings ?? [])) map.set(b.bindingId, b)
  return map
})
const linkableSubTableBindings = computed(() => props.linkedSubTableBindings ?? props.subTableBindings)
const resolveBinding = (id?: number) => {
  const binding = id != null ? bindingMap.value.get(id) : undefined
  return binding
}

function isBindingModeEditable(bindingMode: string | undefined | null): boolean {
  return String(bindingMode ?? '').trim().toUpperCase() === 'EDITABLE'
}

/** Sub-table CRUD follows developer-workstation table binding mode; whole-form readonly wins via {@link Props.readonly}. */
function isSubTableEditable(bindingId?: number): boolean {
  const binding = resolveBinding(bindingId)
  if (!binding || props.readonly) return false
  return isBindingModeEditable(binding.bindingMode)
}

function subTableAssigneeField(bindingId?: number): string | undefined {
  const b = resolveBinding(bindingId)
  if (!b) return undefined
  return resolveAssigneeFieldForBinding(
    b.columns as Array<{ field?: string }>,
    b.tableName
  )
}

function showSubTableAssignColumn(bindingId?: number): boolean {
  if (props.allowSubTableAssign === false) {
    return false
  }
  return !!(props.taskId && subTableAssigneeField(bindingId))
}

// ---------------------------------------------------------------------------
// Portal-views driven rendering helpers (designer → Portal contract)
// ---------------------------------------------------------------------------
/**
 * Effective sub-table display mode at the current view context. Returns one of:
 *   - 'tableOnly': just the SubTableField, nothing else
 *   - 'formBelowTable': SubTableField + inline form below (binds to current row)
 *   - 'summaryWithLinkFormModal': SubTableField; Details modal flow handled by existing
 *      Link Form column logic inside SubTableField (no inline form below)
 *
 * Uses the same portalViews merge as developer-workstation form preview — canvas props and
 * per-binding {@code configJson.subTablePortalViews[bindingId]} combine per field; explicit canvas
 * `assigneeTodo` / `initiatorRequest` overrides still win when set.
 */
function subTableMode(field: FormField): 'tableOnly' | 'formBelowTable' | 'summaryWithLinkFormModal' {
  const binding = resolveBinding(field._bindingId)
  const merged = mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
  return resolveSubTableDisplayMode(merged, props.viewContext)
}

function mergedPortalViewsForSubTable(field: FormField): SubTablePortalViews {
  const binding = resolveBinding(field._bindingId)
  return mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
}

/** My Request: link-form targets (e.g. subtable2) render only via Link Form modal, not duplicate tables. */
function shouldRenderPlacedSubTableField(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return true
  if (field._bindingId == null) return true
  const binding = resolveBinding(field._bindingId)
  if (!binding) return false
  const merged = mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
  const nativeIds = props.nativeSubTableBindingIds?.length
    ? new Set(props.nativeSubTableBindingIds.map(Number))
    : null
  return !shouldSuppressStandaloneSubTableInInitiatorRequest(
    field._bindingId,
    linkableSubTableBindings.value ?? [],
    merged,
    nativeIds,
    props.formConfig,
  )
}

/** 发起人「汇总 + Link/Details」：子表单元格内不展开 lookup / 用户快照明细，与设计师意图一致。 */
function subTableCompactLookupCells(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return false
  return subTableMode(field) === 'summaryWithLinkFormModal'
}

/**
 * 办理人待办 + 表格下内联表单：无论「表单来源」是 subForm 还是 Link 子表，只要列上存在 linkForm，
 * 点击链接只滚动到下方内联区，不打开 Link 弹层（与设计师 form below table 单一路径一致）。
 */
function linkFormScrollToInlineEnabled(field: FormField): boolean {
  if (props.viewContext !== 'assigneeTodo') return false
  return subTableMode(field) === 'formBelowTable'
}

const subTableInlineAnchors = new Map<number, HTMLElement>()
function setSubTableInlineAnchor(bindingId: number | undefined, el: HTMLElement | null) {
  if (bindingId == null) return
  if (el) subTableInlineAnchors.set(bindingId, el)
  else subTableInlineAnchors.delete(bindingId)
}

function scrollSubTableInlineIntoView(bindingId: number | undefined) {
  if (bindingId == null) return
  nextTick(() => {
    subTableInlineAnchors.get(bindingId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function subTableShowTaskStatusInitiator(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return false
  if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
  // Initiator + summary+Link Form: list columns come from designer `subListViews`; runtime Status/Actions
  // duplicate MI state and designer Actions/Detail columns (My Request / subform_copy).
  return false
}

function subTableShowViewDetailInitiator(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return false
  if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
  return false
}

/** Form-below 「表单来源」— follows merged portal views (aligned with developer-workstation preview). */
function resolveAssigneeTodoFormSource(field: FormField): {
  type: 'subForm' | 'linkForm' | 'formId'
  formId?: number | string | null
  linkFormColumnId?: number | string | null
} {
  const src = mergedPortalViewsForSubTable(field).assigneeTodoFormSource ?? {
    type: 'subForm',
    formId: null,
    linkFormColumnId: null
  }
  return {
    type: src.type,
    formId: src.formId ?? null,
    linkFormColumnId: src.linkFormColumnId ?? null
  }
}

/**
 * For a placed sub-table `field`, resolve which Link Form column on the binding's
 * list view drives the inline form-below-table when `assigneeTodoFormSource.type === 'linkForm'`,
 * then return that column's target sub-table binding.
 *
 * Selection precedence:
 *   1. Explicit `assigneeTodoFormSource.linkFormColumnId` (designer pick) — matches the
 *      column whose `props.componentId` equals the configured id.
 *   2. Legacy fallback — the first `type='linkForm'` column on the binding.
 *
 * Returns null when no Link Form column is configured or the target binding isn't loaded;
 * caller falls back to the binding's own subForm in that case.
 */
function findLinkFormTargetBinding(field: FormField): SubTableBinding | null {
  const binding = resolveBinding(field._bindingId)
  if (!binding) return null
  const cols = Array.isArray(binding.columns) ? binding.columns : []
  const source = resolveAssigneeTodoFormSource(field)
  const picked = source.linkFormColumnId
  const pickedKey = picked != null && String(picked).trim() !== '' ? String(picked) : null

  // Helper: read `componentId` off a column regardless of whether it's nested under
  // `props` (live designer state) or hoisted directly (some serialized shapes).
  const componentIdOf = (col: any): string | null => {
    const cid = col?.props?.componentId ?? col?.componentId
    return cid != null ? String(cid) : null
  }
  const targetBindingIdOf = (col: any): number | null => {
    const t = col?.props?.boundSubTableBindingId ?? col?.boundSubTableBindingId
    return t != null ? Number(t) : null
  }

  if (pickedKey) {
    for (const col of cols) {
      if (!col || col.type !== 'linkForm') continue
      if (componentIdOf(col) !== pickedKey) continue
      const targetId = targetBindingIdOf(col)
      if (targetId == null) continue
      const target = resolveBinding(targetId)
      if (target) {
        return target
      }
    }
    // Picked id no longer exists (e.g. column was removed) — fall through to legacy first-match.
  }

  for (const col of cols) {
    if (!col || col.type !== 'linkForm') continue
    const targetId = targetBindingIdOf(col)
    if (targetId == null) continue
    const target = resolveBinding(targetId)
    if (target) {
      return target
    }
  }
  return null
}

/**
 * Decide which binding's data should actually back the inline form-below-table.
 * - `subForm` (or unsupported `formId`): keep the field's own binding.
 * - `linkForm`: switch to the Link Form's target binding so the inline form mirrors
 *   exactly what would show in the Link Form modal — keeping designer and runtime
 *   contracts aligned. Falls back to the own binding when no Link Form column exists,
 *   so a misconfiguration never produces an empty section.
 */
function resolveInlineFormSourceBinding(field: FormField): SubTableBinding | null {
  const own = resolveBinding(field._bindingId)
  if (!own) return null
  const source = resolveAssigneeTodoFormSource(field)
  if (source.type === 'linkForm') {
    const target = findLinkFormTargetBinding(field)
    if (target) {
      return target
    }
  }
  // 待办 + 表格下表单：列表上存在 Link Form 列即以内联展示其目标子表（subtable2），避免仅靠绑定 JSON 未写 type=linkForm 时用错主表 subForm。
  if (props.viewContext === 'assigneeTodo' && subTableMode(field) === 'formBelowTable') {
    const target = findLinkFormTargetBinding(field)
    if (target && target.bindingId !== own.bindingId) {
      return target
    }
  }
  // `formId` is not yet runtime-resolved here (would need cross-form schema lookup); fall through.
  return own
}

/**
 * 「表格下内联表单」应对齐 Link 目标子表/自身子表的 bindingMode，而不是 {@code primaryReadOnly}
 * （主表只读时子表仍可编辑 — 与同页的 {@code SubTableField} 一致）。
 */
function inlineSubTableFormReadonly(field: FormField): boolean {
  if (props.readonly) return true
  const src = resolveInlineFormSourceBinding(field)
  if (!src) return true
  return !isBindingModeEditable(src.bindingMode)
}

/** 内联表单标题：与字段/schema 来源一致（linkForm→subtable2 时显示子表名，而非父表）。 */
function resolveInlineFormTableTitle(field: FormField): string {
  const src = resolveInlineFormSourceBinding(field)
  if (src?.tableName) return String(src.tableName)
  const own = resolveBinding(field._bindingId)
  return own?.tableName ? String(own.tableName) : ''
}

/**
 * Resolve the form schema for the inline form-below-table. Per the designer contract:
 *   - `subForm` (default): use the binding's own `formFields`
 *   - `linkForm`: use the Link Form target binding's `formFields`
 *   - `formId`: not yet runtime-supported; falls back to `subForm`
 */
function resolveInlineFormFields(field: FormField): FormField[] {
  const source = resolveInlineFormSourceBinding(field)
  const fields = Array.isArray(source?.formFields) ? source!.formFields : []
  return fields
}

/** FK candidates used to align a child (linkForm target) row to a parent row. */
function resolveLinkFkCandidates(target: SubTableBinding): string[] {
  const list: string[] = []
  const explicit = (target as any).foreignKeyField
  if (explicit && String(explicit).trim()) list.push(String(explicit))
  // Same heuristic used by SubTableField's Link Form modal so designer/runtime agree.
  for (const k of ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']) {
    if (!list.includes(k)) list.push(k)
  }
  return list
}

/** Match sub-table row to Flowable multi-instance element id (designer PK e.g. id_idw). */
function rowMatchesMiElementId(rec: Record<string, unknown>, parentId: string | number): boolean {
  const keys = ['id', 'rowId', 'id_idw', 'ID', 'RowId'] as const
  for (const k of keys) {
    const v = rec[k]
    if (v != null && v !== '' && String(v) === String(parentId)) return true
  }
  return false
}

/**
 * Persisted {@code target.data} may be empty/thin while child rows still live under parent rows'
 * {@code __subTables__}; merge those for inline form-below-table display and save.
 */
function buildBindingTableIdMap(peers: SubTableBinding[]): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const b of peers) {
    const tid = b.tableId != null ? Number(b.tableId) : null
    if (tid != null && Number.isFinite(tid)) m.set(b.bindingId, tid)
  }
  return m
}

function mergeRowsForInlineFormTarget(field: FormField): {
  target: SubTableBinding
  rows: any[]
  isLinkTarget: boolean
} | null {
  const own = resolveBinding(field._bindingId)
  if (!own) return null
  const target = resolveInlineFormSourceBinding(field) ?? own
  const isLinkTarget = target.bindingId !== own.bindingId
  const peers = linkableSubTableBindings.value ?? []
  const pk = target.primaryKeyFields ?? own.primaryKeyFields ?? null
  const parentId = props.currentMiRowId
  const miIsolate =
    props.suppressLinkFormInitialData
    && parentId != null
    && String(parentId).trim() !== ''
    && isLinkTarget

  if (miIsolate) {
    const parentRow = findMiIsolatedParentRow(
      Array.isArray(own.data) ? own.data : [],
      parentId
    )
    if (parentRow) {
      const peerMap = buildBindingTableIdMap(peers)
      const nestedOnly = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: target.bindingId,
          tableName: target.tableName,
          physicalTableName: target.physicalTableName,
          tableId: target.tableId ?? null
        },
        [parentRow],
        peerMap
      )
      let rows = nestedOnly.map(r => ({ ...(r as Record<string, any>) }))
      const topLevel = Array.isArray(target.data) ? target.data : []
      const topForParent = pickMiLinkChildRowsForParent(
        parentRow,
        topLevel,
        pk,
      )
      if (topForParent.length > 0) {
        rows = mergeSubTableRowsByRowId(rows, topForParent, pk).map(r => ({
          ...(r as Record<string, any>)
        }))
      }
      return {
        target,
        isLinkTarget,
        rows
      }
    }
    return { target, isLinkTarget, rows: [] }
  }

  const nestedFromTarget = collectNestedChildRowsFromPeerBindings(target, peers, null)
  /** Table grid uses `own.data`; link-form inline uses `target` — merge both so the row list matches the grid. */
  let merged = mergeSubTableRowsByRowId(
    Array.isArray(own.data) ? own.data : [],
    Array.isArray(target.data) ? target.data : [],
    pk,
  )
  merged = mergeSubTableRowsByRowId(merged, nestedFromTarget, pk)
  return {
    target,
    isLinkTarget,
    rows: merged.map(r => ({ ...(r as Record<string, any>) })),
  }
}

/** Prefer "fat" snapshot rows when many duplicates exist (preview/read-only diagram clicks often defaulted to rows[0] thin placeholders). */
function scoreInlineRowCompleteness(row: unknown, field: FormField): number {
  if (!row || typeof row !== 'object') return 0
  const rec = row as Record<string, unknown>
  const layoutKeys = resolveInlineFormFields(field).map(f => f.key).filter((k): k is string => typeof k === 'string' && k.length > 0)
  const keys =
    layoutKeys.length > 0
      ? layoutKeys
      : Object.keys(rec).filter(k => !k.startsWith('__'))
  let score = 0
  for (const k of keys) {
    const v = rec[k]
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    score++
  }
  return score
}

function pickPreferredInlineRow(rows: any[], field: FormField): any | null {
  if (!rows.length) return null
  if (rows.length === 1) return rows[0]
  if (!(effectiveReadonly.value && props.previewSubTables)) return rows[0]
  let best = rows[0]
  let bestScore = scoreInlineRowCompleteness(best, field)
  for (let i = 1; i < rows.length; i++) {
    const r = rows[i]
    const s = scoreInlineRowCompleteness(r, field)
    if (s > bestScore) {
      best = r
      bestScore = s
    }
  }
  return best
}

/**
 * Find the "current row" for inline form-below-table binding.
 *
 * For `subForm` source (own binding):
 *   1. If `currentMiRowId` is provided, prefer the matching row (handles MI sub-task).
 *   2. Else fall back to the single available row (普通单任务 single-row table).
 *
 * For `linkForm` source (target binding, e.g. subtable2):
 *   1. If `currentMiRowId` is provided, find the target row whose FK === parent rowId.
 *   2. Else if target has a single row, use it.
 *   3. Else `null` — host renders with empty defaults; first edit creates a new row.
 */
function findInlineRowIndexForMi(
  rows: any[],
  pack: { target: SubTableBinding; isLinkTarget: boolean },
  parentId: string | number | null | undefined,
): number {
  if (parentId == null || String(parentId).trim() === '') return -1
  const fkList = resolveLinkFkCandidates(pack.target)
  let idx = rows.findIndex(r => {
    if (!r || typeof r !== 'object') return false
    const rec = r as Record<string, unknown>
    return fkList.some(k => {
      const v = rec[k]
      return v != null && v !== '' && String(v) === String(parentId)
    })
  })
  if (idx >= 0) return idx
  idx = rows.findIndex(r => {
    if (!r || typeof r !== 'object') return false
    return rowMatchesMiElementId(r as Record<string, unknown>, parentId)
  })
  return idx
}

function getCurrentRowForInlineForm(field: FormField): Record<string, any> | null {
  const pack = mergeRowsForInlineFormTarget(field)
  if (!pack) return null
  const { rows, isLinkTarget } = pack
  const parentId = props.currentMiRowId

  let result: Record<string, any> | null = null
  let pickReason = 'none'

  const miLinkIsolate =
    props.suppressLinkFormInitialData
    && parentId != null
    && String(parentId).trim() !== ''
    && isLinkTarget

  if (miLinkIsolate) {
    if (rows.length === 1) {
      result = { ...(rows[0] as Record<string, any>) }
      pickReason = 'mi-nested-only'
    } else if (rows.length === 0) {
      result = {}
      pickReason = 'mi-nested-empty'
    }
  } else if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
    const own = resolveBinding(field._bindingId)
    const parentRow = own
      ? findMiIsolatedParentRow(Array.isArray(own.data) ? own.data : [], parentId)
      : null
    if (parentRow) {
      const aligned = pickMiLinkChildRowsForParent(
        parentRow,
        rows,
        pack.target.primaryKeyFields ?? null
      )
      if (aligned.length > 0) {
        result = { ...(aligned[0] as Record<string, any>) }
        pickReason = 'mi-link-parent-align'
      }
    }
    if (!result) {
      const fkList = resolveLinkFkCandidates(pack.target)
      const match = rows.find(r => {
        if (!r || typeof r !== 'object') return false
        const rec = r as Record<string, unknown>
        return fkList.some(k => {
          const v = rec[k]
          return v != null && v !== '' && String(v) === String(parentId)
        })
      })
      if (match) {
        result = { ...(match as Record<string, any>) }
        pickReason = 'link-fk'
      } else if (!props.suppressLinkFormInitialData) {
        const pick = pickPreferredInlineRow(rows, field)
        result = pick ? { ...(pick as Record<string, any>) } : null
        pickReason = 'link-fallback-pick'
      }
    }
  } else if (parentId != null && String(parentId).trim() !== '') {
    // subForm path: MI element id often matches a *parent* FK on this row, not the child row PK (e.g. id=999).
    const idx = findInlineRowIndexForMi(rows, pack, parentId)
    if (idx >= 0) {
      result = { ...(rows[idx] as Record<string, any>) }
      pickReason = 'mi-idx'
    }
  }

  if (!result && !miLinkIsolate) {
    const pick = pickPreferredInlineRow(rows, field)
    result = pick ? { ...(pick as Record<string, any>) } : null
    pickReason = pickReason === 'none' ? 'pickPreferred' : `${pickReason}+pickPreferred`
  }

  return result
}

/**
 * When the inline form below is edited, merge the new values back into the matching
 * row in the EFFECTIVE source binding (own binding for `subForm`, link target for
 * `linkForm`) and emit `update:subTableData` so the host (tasks/detail or
 * applications/detail) can persist it via the existing data flow.
 *
 * When no matching child row exists in the link-target binding yet, a fresh row is
 * appended and the FK column is populated with `currentMiRowId` so persistence stays
 * within the existing dw_table_data → child-rows pipeline.
 */
function handleInlineFormUpdate(field: FormField, mergedRow: Record<string, any>) {
  const pack = mergeRowsForInlineFormTarget(field)
  if (!pack) return
  const { target, rows, isLinkTarget } = pack
  const parentId = props.currentMiRowId

  let idx = -1
  if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
    const fkList = resolveLinkFkCandidates(target)
    idx = rows.findIndex(r => {
      if (!r || typeof r !== 'object') return false
      const rec = r as Record<string, unknown>
      return fkList.some(k => {
        const v = rec[k]
        return v != null && v !== '' && String(v) === String(parentId)
      })
    })
    if (idx < 0 && rows.length === 1) idx = 0
  } else if (isLinkTarget && rows.length === 1) {
    idx = 0
  } else if (parentId != null && String(parentId).trim() !== '') {
    idx = findInlineRowIndexForMi(rows, pack, parentId)
  } else if (rows.length === 1) {
    idx = 0
  }

  if (idx < 0 && rows.length > 0) {
    idx = 0
  }

  if (idx >= 0) {
    rows[idx] = { ...rows[idx], ...mergedRow }
  } else {
    const fresh: Record<string, any> = { ...mergedRow }
    if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      // Seed the FK so the new child row aligns with the parent participant.
      const explicit = (target as any).foreignKeyField
      const fkField = explicit && String(explicit).trim() ? String(explicit) : 'parent_id'
      if (fresh[fkField] == null || fresh[fkField] === '') fresh[fkField] = parentId
    }
    if (!isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      const fkList = resolveLinkFkCandidates(target)
      for (const k of fkList) {
        if (fresh[k] == null || fresh[k] === '') {
          fresh[k] = parentId
          break
        }
      }
    }
    rows.push(fresh)
  }
  handleSubTableUpdate(target.bindingId, rows)
}
// Lookup selected data state
const lookupSelectedData = ref<Record<string, Record<string, any>>>({})
const lookupLoadedViewFields = ref<Record<string, any[]>>({})
/** Parity with Form Preview / FieldRenderer — honor lookupConfig.showBackfillView === false. */
function lookupShowBackfillView(field: FormField): boolean {
  return (field as any)._lookupShowBackfillView !== false
}
const handleLookupSelect = (fieldKey: string, row: Record<string, any>) => {
  lookupSelectedData.value[fieldKey] = row
}
const handleLookupClear = (fieldKey: string) => {
  delete lookupSelectedData.value[fieldKey]
}

// Manage file upload lists independently to avoid re-render issues when deriving from formData
const uploadFileLists = ref<Record<string, Array<{ name: string; url: string; uid?: number }>>>({})

// Get all fields (including fields in tabs)
const allFields = computed(() =>
  flattenAllFormFieldSegments(props.fields, props.tabs, props.fieldsAfterTabs),
)

const formCreateRulesResolved = computed(() => {
  if (Array.isArray(props.formCreateRules) && props.formCreateRules.length) {
    return props.formCreateRules
  }
  const fromConfig = props.formConfig?.rule
  return Array.isArray(fromConfig) ? fromConfig : []
})

const fieldComponentEvents = computed(() =>
  collectFieldComponentEventsFromRules(formCreateRulesResolved.value),
)

// ---------------------------------------------------------------------------
// Task 7.2: BusinessLogicEngine integration
// ---------------------------------------------------------------------------
const engine = new BusinessLogicEngine()
const engineVisibility = ref(new Map<string, boolean>())
const eventVisibilityState = reactive<PortalFormVisibilityState>({
  hidden: new Map<string, boolean>(),
  display: new Map<string, boolean>(),
})
const eventVisibilityTick = ref(0)

function notifyEventVisibilityChange() {
  eventVisibilityState.hidden = new Map(eventVisibilityState.hidden)
  eventVisibilityState.display = new Map(eventVisibilityState.display)
  eventVisibilityTick.value++
}

function isFieldVisible(fieldKey: string): boolean {
  void eventVisibilityTick.value
  if (eventVisibilityState.hidden.get(fieldKey) === true) return false
  if (eventVisibilityState.display.get(fieldKey) === false) return false
  return engineVisibility.value.get(fieldKey) ?? true
}

function createFormEventApi() {
  const resolveFieldKey = createFieldKeyResolver(() => allFields.value)
  return createPortalFormApi(
    () => formData.value,
    (patch) => {
      formData.value = { ...formData.value, ...patch }
    },
    resolveFieldKey,
    {
      state: eventVisibilityState,
      notify: notifyEventVisibilityChange,
      getAllFieldKeys: () => allFields.value.map(f => f.key),
    },
  )
}

function runFormOptionsOnChange(field: string, value: unknown) {
  const onChangeHandler = props.formOptions?.onChange
  if (!onChangeHandler) return
  const api = createFormEventApi()
  const rule = fieldComponentEvents.value.get(field)?.rule ?? {}
  runFormOnChangeHandler(onChangeHandler, field, value, api, rule)
}

function runComponentEventsOnFieldChange(key: string, value: unknown) {
  const api = createFormEventApi()
  const ev = fieldComponentEvents.value.get(key)
  runComponentFieldEvents(ev, {
    field: key,
    value,
    api,
    onEvent: 'change',
    hookEvent: 'value',
  })
}

/** Component `on.blur` — runs when focus leaves input/textarea (not on each keystroke). */
function handleFieldBlur(key: string) {
  const value = formData.value[key]
  const api = createFormEventApi()
  const ev = fieldComponentEvents.value.get(key)
  runComponentFieldEvents(ev, {
    field: key,
    value,
    api,
    onEvent: 'blur',
  })
  const onChangeHandler = props.formOptions?.onChange
  if (onChangeHandler || fieldComponentEvents.value.has(key)) {
    if (!props.readonly) {
      emit('update:modelValue', { ...formData.value })
    }
  }
}

function bootstrapFormOptionsOnChange() {
  if (!props.formOptions?.onChange) return
  runFormOptionsOnChange('__bootstrap__', null)
}

function bootstrapComponentHookEvents() {
  if (!formCreateRulesResolved.value.length) return
  runAllComponentHookEvents(
    formCreateRulesResolved.value,
    'load',
    () => formData.value,
    (patch) => { formData.value = { ...formData.value, ...patch } },
    createFieldKeyResolver(() => allFields.value),
    {
      state: eventVisibilityState,
      notify: notifyEventVisibilityChange,
      getAllFieldKeys: () => allFields.value.map(f => f.key),
    },
  )
  runAllComponentHookEvents(
    formCreateRulesResolved.value,
    'mounted',
    () => formData.value,
    (patch) => { formData.value = { ...formData.value, ...patch } },
    createFieldKeyResolver(() => allFields.value),
    {
      state: eventVisibilityState,
      notify: notifyEventVisibilityChange,
      getAllFieldKeys: () => allFields.value.map(f => f.key),
    },
  )
}
const engineOptions = ref(new Map<string, Array<{ label: string; value: any }>>())
const engineFieldStates = ref(new Map<string, { disabled?: boolean; required?: boolean }>())
const engineCalculatedValues = ref(new Map<string, number>())

function initEngine() {
  if (props.config) {
    engine.init(props.config)
  }
}

// ---------------------------------------------------------------------------
// User search — listen to FieldRenderer search:users event (Req 11.2)
// ---------------------------------------------------------------------------
const userSearchResults = ref(new Map<string, Array<{ id: string; name: string }>>())

async function handleUserSearch(query: string, fieldKey: string) {
  try {
    const results = await userApi.searchUsers(query)
    userSearchResults.value.set(fieldKey, results)
    userSearchResults.value = new Map(userSearchResults.value)
  } catch {
    userSearchResults.value.set(fieldKey, [])
    userSearchResults.value = new Map(userSearchResults.value)
  }
}

function applyEngineResult(result: {
  visibilityChanges: Map<string, boolean>
  calculatedValues: Map<string, number>
  optionChanges: Map<string, Array<{ label: string; value: any }>>
  stateChanges: Map<string, { disabled?: boolean; required?: boolean }>
}) {
  // Merge visibility changes
  for (const [k, v] of result.visibilityChanges) {
    engineVisibility.value.set(k, v)
  }
  // Merge calculated values and update formData
  for (const [k, v] of result.calculatedValues) {
    engineCalculatedValues.value.set(k, v)
    formData.value[k] = v
  }
  // Merge option changes
  for (const [k, v] of result.optionChanges) {
    engineOptions.value.set(k, v)
  }
  // Merge state changes
  for (const [k, v] of result.stateChanges) {
    engineFieldStates.value.set(k, v)
  }
  // Trigger reactivity
  engineVisibility.value = new Map(engineVisibility.value)
  engineOptions.value = new Map(engineOptions.value)
  engineFieldStates.value = new Map(engineFieldStates.value)
  engineCalculatedValues.value = new Map(engineCalculatedValues.value)
}

// ---------------------------------------------------------------------------
// Form data initialization
// ---------------------------------------------------------------------------
const initFormData = () => {
  const data: Record<string, any> = {}
  allFields.value.forEach(field => {
    if (props.modelValue[field.key] !== undefined) {
      data[field.key] = props.modelValue[field.key]
    } else if (field.defaultValue !== undefined) {
      data[field.key] = field.defaultValue
    } else if (field.type === 'checkbox') {
      data[field.key] = []
    } else {
      data[field.key] = null
    }
  })
  isInternalUpdate = true
  formData.value = data
  setTimeout(() => { isInternalUpdate = false }, 0)
  // Element Plus AsyncValidator resolves as micro-tasks after nextTick;
  // use setTimeout (macro-task) to guarantee clearValidate runs last.
  setTimeout(() => {
    const el = formRef.value
    if (el && typeof (el as { clearValidate?: () => void }).clearValidate === 'function') {
      el.clearValidate()
    }
  }, 0)
}

// ---------------------------------------------------------------------------
// Form rules
// ---------------------------------------------------------------------------
const formRules = computed<FormRules>(() => {
  if (props.readonly) return {}
  const rules: FormRules = {}
  allFields.value.forEach(field => {
    if (field.required || field.rules) {
      const fieldRules: any[] = []
      if (field.required) {
        fieldRules.push({
          required: true,
          message: t('common.pleaseInput', { label: field.label }),
          trigger: field.type === 'select' ? 'change' : 'blur'
        })
      }
      if (field.rules) {
        fieldRules.push(...field.rules)
      }
      rules[field.key] = fieldRules
    }
  })
  return rules
})

// ---------------------------------------------------------------------------
// Field change handler (Task 7.1 + 7.2)
// ---------------------------------------------------------------------------
function handleFieldChange(key: string, value: any) {
  formData.value[key] = value
  emit('change', key, value)

  runComponentEventsOnFieldChange(key, value)

  const onChangeHandler = props.formOptions?.onChange
  if (onChangeHandler) {
    runFormOptionsOnChange(key, value)
  }
  if (onChangeHandler || fieldComponentEvents.value.has(key)) {
    if (!props.readonly) {
      emit('update:modelValue', { ...formData.value })
    }
  }

  // Task 7.2: Trigger engine evaluation on field change
  if (props.config) {
    const result = engine.onFieldChange(key, value, formData.value)
    applyEngineResult(result)
  }
}

// ---------------------------------------------------------------------------
// Upload handlers
// ---------------------------------------------------------------------------
function handleUploadSuccess(response: any, _file: any, fieldKey: string) {
  const url = response?.data?.url || ''
  formData.value[fieldKey] = url
  emit('update:modelValue', { ...formData.value })
}

function handleUploadRemove(_file: any, fieldKey: string) {
  formData.value[fieldKey] = ''
  emit('update:modelValue', { ...formData.value })
}

// ---------------------------------------------------------------------------
// Task 7.4: Sub-table summary integration
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// SubTableField config helpers (Req 10.1, 10.2, 10.3)
// ---------------------------------------------------------------------------
function getSubFormRowFormulas(bindingId?: number) {
  if (!bindingId || !props.config?.subForms) return undefined
  return props.config.subForms[String(bindingId)]?.rowFormulas
}

function getSummaryColumns(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  return props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .map(r => r.sourceColumn)
}

function getSummaryAggregations(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  const aggs: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> = {}
  props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .forEach(r => { aggs[r.sourceColumn] = r.aggregation })
  return Object.keys(aggs).length > 0 ? aggs : undefined
}

function getSubTableValidation(bindingId?: number) {
  if (!bindingId || !props.config?.subTableValidation) return undefined
  return props.config.subTableValidation[String(bindingId)]
}

function handleSubTableUpdate(bindingId: number, rows: any[]) {
  emit('update:subTableData', bindingId, rows)

  // Trigger engine summary calculations
  if (props.config) {
    const summaryResult = engine.onSubTableChange(bindingId, rows, formData.value)
    for (const [targetField, value] of summaryResult.summaryValues) {
      formData.value[targetField] = value
      engineCalculatedValues.value.set(targetField, value)
    }
    engineCalculatedValues.value = new Map(engineCalculatedValues.value)
  }
}

// ---------------------------------------------------------------------------
// Watchers
// ---------------------------------------------------------------------------
watchThrottled(
  formData,
  (newVal) => {
    if (!isInternalUpdate && !props.readonly) {
      emit('update:modelValue', { ...newVal })
    }
  },
  { deep: true, throttle: 150 },
)

watch(() => props.modelValue, (newVal, oldVal) => {
  if (!isEqual(newVal, oldVal)) {
    initFormData()
  }
}, { deep: true })

watch(allFields, (newFields, oldFields) => {
  const hasChanged = newFields.length !== oldFields.length ||
    newFields.some((f, i) => f.key !== oldFields[i]?.key)
  if (hasChanged) {
    initFormData()
  }
})

watch(
  () => props.config,
  () => {
    initEngine()
  },
)

// ---------------------------------------------------------------------------
// Task 7.3: Form validation with engine integration
// ---------------------------------------------------------------------------

/**
 * Inject an engine validation error into an Element Plus form-item via DOM.
 * Adds the `is-error` class and appends an `.el-form-item__error` element.
 */
function injectFieldError(fieldKey: string, message: string) {
  const itemEl = document.querySelector(
    `.el-form-item[prop="${fieldKey}"]`
  ) as HTMLElement | null
  if (!itemEl) return
  itemEl.classList.add('is-error')
  const contentEl = itemEl.querySelector('.el-form-item__content')
  if (!contentEl) return
  // Remove any previously injected engine errors
  contentEl.querySelectorAll('.engine-error').forEach(el => el.remove())
  const errorDiv = document.createElement('div')
  errorDiv.className = 'el-form-item__error engine-error'
  errorDiv.textContent = message
  contentEl.appendChild(errorDiv)
}

/**
 * Clear all previously injected engine validation errors from the form.
 */
function clearEngineErrors() {
  document.querySelectorAll('.engine-error').forEach(el => el.remove())
  // Note: we don't remove is-error class here because Element Plus may have its own errors
}

const validate = async (): Promise<boolean> => {
  if (!formRef.value) return false

  // Clear previously injected engine errors before re-validating
  clearEngineErrors()

  let elPlusValid = true
  try {
    await formRef.value.validate()
  } catch {
    elPlusValid = false
  }

  // Engine validation (cross-field + custom rules)
  if (props.config) {
    const engineResult = engine.validateAll(formData.value)
    const crossResult = engine.validateCrossField(formData.value)

    if (!engineResult.valid || !crossResult.valid) {
      // Inject engine field errors into Element Plus form-item error state via DOM
      for (const [fieldKey, errors] of engineResult.fieldErrors) {
        if (errors.length > 0) {
          injectFieldError(fieldKey, errors[0])
        }
      }
      // Inject cross-field errors into targetField form-items
      for (const err of crossResult.errors) {
        injectFieldError(err.targetField, err.message)
      }
      // Scroll to first error field
      nextTick(() => {
        const firstError = document.querySelector('.el-form-item.is-error')
        firstError?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      return false
    }
  }

  if (!elPlusValid) {
    // Scroll to first Element Plus error
    nextTick(() => {
      const firstError = document.querySelector('.el-form-item.is-error')
      if (firstError) {
        firstError.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    })
  }

  return elPlusValid
}

// ---------------------------------------------------------------------------
// Task 7.5: Auto-save to localStorage
// ---------------------------------------------------------------------------
const AUTO_SAVE_INTERVAL = 30_000 // 30 seconds
let autoSaveTimer: ReturnType<typeof setInterval> | null = null

function getAutoSaveKey(): string | null {
  if (props.functionUnitId && props.formId) {
    return `form_autosave_${props.functionUnitId}_${props.formId}`
  }
  return null
}

function autoSave() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return
  try {
    localStorage.setItem(key, JSON.stringify(formData.value))
  } catch (err) {
    console.warn('[FormRenderer] Auto-save to localStorage failed:', err)
  }
}

function startAutoSave() {
  stopAutoSave()
  if (getAutoSaveKey() && !props.readonly) {
    autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
  }
}

function stopAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function clearAutoSave() {
  const key = getAutoSaveKey()
  if (key) {
    try {
      localStorage.removeItem(key)
    } catch (err) {
      console.warn('[FormRenderer] Failed to clear auto-save:', err)
    }
  }
  stopAutoSave()
}

async function checkAutoSaveRestore() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return

  try {
    const saved = localStorage.getItem(key)
    if (!saved) return

    const savedData = JSON.parse(saved)
    if (!savedData || typeof savedData !== 'object') return

    await ElMessageBox.confirm(
      t('formRenderer.autoSaveRestorePrompt'),
      t('formRenderer.autoSaveTitle'),
      {
        confirmButtonText: t('formRenderer.restore'),
        cancelButtonText: t('formRenderer.discard'),
        type: 'info',
      }
    )
    // User chose to restore
    isInternalUpdate = true
    formData.value = { ...formData.value, ...savedData }
    setTimeout(() => { isInternalUpdate = false }, 0)
    emit('update:modelValue', { ...formData.value })

    // Trigger engine re-evaluation for all restored fields (Req 12.1, 12.2)
    if (props.config) {
      for (const [key, value] of Object.entries(formData.value)) {
        if (value != null && value !== '') {
          const result = engine.onFieldChange(key, value, formData.value)
          applyEngineResult(result)
        }
      }
    }
  } catch {
    // User chose to discard or parse error — clear saved data
    clearAutoSave()
  }
}

// ---------------------------------------------------------------------------
// Existing exposed methods
// ---------------------------------------------------------------------------
const resetForm = () => {
  formRef.value?.resetFields()
  initFormData()
}

const getFormData = () => {
  return { ...formData.value }
}

const setFieldValue = (key: string, value: any) => {
  formData.value[key] = value
}

provide(FORM_RENDERER_FIELDS_CTX, reactive({
  formData,
  readonly: effectiveReadonly,
  labelWidth: computed(() => props.labelWidth),
  uploadUrl: computed(() => props.uploadUrl),
  taskId: computed(() => props.taskId),
  viewContext: computed(() => props.viewContext),
  subTableBindings: computed(() => props.subTableBindings),
  linkableSubTableBindings,
  enableSubTablePolling: computed(() => props.enableSubTablePolling),
  subTablePollingInterval: computed(() => props.subTablePollingInterval),
  suppressLinkFormInitialData: computed(() => props.suppressLinkFormInitialData),
  showLinkFormDialogFooter: computed(() => props.showLinkFormDialogFooter),
  lookupSelectedData,
  lookupLoadedViewFields,
  engineVisibility,
  isFieldVisible,
  engineFieldStates,
  engineOptions,
  userSearchResults,
  isFieldReadonly,
  resolveBinding,
  shouldRenderPlacedSubTableField,
  isSubTableEditable,
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
  subTableAssigneeField,
  showSubTableAssignColumn,
  linkFormScrollToInlineEnabled,
  subTableShowTaskStatusInitiator,
  subTableShowViewDetailInitiator,
  subTableCompactLookupCells,
  subTableMode,
  resolveInlineFormTableTitle,
  resolveInlineFormFields,
  getCurrentRowForInlineForm,
  inlineSubTableFormReadonly,
  lookupShowBackfillView,
  handleSubTableUpdate,
  handleInlineFormUpdate,
  scrollSubTableInlineIntoView,
  setSubTableInlineAnchor,
  handleLookupSelect,
  handleLookupClear,
  handleFieldChange,
  handleFieldBlur,
  handleUploadSuccess,
  handleUploadRemove,
  handleUserSearch,
  emitViewSubtaskDetail: (row: unknown, siblingRows?: unknown[]) => {
    emit('viewSubtaskDetail', row, siblingRows)
  },
}))

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------
onMounted(() => {
  initFormData()
  initEngine()
  bootstrapComponentHookEvents()
  bootstrapFormOptionsOnChange()
  // Task 7.5: Check for auto-saved data, then start auto-save timer
  checkAutoSaveRestore().then(() => {
    startAutoSave()
  })
})

onBeforeUnmount(() => {
  stopAutoSave()
})

// ---------------------------------------------------------------------------
// Expose (keep existing + add clearAutoSave)
// ---------------------------------------------------------------------------
defineExpose({
  validate,
  resetForm,
  getFormData,
  setFieldValue,
  clearAutoSave,
  // Exposed for testing (Req 10 property test)
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
})
</script>

<style scoped lang="scss">
@import '@/styles/form-readonly.scss';

.form-renderer {
  width: 100%;

  :deep(.el-form-item__label) {
    font-weight: 500;
    white-space: nowrap;
    padding-right: 16px;
  }

  /* Form item content in flex layout must be shrinkable and fill remaining width so dropdowns/date pickers render at 100% */
  :deep(.el-form-item__content) {
    flex: 1;
    min-width: 0;
    max-width: 100%;
  }

  :deep(.el-form-item__content .el-select),
  :deep(.el-form-item__content .el-tree-select),
  :deep(.el-form-item__content .el-cascader),
  :deep(.el-form-item__content .el-date-editor) {
    width: 100% !important;
  }

  :deep(.el-form-item__content .el-select .el-select__wrapper),
  :deep(.el-form-item__content .el-tree-select .el-select__wrapper) {
    width: 100%;
  }

  :deep(.el-tabs--border-card) {
    border-radius: 4px;
    width: 100%;

    .el-tabs__header {
      background-color: #f5f7fa;
    }

    .el-tabs__content {
      padding: 20px;
    }
  }

  :deep(.el-form) {
    width: 100%;
  }

  .form-fields-before-tabs,
  .form-fields-after-tabs {
    width: 100%;
    margin-bottom: 18px;
  }

  .form-renderer-tabs {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-tabs__header) {
      margin-bottom: 0;
    }

    :deep(.el-tabs__content) {
      padding: 16px 0 0;
    }
  }

  .form-renderer-collapse {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-collapse-item__header) {
      font-weight: 500;
      color: #303133;
    }

    :deep(.el-collapse-item__content) {
      padding: 16px 0 4px;
    }
  }

  .form-layout-card {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-card__header) {
      padding: 12px 16px;
      font-weight: 500;
      background: #fafafa;
    }
  }

  .form-layout-card-title {
    color: #303133;
  }

  .color-swatch {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 3px;
    border: 1px solid #dcdfe6;
    vertical-align: middle;
  }

  .editor-readonly {
    padding: 8px;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background: #f5f7fa;
    min-height: 40px;
    line-height: 1.5;
    word-break: break-word;
    width: 100%;
  }

  .signature-preview {
    max-width: 200px;
    max-height: 80px;
    object-fit: contain;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background: #fff;
  }

  .lookup-form-item {
    margin-bottom: 18px;

    :deep(.el-form-item__label) {
      display: flex;
      align-items: center;
    }
  }

  .lookup-label-text {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #606266;
  }

  .lookup-label-icon {
    color: #409eff;
    font-size: 14px;
  }

  .lookup-field-wrapper {
    width: 100%;
  }
}
</style>

<style lang="scss">
/* Scoped to .form-renderer container to prevent global style leak (Req 30) */
.form-renderer {
  .form-renderer-popper {
    z-index: 2050;
  }

  :deep(.el-select__popper) {
    z-index: 2050;
  }

  :deep(.el-picker__popper) {
    z-index: 2050;
  }

  :deep(.el-cascader__dropdown) {
    z-index: 2050;
  }

  :deep(.el-tree-select__popper) {
    z-index: 2050;
  }
}
</style>

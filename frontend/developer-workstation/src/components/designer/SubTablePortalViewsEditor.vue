<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveBindingDisplayName } from '@/utils/bindingDisplayHelpers'

/**
 * User-facing labels are translated via `form.portalViews.*`.
 * Persisted JSON still uses enum values (`formBelowTable`, `subForm`, …).
 * Legacy `assigneeTodoFormSource.type === 'formId'` is coerced to `subForm` in the UI.
 */
interface DesignerLinkFormColumnInfo {
  // String for the fieldName fallback path; number for both negative "generic"
  // componentIds (auto-assigned from -bindingId) and positive curated ids.
  componentId: number | string
  sourceBindingId: number
  sourceBindingName: string
  boundSubTableBindingId: number | null
  boundSubTableName: string | null
  columnLabel: string
  linkText: string
}

type AssigneeTodoMode = 'formBelowTable' | 'tableOnly'
type FormSourceType = 'subForm' | 'linkForm'
type InitiatorRequestMode = 'mirrorTodo' | 'summaryWithLinkFormModal' | 'tableOnly'

interface PortalViews {
  assigneeTodo: AssigneeTodoMode
  assigneeTodoFormSource?: {
    type: FormSourceType | 'formId'
    formId?: number | string | null
    /**
     * For `type='linkForm'`: which Link Form column on the sub-table's list view to use
     * (matched against dw_link_form_components.id, persisted as `componentId`).
     * Unset → legacy fallback (use the first Link Form column the runtime finds).
     */
    linkFormColumnId?: number | string | null
  }
  initiatorRequest: InitiatorRequestMode
}

interface Props {
  modelValue: PortalViews | null | undefined
  /**
   * Binding-level bar passes this so the Link Form column list is scoped; the main canvas
   * property panel often leaves it unset (global picker).
   */
  bindingId?: number | string | null
  /**
   * When set (binding-level portalViews bar in FormDesigner), Link Form column options
   * come from this map — no inject required.
   */
  linkFormColumnsByBinding?: Record<number, DesignerLinkFormColumnInfo[]> | null
  /**
   * Main fc-designer property row already shows a title — hide the in-component heading
   * to avoid duplication.
   */
  showSectionHeading?: boolean
}

const { t } = useI18n()

const props = withDefaults(defineProps<Props>(), {
  showSectionHeading: true
})
const emit = defineEmits<{
  'update:modelValue': [val: PortalViews]
}>()

/** Default collapsed — expand to edit To Do / My Request portal options. */
const openPanels = ref<string[]>([])

const collapseHeaderTitle = computed(() =>
  props.showSectionHeading
    ? t('form.portalViews.sectionTitle')
    : t('form.portalViews.sidePanelCollapseTitle')
)

const injectedLinkFormColumns = inject<() => Record<number, DesignerLinkFormColumnInfo[]>>(
  'designerLinkFormColumns',
  () => ({})
)

/**
 * All Link Form columns indexed by source binding id.
 * Prefer explicit prop from FormDesigner (binding-level bar); otherwise inject
 * (fc-designer property panel). When inject misses, default `() => ({})` never
 * calls FormDesigner's provider — the prop path avoids that entirely.
 */
const allLinkFormColumns = computed<Record<number, DesignerLinkFormColumnInfo[]>>(() => {
  if (props.linkFormColumnsByBinding != null) {
    return props.linkFormColumnsByBinding
  }
  return injectedLinkFormColumns() || {}
})

/**
 * Flattened list of every Link Form column across all SUB bindings of the current form.
 * Used as the "global" picker scope (rule-level form-create panel context) and as the
 * fallback when the binding-scoped list is empty so designers don't get stuck.
 */
const allLinkFormColumnsFlat = computed<DesignerLinkFormColumnInfo[]>(() => {
  const flat: DesignerLinkFormColumnInfo[] = []
  for (const arr of Object.values(allLinkFormColumns.value)) flat.push(...arr)
  return flat
})

/** Columns scoped to the binding-level editor's own binding (if any). */
const scopedLinkFormColumns = computed<DesignerLinkFormColumnInfo[]>(() => {
  if (props.bindingId == null || String(props.bindingId).trim() === '') return []
  const numeric = Number(props.bindingId)
  return allLinkFormColumns.value[numeric] || []
})

/**
 * Effective picker contents:
 *   - Prefer the scoped list (this sub-table's own Link Form columns).
 *   - Fall back to the global list when scoped is empty so the picker is still useful
 *     (e.g. when the designer expected scoped data but state isn't loaded yet, or when
 *     the rule-level editor has no binding context at all).
 */
const availableLinkFormColumns = computed<DesignerLinkFormColumnInfo[]>(() => {
  const scoped = scopedLinkFormColumns.value
  if (scoped.length > 0) return scoped
  return allLinkFormColumnsFlat.value
})

/** True when scoped lookup yielded nothing but we have columns from other bindings to show. */
const showingFallbackScope = computed(() =>
  props.bindingId != null
  && String(props.bindingId).trim() !== ''
  && scopedLinkFormColumns.value.length === 0
  && allLinkFormColumnsFlat.value.length > 0
)

/** Defaults = simple sub-task: table-only + row subForm + mirror initiator; MI setups pick form-below / linkForm / summary in the UI. */
const DEFAULT_VIEWS: PortalViews = {
  assigneeTodo: 'tableOnly',
  assigneeTodoFormSource: { type: 'subForm', formId: null, linkFormColumnId: null },
  initiatorRequest: 'mirrorTodo'
}

const current = computed<PortalViews>(() => {
  const v = props.modelValue
  if (!v || typeof v !== 'object') return { ...DEFAULT_VIEWS, assigneeTodoFormSource: { ...DEFAULT_VIEWS.assigneeTodoFormSource! } }
  let rawType = v.assigneeTodoFormSource?.type ?? 'subForm'
  if (rawType === 'formId') rawType = 'subForm'
  const formSrcType: FormSourceType = rawType === 'linkForm' ? 'linkForm' : 'subForm'
  return {
    assigneeTodo: v.assigneeTodo === 'tableOnly' ? 'tableOnly' : 'formBelowTable',
    assigneeTodoFormSource: {
      type: formSrcType,
      formId: null,
      linkFormColumnId: v.assigneeTodoFormSource?.linkFormColumnId ?? null
    },
    initiatorRequest:
      v.initiatorRequest === 'summaryWithLinkFormModal'
        ? 'summaryWithLinkFormModal'
        : v.initiatorRequest === 'tableOnly'
          ? 'tableOnly'
          : 'mirrorTodo'
  }
})

const showFormSource = computed(() => current.value.assigneeTodo === 'formBelowTable')
const showLinkFormColumnPicker = computed(
  () => showFormSource.value && current.value.assigneeTodoFormSource?.type === 'linkForm'
)

function updateAssigneeTodo(val: AssigneeTodoMode) {
  emit('update:modelValue', {
    ...current.value,
    assigneeTodo: val
  })
}

function updateFormSourceType(val: FormSourceType) {
  const cur = current.value
  emit('update:modelValue', {
    ...cur,
    assigneeTodoFormSource: {
      type: val,
      formId: null,
      linkFormColumnId: val === 'linkForm' ? cur.assigneeTodoFormSource?.linkFormColumnId ?? null : null
    }
  })
}

function updateLinkFormColumnId(val: number | string | null) {
  const cur = current.value
  emit('update:modelValue', {
    ...cur,
    assigneeTodoFormSource: {
      type: cur.assigneeTodoFormSource?.type ?? 'linkForm',
      formId: null,
      linkFormColumnId: val ?? null
    }
  })
}

function updateInitiatorRequest(val: InitiatorRequestMode) {
  emit('update:modelValue', {
    ...current.value,
    initiatorRequest: val
  })
}

const injectedSubBindings = inject<() => Array<{
  id: number
  tableName: string
  tableDisplayName?: string
  tableId?: number
}>>('designerSubBindings', () => [])

function resolveBoundSubTableLabel(col: DesignerLinkFormColumnInfo): string {
  if (col.boundSubTableName) return col.boundSubTableName
  return resolveBindingDisplayName(
    col.boundSubTableBindingId,
    injectedSubBindings().map(b => ({
      bindingId: b.id,
      tableName: b.tableName,
      tableDisplayName: b.tableDisplayName,
      tableId: b.tableId,
    })),
  )
}

function getLinkFormColumnLabel(col: DesignerLinkFormColumnInfo, forcePrefix = false): string {
  const left = col.columnLabel || `linkForm:${col.componentId}`
  // A Link Form column whose `boundSubTableBindingId` equals its source binding is the
  // common "generic" case from SubTableListView — it opens the row's own subForm.
  // Don't decorate with " → self" in that case; just show the column label.
  const selfReferential =
    col.boundSubTableBindingId != null && col.boundSubTableBindingId === col.sourceBindingId
  const target =
    selfReferential
      ? ''
      : resolveBoundSubTableLabel(col)
  // Show the source binding prefix in two cases:
  //   1. Editor is invoked without a specific bindingId (rule-level form-create panel)
  //   2. Picker is in fallback mode (scoped lookup empty → cross-binding pool shown)
  const needsPrefix = forcePrefix || props.bindingId == null
  if (!needsPrefix) {
    return target ? `${left} → ${target}` : left
  }
  return target
    ? `[${col.sourceBindingName}] ${left} → ${target}`
    : `[${col.sourceBindingName}] ${left}`
}
</script>

<template>
  <div class="portal-views-editor">
    <el-collapse
      v-model="openPanels"
      class="portal-views-collapse"
    >
      <el-collapse-item
        name="pv"
        :title="collapseHeaderTitle"
      >
        <div class="portal-views-groups-row">
      <div class="portal-views-group portal-views-group--todo">
        <div class="portal-views-todo-row">
          <div class="row row--inline">
            <label class="row-label">{{ t('form.portalViews.toDoDisplay') }}</label>
            <el-select
              :model-value="current.assigneeTodo"
              size="small"
              class="row-select"
              @change="updateAssigneeTodo($event)"
            >
              <el-option
                value="formBelowTable"
                :label="t('form.portalViews.optTodoFormBelow')"
              />
              <el-option
                value="tableOnly"
                :label="t('form.portalViews.optTodoTableOnly')"
              />
            </el-select>
          </div>

          <div
            v-if="showFormSource"
            class="row row--inline"
          >
            <label class="row-label">{{ t('form.portalViews.toDoFormSource') }}</label>
            <el-select
              :model-value="current.assigneeTodoFormSource?.type ?? 'subForm'"
              size="small"
              class="row-select"
              @change="updateFormSourceType($event)"
            >
              <el-option
                value="subForm"
                :label="t('form.portalViews.optSourceSubForm')"
              />
              <el-option
                value="linkForm"
                :label="t('form.portalViews.optSourceLinkForm')"
              />
            </el-select>
          </div>

          <div
            v-if="showLinkFormColumnPicker"
            class="row row--inline row--inline-grow"
          >
            <label class="row-label">{{ t('form.portalViews.linkFormName') }}</label>
            <el-select
              :model-value="current.assigneeTodoFormSource?.linkFormColumnId ?? null"
              clearable
              size="small"
              class="row-select"
              :placeholder="t('form.portalViews.linkFormColumnPlaceholder')"
              @change="updateLinkFormColumnId($event ?? null)"
            >
              <el-option
                v-for="col in availableLinkFormColumns"
                :key="String(col.componentId)"
                :value="col.componentId"
                :label="getLinkFormColumnLabel(col, showingFallbackScope)"
              />
              <template
                v-if="availableLinkFormColumns.length === 0"
                #empty
              >
                <span class="el-select-dropdown__empty">{{ t('form.portalViews.linkFormColumnEmpty') }}</span>
              </template>
            </el-select>
          </div>
        </div>
        <div
          v-if="showLinkFormColumnPicker && showingFallbackScope"
          class="row-hint row-hint--full"
        >
          {{ t('form.portalViews.linkFormScopeFallback') }}
        </div>
      </div>

      <div class="portal-views-group portal-views-group--initiator">
        <div class="row">
          <label class="row-label">{{ t('form.portalViews.myRequestsDisplay') }}</label>
          <el-select
            :model-value="current.initiatorRequest"
            size="small"
            class="row-select"
            @change="updateInitiatorRequest($event)"
          >
            <el-option
              value="mirrorTodo"
              :label="t('form.portalViews.optInitiatorMirrorTodo')"
            />
            <el-option
              value="summaryWithLinkFormModal"
              :label="t('form.portalViews.optInitiatorSummaryModal')"
            />
            <el-option
              value="tableOnly"
              :label="t('form.portalViews.optInitiatorTableOnly')"
            />
          </el-select>
        </div>
      </div>
    </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.portal-views-editor {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.portal-views-collapse {
  width: 100%;
  border: none;
  --el-collapse-border-color: transparent;
}

.portal-views-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.portal-views-collapse :deep(.el-collapse-item__header) {
  min-height: 36px;
  height: auto;
  line-height: 1.3;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 4px;
  color: var(--el-text-color-primary);
  background: transparent;
}

.portal-views-collapse :deep(.el-collapse-item__content) {
  padding: 0 4px 10px;
}

.portal-views-collapse :deep(.el-collapse-item__arrow) {
  margin: 0 6px 0 0;
}

.portal-views-groups-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 12px;
}

.portal-views-groups-row > .portal-views-group--todo {
  flex: 2 1 320px;
  min-width: 260px;
}

.portal-views-groups-row > .portal-views-group--initiator {
  flex: 1 1 200px;
  min-width: 180px;
}

.portal-views-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.portal-views-group--todo .portal-views-todo-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px 16px;
}

.portal-views-group--todo .row--inline {
  flex: 1 1 0;
  min-width: 140px;
}

.portal-views-group--todo .row--inline-grow {
  flex: 1.35 1 220px;
  min-width: 200px;
}

.portal-views-group--initiator {
  background: var(--el-fill-color-light, var(--el-fill-color-blank));
}

.portal-views-editor .row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.portal-views-editor .row-select {
  width: 100%;
}

.portal-views-editor .row-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.portal-views-editor .row-hint {
  font-size: 11px;
  color: var(--el-color-warning);
  line-height: 1.4;
  margin-top: 2px;
}

.portal-views-editor .row-hint--full {
  margin-top: 0;
}
</style>

<template>
  <SubTableNestedModalShell
    :visible="visible"
    :title="title || (mode === 'edit' ? t('common.edit') : t('common.add'))"
    width="min(700px, calc(100vw - 48px))"
    @update:visible="emit('update:visible', $event)"
    @closed="handleClosed"
  >
    <div
      v-if="formRule && formRule.length"
      class="sub-table-form-preview form-readonly-surface"
      :style="{ '--mi-label-min-width': assignmentLabelMinWidth }"
    >
      <form-create
        v-if="formCreateMounted"
        v-model="formData"
        locale="en"
        :rule="formRule"
        :option="formOption"
      />
      <div
        v-else
        class="form-loading"
      >
        <el-icon class="is-loading">
          <Loading />
        </el-icon>
        <span>{{ t('common.loading') }}...</span>
      </div>
    </div>

    <el-empty
      v-else
      :description="t('subTable.noFormDesign')"
      :image-size="60"
    />

    <template
      v-if="!hideFooter"
      #footer
    >
      <el-button @click="emit('update:visible', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="handleSave"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </SubTableNestedModalShell>
</template>

<script setup lang="ts">
import { computed, provide, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import SubTableNestedModalShell from './SubTableNestedModalShell.vue'
import { cloneFormRules, collectUploadRulesFromTree, injectPreviewUploadHandlers } from '@/utils/formDesigner'
import { syncDesignerComponentEventsForFcPreview } from '@/utils/formCreatePreviewEvents'
import { mapFormCreateRulesReadonlyDeep } from '@/utils/formCreateRuleUtils'
import {
  alignUploadFieldsToColumns,
  hydrateUploadFieldsForFormCreate,
  normalizeUploadFieldsInRow,
  resolveUploadCellUrl,
} from './uploadFieldUtils'
import { mergeFormRowWithSeed } from './subTableAddDialogHelpers'
import {
  MI_ASSIGNMENT_CONFIG_KEY,
  MI_ASSIGNMENT_MODE_KEY,
  fieldsHiddenByMode,
  fieldsOwnedByMode,
  isAssignmentConfigured,
  resolveAssignModeFromRow,
  type AssignmentConfig,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'

export interface SubTableFormDialogProps {
  visible: boolean
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  /** Form-create rule from the sub-table form designer */
  rule?: any[]
  /** Form-create option from the sub-table form designer */
  option?: any
  /** Form Preview: read-only link-form view — no Cancel/Save footer */
  hideFooter?: boolean
  /** Form Preview: all form-create fields disabled (view only) */
  readOnly?: boolean
  /** List columns — align upload field names on save (preview table display) */
  columns?: Array<{ field: string; type?: string; props?: Record<string, unknown> }>
  /** BPMN-derived assignment contract for this Sub Table. */
  assignmentConfig?: AssignmentConfig
}

const props = withDefaults(defineProps<SubTableFormDialogProps>(), {
  mode: 'add',
  rule: () => [],
  option: () => ({}),
  hideFooter: false,
  readOnly: false,
  columns: () => [],
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

const { t } = useI18n()

const formData = ref<Record<string, any>>({})
const formCreateMounted = ref(false)
const uploadSession = ref<Record<string, { url: string; name?: string }>>({})
const assignmentConfigRef = computed(() => props.assignmentConfig)
provide(MI_ASSIGNMENT_CONFIG_KEY, assignmentConfigRef)

const defaultFormOption = {
  resetBtn: false,
  submitBtn: false,
  showMsg: true,
  form: {
    labelPosition: 'left',
    labelWidth: 'auto',
  },
  language: {
    en: {
      clickToUpload: t('form.clickToUpload'),
    },
  },
  onSubmit: () => {},
}

function buildDialogFormOption(option: Record<string, any> = {}) {
  const { title: _dropTitle, form: optionForm, ...rest } = option || {}
  return {
    ...defaultFormOption,
    ...rest,
    resetBtn: false,
    submitBtn: false,
    // Same as Form-mode Preview: component on.blur/change receive inject.api.
    injectEvent: true,
    onSubmit: () => {},
    form: {
      ...defaultFormOption.form,
      ...(optionForm && typeof optionForm === 'object' ? optionForm : {}),
      // 弹窗内强制 auto + left：EP 取最长 label 宽度整列对齐、文字左对齐，
      // 长 label 不折行不错位（设计器配置的固定值/right 也覆盖）
      labelWidth: 'auto',
      labelPosition: 'left',
      ...(props.readOnly ? { disabled: true } : {}),
    },
  }
}

const formOption = ref(buildDialogFormOption(props.option))
const formRule = ref<any[]>([])

/**
 * `labelWidth: 'auto'` re-measures against whichever fields are currently visible, so
 * switching assignment mode ("Assignee" ⇄ "Business Unit" / "Role") moved every other
 * row's input edge sideways. Publish a floor equal to the widest label the dialog can
 * ever show — measured across BOTH modes — so the column is already wide enough for the
 * other branch and nothing shifts on toggle. `auto` still governs above the floor, so a
 * genuinely longer label elsewhere is never clipped or wrapped.
 */
const assignmentLabelMinWidth = ref<string>('')

function syncAssignmentLabelMinWidth(): void {
  const config = props.assignmentConfig
  if (!config || !isAssignmentConfigured(config)) {
    assignmentLabelMinWidth.value = ''
    return
  }
  const owned = new Set(
    [config.assigneeField, config.buField, config.roleField]
      .filter((field): field is string => !!field),
  )
  // Titles come from the raw rule so hidden-by-mode fields are included too.
  const titles: string[] = []
  const walk = (list: any[]) => {
    for (const rule of list || []) {
      if (rule?.field && owned.has(rule.field) && rule.title) titles.push(String(rule.title))
      if (Array.isArray(rule?.children)) walk(rule.children)
    }
  }
  walk(rawRule.value || [])
  if (titles.length === 0) {
    assignmentLabelMinWidth.value = ''
    return
  }

  const ruler = document.createElement('span')
  ruler.style.cssText =
    'position:absolute;visibility:hidden;white-space:nowrap;left:-9999px;top:-9999px;font:14px sans-serif;'
  document.body.appendChild(ruler)
  let widest = 0
  for (const title of titles) {
    ruler.textContent = title
    widest = Math.max(widest, ruler.getBoundingClientRect().width)
  }
  ruler.remove()
  // Element Plus adds the label's right padding on top of the text itself.
  assignmentLabelMinWidth.value = widest > 0 ? `${Math.ceil(widest) + 12}px` : ''
}
/** 未按 assignMode 过滤的原始 rule（radio 切换时据此重建 formRule）。 */
const rawRule = ref<any[]>([])
/** Allocated PK / FK values seeded on add — merged back on save when form-create omits disabled fields. */
const seedRow = ref<Record<string, any>>({})

watch(
  () => [
    props.visible,
    props.initialData,
    props.mode,
    props.rule,
    props.option,
    props.readOnly,
    props.assignmentConfig,
  ] as const,
  ([open, data, mode, rule, option]) => {
    if (!open) {
      formCreateMounted.value = false
      return
    }
    formCreateMounted.value = false
    uploadSession.value = {}
    rawRule.value = (rule || []) as any[]
    if (mode === 'edit' && data) {
      seedRow.value = { ...(data as Record<string, any>) }
      formData.value = { ...seedRow.value }
    } else if (mode === 'add' && data) {
      seedRow.value = { ...(data as Record<string, any>) }
      formData.value = { ...seedRow.value }
    } else {
      seedRow.value = {}
      formData.value = {}
    }
    const assignmentConfig = props.assignmentConfig
    if (assignmentConfig && isAssignmentConfigured(assignmentConfig)) {
      assignMode.value = resolveAssignModeFromRow(
        (data || {}) as Record<string, unknown>,
        assignmentConfig,
      )
    }
    rebuildFormRule()
    hydrateUploadFieldsForFormCreate(formData.value, collectUploadRulesFromTree(formRule.value))
    formOption.value = buildDialogFormOption(option || {})
    nextTick(() => {
      formCreateMounted.value = true
    })
  },
  { immediate: true },
)

const assignMode = ref<AssignmentMode>('person')

// The only channel that reaches the container widget (see MI_ASSIGNMENT_MODE_KEY):
// form-create does not forward rule.props/rule.on to `input: false` components.
provide(MI_ASSIGNMENT_MODE_KEY, {
  mode: assignMode,
  setMode: (mode: AssignmentMode) => onAssignmentModeChange(mode),
})

/** A container rule for sub-forms designed before Assignment Mode owned its fields. */
function makeAssignmentContainerRule(children: any[]): any {
  return { type: 'miAssignment', props: {}, children }
}

/**
 * Give the Assignment Mode container its children, so the block and its pickers
 * render as one nested unit. Mirrors nestAssignmentFieldsIntoContainer() plus
 * ensureAssignmentBlockPlaced() in the Portal — keep in sync.
 *
 * Forms saved before the container existed keep assignee / BU / role as siblings;
 * here they are folded into the container (inserted at the first owned field when
 * no container rule exists yet). Nothing is dropped: every field rule survives,
 * only its depth in the tree changes, so bindings and validation stay intact.
 */
function nestAssignmentChildren(list: any[], order: string[]): any[] {
  if (order.length === 0) return list
  const owned = new Set(order)
  // Author order wins: children already inside the container keep their arrangement,
  // and only fields still loose outside it get appended. The designer owns placement,
  // so preview must not re-sort into contract order.
  const nestedFirst: any[] = []
  const looseByField = new Map<string, any>()
  for (const rule of list) {
    if (rule?.type === 'miAssignment') {
      for (const child of (rule.children ?? [])) {
        if (child?.field && owned.has(child.field)) nestedFirst.push(child)
      }
    } else if (rule?.field && owned.has(rule.field)) {
      looseByField.set(rule.field, rule)
    }
  }
  const alreadyNested = new Set(nestedFirst.map(child => child?.field))
  const appended = order
    .filter(field => !alreadyNested.has(field))
    .map(field => looseByField.get(field))
    .filter(Boolean)
  const children = [...nestedFirst, ...appended]
  if (children.length === 0) return list

  const rest = list.filter(rule => !(rule?.field && owned.has(rule.field)))
  const containerAt = rest.findIndex(rule => rule?.type === 'miAssignment')
  if (containerAt >= 0) {
    return rest.map(rule =>
      rule?.type === 'miAssignment' ? { ...rule, children } : rule)
  }
  // No container yet — place it where its first field already sat, counting only
  // the non-owned rules before that point so nothing shifts unexpectedly.
  const anchorAt = list.findIndex(rule => rule?.field && owned.has(rule.field))
  if (anchorAt < 0) return list
  const keptBefore = list
    .slice(0, anchorAt)
    .filter(rule => !(rule?.field && owned.has(rule.field))).length
  return [
    ...rest.slice(0, keptBefore),
    makeAssignmentContainerRule(children),
    ...rest.slice(keptBefore),
  ]
}

function filterRuleByAssignMode(rules: any[]): any[] {
  const config = props.assignmentConfig
  const configured = !!config && isAssignmentConfigured(config)
  const hidden = configured ? fieldsHiddenByMode(assignMode.value, config) : new Set<string>()
  // Children are the active mode's fields only; the other mode's are filtered out.
  const order = configured ? fieldsOwnedByMode(assignMode.value, config!) : []
  // The container is a non-field component (drag rule `input: false`): form-create
  // forwards neither rule.props nor rule.on to it, so mode + setter reach the widget
  // through provide(MI_ASSIGNMENT_MODE_KEY) instead. Nothing to decorate here.
  const decorateContainer = (rule: any) => rule
  const walk = (list: any[]): any[] =>
    nestAssignmentChildren(
      (list || [])
        .filter(r => {
          // Single-mode setups keep the container too: it holds their one picker.
          if (r?.type === 'miAssignment') return configured
          return !(r?.field && hidden.has(r.field))
        })
        .map(r => {
          if (!r) return r
          if (r.type === 'miAssignment') {
            // Drop children hidden by the active mode before nesting re-seats them.
            const kept = (r.children ?? []).filter(
              (child: any) => !(child?.field && hidden.has(child.field)))
            return decorateContainer({ ...r, children: kept })
          }
          return Array.isArray(r.children) ? { ...r, children: walk(r.children) } : r
        }),
      order,
    )
  return walk(rules)
}

// Form Preview 只演示布局/交互，BU/Role 用示例假数据（不查 admin-center）；
// 真实 BU→Role 级联在 user portal 运行时（SubTableAddDialog）里查。
// 纯占位示例（仅供 Preview 看布局/交互）；真实 BU→Role 在 user portal 运行时查。
const DEMO_BU_OPTIONS = [
  { label: 'Sample Business Unit 1', value: '__demo_bu_1' },
  { label: 'Sample Business Unit 2', value: '__demo_bu_2' },
]
const DEMO_ROLE_OPTIONS = [
  { label: 'Sample Role A', value: '__demo_role_a' },
  { label: 'Sample Role B', value: '__demo_role_b' },
]

function injectDemoBuRoleOptions(rules: any[]) {
  const config = props.assignmentConfig
  if (!config) return
  const walk = (list: any[]) => {
    for (const r of list || []) {
      if (r && config.buField && r.field === config.buField) r.options = DEMO_BU_OPTIONS
      else if (r && config.roleField && r.field === config.roleField) r.options = DEMO_ROLE_OPTIONS
      if (r && Array.isArray(r.children)) walk(r.children)
    }
  }
  walk(rules)
}

function rebuildFormRule() {
  const filtered = filterRuleByAssignMode(
    mapFormCreateRulesReadonlyDeep(cloneFormRules(rawRule.value || [])) as any[]
  )
  injectDemoBuRoleOptions(filtered)
  formRule.value = filtered
  // Parent Preview may have sanitized $FNX strings off `on`/`hook` (crash guard).
  // Recompile from `_on`/`_hook` so sub-form component events run like Form-mode Preview.
  syncDesignerComponentEventsForFcPreview(formRule.value)
  injectPreviewUploadHandlers(formRule.value, formData, uploadSession)
  // Recompute from rawRule (all modes), so the floor does not depend on the active one.
  syncAssignmentLabelMinWidth()
}

function onAssignmentModeChange(value: unknown) {
  if (value !== 'person' && value !== 'role') return
  assignMode.value = value
  const config = props.assignmentConfig
  if (!config) return
  for (const field of fieldsHiddenByMode(value, config)) formData.value[field] = ''
  formCreateMounted.value = false
  rebuildFormRule()
  nextTick(() => { formCreateMounted.value = true })
}

function handleClosed() {
  formCreateMounted.value = false
  formData.value = {}
  seedRow.value = {}
  uploadSession.value = {}
}

function handleSave() {
  const row = mergeFormRowWithSeed(seedRow.value, formData.value)
  const uploadRules = collectUploadRulesFromTree(formRule.value)
  const uploadRuleFields = uploadRules.map((r) => r.field)

  for (const [field, entry] of Object.entries(uploadSession.value)) {
    if (entry.url && !resolveUploadCellUrl(row[field])) {
      row[field] = entry.url
    }
  }

  normalizeUploadFieldsInRow(row, uploadRules)

  if (props.columns?.length) {
    alignUploadFieldsToColumns(row, props.columns, uploadRuleFields)
    normalizeUploadFieldsInRow(row, props.columns)
  }

  emit('save', row)
  emit('update:visible', false)
}
</script>

<style scoped>
@import '@/styles/form-readonly.scss';

.sub-table-form-preview {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
}

/* form-create adjacent elCard (e.g. Participants Title cards) — parity with Portal */
.sub-table-form-preview :deep(.el-card) {
  margin-bottom: 10px;
}

/* ── Assignment Mode container ────────────────────────────────────────────────
   The widget now owns its fields as nested children and draws its own complete
   frame, so no external border stitching is needed here — only the block's own
   width and the gap below it. Mirrors user-portal SubTableAddDialog. */
.sub-table-form-preview :deep(.mi-assignment-widget) {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 18px;
}

/* Hold the label column at the widest label either assignment mode can show (see
   syncAssignmentLabelMinWidth). Element Plus sets `min-width: max-content` on labels in
   auto mode, so this needs the extra .el-form specificity (and !important) to win;
   `labelWidth: auto` still governs above the floor, so longer labels never wrap. */
.sub-table-form-preview :deep(.el-form .el-form-item__label) {
  min-width: var(--mi-label-min-width, 0) !important;
}

.form-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  color: #909399;
}
</style>

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
    >
      <!-- MI 场景 C：分派方式 radio 由 rebuildFormRule 注入到 form-create rule 中，插在分派字段组正上方 -->
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
import { ref, watch, nextTick, computed } from 'vue'
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
/** 未按 assignMode 过滤的原始 rule（radio 切换时据此重建 formRule）。 */
const rawRule = ref<any[]>([])
/** Allocated PK / FK values seeded on add — merged back on save when form-create omits disabled fields. */
const seedRow = ref<Record<string, any>>({})

watch(
  () => [props.visible, props.initialData, props.mode, props.rule, props.option, props.readOnly] as const,
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
    // 初始 radio：已填 role/bu → role，否则 person（仅场景 C 有 radio）。
    if (showAssignModeRadio.value) {
      const d = (data || {}) as Record<string, any>
      const roleFilled = (d.role_code && String(d.role_code).trim()) || (d.bu_code && String(d.bu_code).trim())
      assignMode.value = roleFilled ? 'role' : 'person'
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

/**
 * MI 子任务「按个人 / 按角色」二选一（form-create Form Preview 侧）：
 * 场景 C（表单同时含 assignee 与 role_code/bu_code 字段）时顶部显示 radio，
 * 选中哪种就只渲染哪组字段，从根上杜绝一行两种方式。与运行时 SubTableAddDialog 一致。
 */
const ROLE_GROUP = ['bu_code', 'role_code']

function ruleHasField(rules: any[], field: string): boolean {
  for (const r of rules || []) {
    if (r && r.field === field) return true
    if (r && Array.isArray(r.children) && ruleHasField(r.children, field)) return true
  }
  return false
}

const hasAssigneeField = computed(() => ruleHasField(rawRule.value, 'assignee'))
const hasRoleFields = computed(() =>
  ROLE_GROUP.some(f => ruleHasField(rawRule.value, f)))
const showAssignModeRadio = computed(() => hasAssigneeField.value && hasRoleFields.value)
const assignMode = ref<'person' | 'role'>('person')

/** 按 assignMode 从 rule 树里剔除另一组字段（person 去 bu/role，role 去 assignee）。 */
function filterRuleByAssignMode(rules: any[]): any[] {
  if (!showAssignModeRadio.value) return rules
  const drop = assignMode.value === 'person' ? ROLE_GROUP : ['assignee']
  const walk = (list: any[]): any[] =>
    (list || [])
      .filter(r => !(r && drop.includes(r.field)))
      .map(r => (r && Array.isArray(r.children) ? { ...r, children: walk(r.children) } : r))
  return walk(rules)
}

/** 构造 form-create 原生 radio rule，插到分派字段组正上方，与字段成一体。 */
function buildAssignModeRadioRule(): any {
  return {
    type: 'radio',
    field: '__assignMode',
    title: t('subTable.assignMode'),
    value: assignMode.value,
    options: [
      { label: t('subTable.assignByPerson'), value: 'person' },
      { label: t('subTable.assignByRole'), value: 'role' },
    ],
    props: { button: false },
    on: {
      change: (v: string) => {
        if (v === 'person' || v === 'role') {
          assignMode.value = v
          onAssignModeChange()
        }
      },
    },
  }
}

/** 在顶层 rule 数组里，把 radio 插到第一个分派字段（assignee/bu_code/role_code）之前。 */
function insertAssignModeRadio(rules: any[]): any[] {
  if (!showAssignModeRadio.value) return rules
  const targets = new Set(['assignee', ...ROLE_GROUP])
  const idx = rules.findIndex(r => r && targets.has(r.field))
  const out = [...rules]
  out.splice(idx < 0 ? 0 : idx, 0, buildAssignModeRadioRule())
  return out
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

/** 给 Preview 的 bu_code/role_code 字段填充示例 options（form-create select）。 */
function injectDemoBuRoleOptions(rules: any[]) {
  const walk = (list: any[]) => {
    for (const r of list || []) {
      if (r && r.field === 'bu_code') r.options = DEMO_BU_OPTIONS
      else if (r && r.field === 'role_code') r.options = DEMO_ROLE_OPTIONS
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
  formRule.value = insertAssignModeRadio(filtered)
  // Parent Preview may have sanitized $FNX strings off `on`/`hook` (crash guard).
  // Recompile from `_on`/`_hook` so sub-form component events run like Form-mode Preview.
  syncDesignerComponentEventsForFcPreview(formRule.value)
  injectPreviewUploadHandlers(formRule.value, formData, uploadSession)
}

function onAssignModeChange() {
  // 清掉另一组的值，重建 rule 并 remount form-create。
  if (assignMode.value === 'person') {
    formData.value.bu_code = ''
    formData.value.role_code = ''
  } else {
    formData.value.assignee = ''
  }
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

.form-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 200px;
  color: #909399;
}
</style>

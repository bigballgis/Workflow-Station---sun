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
      class="sub-table-form-preview"
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
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import SubTableNestedModalShell from './SubTableNestedModalShell.vue'
import { cloneFormRules, collectUploadRulesFromTree, injectPreviewUploadHandlers } from '@/utils/formDesigner'
import {
  alignUploadFieldsToColumns,
  hydrateUploadFieldsForFormCreate,
  normalizeUploadFieldsInRow,
  resolveUploadCellUrl,
} from './uploadFieldUtils'

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
    labelWidth: '140px',
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
    onSubmit: () => {},
    form: {
      ...defaultFormOption.form,
      ...(optionForm && typeof optionForm === 'object' ? optionForm : {}),
      ...(props.readOnly ? { disabled: true } : {}),
    },
  }
}

const formOption = ref(buildDialogFormOption(props.option))
const formRule = ref<any[]>([])

watch(
  () => [props.visible, props.initialData, props.mode, props.rule, props.option, props.readOnly] as const,
  ([open, data, mode, rule, option]) => {
    if (!open) {
      formCreateMounted.value = false
      return
    }
    formCreateMounted.value = false
    uploadSession.value = {}
    formRule.value = cloneFormRules(rule || [])
    injectPreviewUploadHandlers(formRule.value, formData, uploadSession)
    if (mode === 'edit' && data) {
      formData.value = { ...(data as Record<string, any>) }
    } else {
      formData.value = {}
    }
    hydrateUploadFieldsForFormCreate(formData.value, collectUploadRulesFromTree(formRule.value))
    formOption.value = buildDialogFormOption(option || {})
    nextTick(() => {
      formCreateMounted.value = true
    })
  },
  { immediate: true },
)

function handleClosed() {
  formCreateMounted.value = false
  formData.value = {}
  uploadSession.value = {}
}

function handleSave() {
  const row = { ...formData.value }
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
.sub-table-form-preview {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
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

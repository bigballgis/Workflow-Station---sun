<template>
  <div
    class="process-debug-node-form"
    :class="{ 'process-debug-node-form--expanded': expanded }"
  >
    <el-empty
      v-if="!binding"
      :description="emptyDescription"
    />
    <div
      v-else-if="loading"
      v-loading="true"
      class="loading-box"
    />
    <el-alert
      v-else-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />
    <template v-else>
      <div class="form-meta">
        <span class="form-title">{{ displayFormName }}</span>
        <el-tag
          size="small"
          type="info"
        >
          {{ binding.nodeType }}
        </el-tag>
        <el-tag
          v-if="binding.readOnly"
          size="small"
          type="warning"
        >
          {{ t('debug.formReadOnly') }}
        </el-tag>
        <el-tag
          v-if="miInstanceLabel"
          size="small"
          type="success"
        >
          {{ miInstanceLabel }}
        </el-tag>
      </div>
      <div class="form-preview-wrap">
        <FormPreviewItems
          v-if="previewItems.length > 0"
          v-model:preview-data="previewData"
          v-model:preview-table-rows="previewTableRows"
          :items="previewItems"
          :preview-option="previewOption"
        />
        <el-empty
          v-else
          :description="t('form.noFormContent')"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { functionUnitApi, type FormDefinition, type TableBinding } from '@/api/functionUnit'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import FormPreviewItems from '@/components/designer/FormPreviewItems.vue'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import type { BpmnNodeFormBinding } from '@/utils/bpmnFormBindings'
import { buildSavedFormPreviewItems } from '@/utils/savedFormPreviewBuilder'

interface MiContext {
  instanceIndex?: number
  totalInstances?: number
  currentItem?: Record<string, unknown>
  phase?: string
}

const props = defineProps<{
  functionUnitId: number
  binding: BpmnNodeFormBinding | null
  miContext?: MiContext | null
  expanded?: boolean
}>()

const { t } = useI18n()
const store = useFunctionUnitStore()

const loading = ref(false)
const loadError = ref('')
const previewItems = ref<FormPreviewItem[]>([])
const previewData = ref<Record<string, unknown>>({})
const previewTableRows = ref<Record<number, any[]>>({})
const previewOption = { submitBtn: false, resetBtn: false }
const resolvedFormName = ref('')

const emptyDescription = computed(() => {
  if (!props.binding) return t('debug.noNodeForm')
  return t('debug.noNodeFormOnStep')
})

const displayFormName = computed(() => {
  return props.binding?.formName || resolvedFormName.value || `Form #${props.binding?.formId}`
})

const miInstanceLabel = computed(() => {
  const ctx = props.miContext
  if (!ctx?.instanceIndex || !ctx.totalInstances || ctx.phase !== 'instance') return ''
  return t('debug.miInstanceProgress', {
    current: ctx.instanceIndex,
    total: ctx.totalInstances,
  })
})

function applyMiRowToPreviewData(currentItem?: Record<string, unknown> | null) {
  if (!currentItem) return
  const next = { ...previewData.value }
  for (const [key, value] of Object.entries(currentItem)) {
    if (key === 'rowId') continue
    next[key] = value
  }
  previewData.value = next
}

async function loadFormPreview() {
  previewItems.value = []
  previewData.value = {}
  previewTableRows.value = {}
  loadError.value = ''
  resolvedFormName.value = ''

  const binding = props.binding
  if (!binding?.formId) return

  loading.value = true
  try {
    await Promise.all([
      store.fetchForms(props.functionUnitId),
      store.fetchTables(props.functionUnitId),
    ])

    let form: FormDefinition | undefined = store.forms.find(f => f.id === binding.formId)
    if (!form) {
      loadError.value = t('debug.nodeFormNotFound', { id: binding.formId })
      return
    }

    let extraBindings: TableBinding[] = form.tableBindings || []
    try {
      const res = await functionUnitApi.getFormBindings(props.functionUnitId, binding.formId)
      if (res.data?.length) {
        extraBindings = res.data
        form = { ...form, tableBindings: res.data }
      }
    } catch {
      // use form.tableBindings from list API
    }

    resolvedFormName.value = form.formName
    previewItems.value = buildSavedFormPreviewItems({
      form,
      tables: store.tables,
      tableBindings: extraBindings,
      t,
    })

    for (const item of previewItems.value) {
      if (item.kind === 'subTable') {
        previewTableRows.value[item.binding.bindingId] = []
      }
    }
    applyMiRowToPreviewData(props.miContext?.currentItem)
  } catch (e: any) {
    loadError.value = e?.message || t('debug.nodeFormLoadFailed')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.binding?.nodeId,
  () => {
    void loadFormPreview()
  },
  { immediate: true },
)

watch(
  () => props.miContext?.currentItem,
  (currentItem) => {
    applyMiRowToPreviewData(currentItem ?? null)
  },
  { deep: true },
)
</script>

<style lang="scss" scoped>
.process-debug-node-form {
  min-height: 200px;
  display: flex;
  flex-direction: column;
}

.process-debug-node-form--expanded {
  flex: 1;
  min-height: 0;

  .form-preview-wrap {
    max-height: none;
    flex: 1;
    min-height: 320px;
  }
}

.loading-box {
  min-height: 160px;
}

.form-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .form-title {
    font-weight: 600;
    font-size: 14px;
  }
}

.form-preview-wrap {
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}
</style>

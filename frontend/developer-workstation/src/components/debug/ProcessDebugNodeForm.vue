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
        <div
          v-if="lookupItems.length"
          class="lookup-probe-toolbar"
        >
          <span class="lookup-probe-title">{{ t('debug.lookupProbe') }}</span>
          <div
            v-for="lookup in lookupItems"
            :key="lookup.key"
            class="lookup-probe-item"
          >
            <span class="lookup-probe-label">{{ lookup.label }}</span>
            <el-input
              v-model="lookupKeywords[lookup.key]"
              size="small"
              :placeholder="t('debug.lookupProbeKeywordPlaceholder')"
            />
            <el-button
              size="small"
              :disabled="!canProbeLookup(lookup) || lookupProbeUnavailable"
              :loading="probingLookupKey === lookup.key"
              @click="handleLookupProbe(lookup)"
            >
              {{ t('debug.lookupProbeRun') }}
            </el-button>
          </div>
          <p
            v-if="lookupProbeUnavailable"
            class="lookup-probe-hint"
          >
            {{ t('debug.lookupProbeUnavailable') }}
          </p>
        </div>
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
      <el-dialog
        v-model="showLookupProbeDialog"
        :title="t('debug.lookupProbeResultTitle')"
        width="760px"
      >
        <div class="table-scroll-wrap">
        <el-table
          v-if="lookupProbeRows.length"
          :data="lookupProbeRows"
          border
          size="small"
          height="360"
          @row-click="handleLookupRowPick"
        >
          <el-table-column
            v-for="col in lookupProbeColumns"
            :key="col.fieldName"
            :prop="col.fieldName"
            :label="col.label || col.fieldName"
            min-width="160"
          />
        </el-table>
        </div>
        <el-empty
          v-if="!lookupProbeRows.length"
          :description="t('debug.lookupProbeNoRows')"
        />
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  functionUnitApi,
  type DebugLookupProbeResult,
  type FormDefinition,
  type TableBinding,
} from '@/api/functionUnit'
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
const emit = defineEmits<{
  (e: 'lookup-probe-log', payload: { message: string; detail?: Record<string, any> }): void
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
const lookupKeywords = ref<Record<string, string>>({})
const probingLookupKey = ref('')
const lookupProbeUnavailable = ref(false)
const showLookupProbeDialog = ref(false)
const lookupProbeColumns = ref<Array<{ fieldName: string; label?: string }>>([])
const lookupProbeRows = ref<Array<Record<string, any>>>([])

type LookupPreviewItem = Extract<FormPreviewItem, { kind: 'lookup' }>
type ProbeLookupItem = LookupPreviewItem & { key: string }

const lookupItems = computed<ProbeLookupItem[]>(() =>
  previewItems.value
    .filter((item): item is LookupPreviewItem => item.kind === 'lookup')
    .map((item, index) => ({
      ...item,
      key: `${item.bindingId ?? 'unknown'}-${index}`,
    })),
)

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
  lookupKeywords.value = {}
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

function canProbeLookup(item: ProbeLookupItem): boolean {
  return Number.isFinite(Number(item.bindingId))
}

async function handleLookupProbe(item: ProbeLookupItem) {
  const bindingId = Number(item.bindingId)
  if (!Number.isFinite(bindingId) || !props.binding?.formId) return
  probingLookupKey.value = item.key
  try {
    const result = await functionUnitApi.debugLookupProbe(props.functionUnitId, {
      formId: props.binding.formId,
      bindingId,
      lookupConfig: {
        searchFields: item.searchFields,
        displayFields: item.displayFields,
        selectedDisplayField: item.selectedDisplayField,
        filterConditions: item.filterConditions || [],
      },
      keyword: lookupKeywords.value[item.key] || '',
      runtimeVariables: previewData.value,
      page: 0,
      size: 20,
      searchMode: 'contains',
    })
    applyLookupProbeResult(result.data)
    emit('lookup-probe-log', {
      message: t('debug.lookupProbeSucceeded', {
        label: item.label,
        count: result.data.total ?? result.data.rows?.length ?? 0,
      }),
      detail: { bindingId, keyword: lookupKeywords.value[item.key] || '' },
    })
  } catch (e: any) {
    const status = Number(e?.response?.status)
    if (status === 404 || status === 501) {
      lookupProbeUnavailable.value = true
    }
    emit('lookup-probe-log', {
      message: t('debug.lookupProbeFailed', { label: item.label }),
      detail: { error: e?.response?.data?.error?.message || e?.message || 'unknown_error' },
    })
  } finally {
    probingLookupKey.value = ''
  }
}

function applyLookupProbeResult(result: DebugLookupProbeResult) {
  lookupProbeColumns.value = result.columns || []
  lookupProbeRows.value = result.rows || []
  showLookupProbeDialog.value = true
}

function handleLookupRowPick(row: Record<string, any>) {
  previewData.value = {
    ...previewData.value,
    ...row,
  }
  emit('lookup-probe-log', {
    message: t('debug.lookupProbeRowApplied'),
    detail: { fields: Object.keys(row).length },
  })
  showLookupProbeDialog.value = false
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

.lookup-probe-toolbar {
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  background: #fafafa;
}

.lookup-probe-title {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  color: #606266;
}

.lookup-probe-item {
  display: grid;
  grid-template-columns: 140px 1fr auto;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.lookup-probe-label {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lookup-probe-hint {
  margin: 0;
  font-size: 12px;
  color: #909399;
}
</style>

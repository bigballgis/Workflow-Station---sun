<template>
  <div
    v-loading="loading"
    class="called-function-unit-section"
  >
    <el-empty
      v-if="!loading && instances.length === 0"
      :description="t('calledFunctionUnit.none')"
    />

    <div
      v-for="group in groupedByCallActivity"
      :key="group.callActivityId"
      class="call-group"
    >
      <div class="call-group-header">
        <span class="call-group-title">{{ group.title }}</span>
        <span
          v-if="group.instances.length > 1"
          class="call-group-count"
        >
          {{ t('calledFunctionUnit.instanceCount', { count: group.instances.length }) }}
        </span>
      </div>

      <el-card
        v-for="instance in group.instances"
        :key="instance.processInstanceId"
        class="called-instance"
        shadow="never"
      >
        <div class="called-instance-header">
          <div class="called-instance-identity">
            <span class="called-unit-name">
              {{ instance.functionUnitName || instance.functionUnitCode }}
            </span>
            <el-tag
              :type="statusType(instance.status)"
              size="small"
            >
              {{ statusLabel(instance.status) }}
            </el-tag>
          </div>
          <div class="called-instance-progress">
            <span v-if="instance.currentNode">
              {{ t('calledFunctionUnit.currentStep', { step: instance.currentNode }) }}
            </span>
            <span v-else-if="isTerminal(instance.status)">
              {{ t('calledFunctionUnit.finished') }}
            </span>
          </div>
        </div>

        <!-- Designed layout: the called unit's own form (the call step's "Form to Show Its
             Data"), read-only. Its fields, labels, order and sub-tables come from that unit's
             Form Design. -->
        <div
          v-if="childFormView(instance)"
          class="called-instance-form"
        >
          <!-- One label width for the whole form, so every input starts at the same edge. -->
          <el-form
            label-position="left"
            label-width="160px"
            size="default"
          >
            <PortalFormFields
              :fields="childFormView(instance)!.fields"
              :model="instance.formData || {}"
              :sub-table-bindings="childFormView(instance)!.bindings"
              readonly
            />
          </el-form>
        </div>
        <el-descriptions
          v-else-if="displayFields(instance).length > 0"
          :column="2"
          size="small"
          border
          class="called-instance-data"
        >
          <el-descriptions-item
            v-for="field in displayFields(instance)"
            :key="field.key"
            :label="field.label"
          >
            {{ field.value }}
          </el-descriptions-item>
        </el-descriptions>
        <div
          v-else
          class="called-instance-empty"
        >
          {{ t('calledFunctionUnit.noData') }}
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { processApi, type CalledFunctionUnitInstance } from '@/api/process'
import PortalFormFields, { type PortalSubTableBindingLite } from './PortalFormFields.vue'
import type { FormField } from './FormRenderer.vue'
import { createFieldExtractor } from '@/composables/processStart/useProcessStartFieldExtractor'

const { t } = useI18n()

const props = defineProps<{
  processId: string
}>()

const loading = ref(false)
const instances = ref<CalledFunctionUnitInstance[]>([])

/** Internal bookkeeping that is not business data and would only clutter the read-only view. */
const NON_BUSINESS_KEYS = new Set([
  '__subTables__',
  'initiator',
  'functionUnitId',
  'functionUnitCode',
  'decision',
  'action',
  'approvalStatus',
  'approverComments',
])

const TERMINAL_STATUSES = new Set(['COMPLETED', 'REJECTED', 'WITHDRAWN'])

function isTerminal(status: string) {
  return TERMINAL_STATUSES.has(status)
}

function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    RUNNING: 'warning',
    COMPLETED: 'success',
    WITHDRAWN: 'info',
    REJECTED: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    RUNNING: t('application.running'),
    COMPLETED: t('application.completed'),
    WITHDRAWN: t('application.withdrawn'),
    REJECTED: t('application.rejected'),
  }
  return map[status] || status
}

/** Shared field extraction (the New Request page's), with no lookup/relation-view overrides. */
const extractFields = createFieldExtractor({ lookupDbConfigs: ref({}), relationViewConfigs: ref({}) })
  .extractFieldsRecursive

interface ChildFormView {
  fields: FormField[]
  bindings: PortalSubTableBindingLite[]
}

const childFormViews = new WeakMap<object, ChildFormView | null>()

/**
 * The child's data laid out by its configured form, or null when the call step has none (or the
 * form cannot be read). Sub-table fields of that form show the child's own rows, read-only.
 */
function childFormView(instance: CalledFunctionUnitInstance): ChildFormView | null {
  const form = instance.childForm
  if (!form) return null
  if (childFormViews.has(form)) return childFormViews.get(form)!
  let view: ChildFormView | null = null
  try {
    const config = typeof form.data === 'string' ? JSON.parse(form.data) : (form.data || {})
    const rules = Array.isArray(config.rule) ? config.rule : []
    const subForms: Record<string, { rule?: unknown[] }> = config.subForms || {}
    const subTables = (instance.formData?.__subTables__ || {}) as Record<string, unknown>
    const bindings = (form.tableBindings || [])
      .filter((b) => b.bindingType !== 'PRIMARY' && b.bindingId != null)
      .map((b): PortalSubTableBindingLite => {
        const designed = extractFields((subForms[String(b.bindingId)]?.rule as any[]) || [])
        const columns = designed.length > 0
          ? designed.map((f) => ({ field: f.key, label: f.label, type: f.type }))
          : (b.fieldDefinitions || []).map((d) => ({ field: d.fieldName, label: d.displayName || d.fieldName }))
        const rows = subTables[`dw:${b.tableName}`]
        return {
          bindingId: Number(b.bindingId),
          tableName: b.tableDisplayName || b.tableName,
          designerTableName: b.tableName,
          columns,
          formFields: designed,
          data: Array.isArray(rows) ? rows : [],
          bindingType: b.bindingType,
          bindingMode: 'READONLY',
          fieldDefinitions: b.fieldDefinitions as any,
        }
      })
    view = { fields: extractFields(rules), bindings }
  } catch (error) {
    console.warn('Could not render called Function Unit form', error)
  }
  childFormViews.set(form, view)
  return view
}

/**
 * Scalar business fields of the child, flattened for display.
 *
 * Sub-table rows are deliberately left out: rendering them properly means the full binding
 * machinery, and this section exists to answer "what did the sub-process conclude", not to be a
 * second form renderer.
 */
function displayFields(instance: CalledFunctionUnitInstance) {
  const data = instance.formData
  if (!data) return []
  // Without a configured form, list the called unit's main-table fields under their designed
  // names, in design order — not every engine variable under its technical name.
  if (instance.childFields?.length) {
    return instance.childFields
      .map((f) => ({ key: f.fieldName, label: f.displayName || f.fieldName, value: data[f.fieldName] }))
      .filter((f) => f.value !== null && f.value !== undefined && f.value !== '' && typeof f.value !== 'object')
      .map((f) => ({ ...f, value: String(f.value) }))
  }
  return Object.entries(data)
    .filter(([key, value]) => {
      if (NON_BUSINESS_KEYS.has(key)) return false
      if (value === null || value === undefined || value === '') return false
      // Objects and arrays are sub-table rows or nested structures, not scalars.
      return typeof value !== 'object'
    })
    .map(([key, value]) => ({ key, label: key, value: String(value) }))
}

/**
 * Children grouped by the call step that started them, so a multi-instance call reads as one
 * labelled group of N rather than N unrelated cards.
 */
const groupedByCallActivity = computed(() => {
  const groups = new Map<string, { callActivityId: string; title: string; instances: CalledFunctionUnitInstance[] }>()
  for (const instance of instances.value) {
    const id = instance.callActivityId || instance.processInstanceId
    if (!groups.has(id)) {
      groups.set(id, {
        callActivityId: id,
        title: instance.callActivityName
          || instance.functionUnitName
          || instance.functionUnitCode
          || t('calledFunctionUnit.untitledCall'),
        instances: [],
      })
    }
    groups.get(id)!.instances.push(instance)
  }
  return [...groups.values()]
})

async function load() {
  if (!props.processId) return
  loading.value = true
  try {
    const res = await processApi.getCalledFunctionUnits(props.processId)
    instances.value = Array.isArray(res) ? res : ((res as { data?: CalledFunctionUnitInstance[] })?.data ?? [])
  } catch (error) {
    console.error('Failed to load called function units', error)
    instances.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.processId, load)
onMounted(load)

defineExpose({ reload: load })
</script>

<style scoped lang="scss">
.called-function-unit-section {
  min-height: 120px;
}

.call-group {
  margin-bottom: 20px;

  &:last-child {
    margin-bottom: 0;
  }
}

.call-group-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.call-group-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.call-group-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.called-instance {
  margin-bottom: 10px;
  border: 1px solid var(--el-border-color-lighter);

  &:last-child {
    margin-bottom: 0;
  }
}

.called-instance-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.called-instance-identity {
  display: flex;
  align-items: center;
  gap: 8px;
}

.called-unit-name {
  font-weight: 500;
}

.called-instance-progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.called-instance-form {
  padding-top: 4px;
}

.called-instance-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

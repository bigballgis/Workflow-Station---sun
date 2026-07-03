<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { lookupStore } from './lookupStore'
import { relationTableBindingApi } from '@/api/relationTable'
import {
  type LookupFilterCondition,
  getLookupFilterMatchOptions,
  isBooleanDataType,
  isDateDataType,
  isNumericDataType,
  isTimestampDataType,
  normalizeLookupFilterCondition,
  normalizeLookupFilterMatchType,
  serializeLookupFilterValue,
} from '@/utils/lookupFilterConditions'

interface FieldInfo {
  fieldName: string
  dataType: string
  isPrimaryKey: boolean
  displayName?: string
  scale?: number
}

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': (val: string) => void
}>()

const { t } = useI18n()
// Use module-level store — fc-designer registers this component in its own Vue app context,
// so provide/inject from FormDesigner doesn't reach here.
function goToViewDesign() {
  if (selectedBindingId.value != null) lookupStore.switchToBinding?.(selectedBindingId.value)
}

const relationBindings = computed(() => lookupStore.relationBindings)

// Internal state
const selectedBindingId = ref<number | null>(null)
const searchFields = ref<string[]>([])
const displayFields = ref<string[]>([])
const selectedDisplayField = ref('')
const filterConditions = ref<LookupFilterCondition[]>([])
const showBackfillView = ref(true)
// Fields loaded from API for deployed relation tables
const apiFields = ref<FieldInfo[]>([])
const fieldsLoading = ref(false)

// Get fields: first try local store, then use API-loaded fields
const availableFields = computed<FieldInfo[]>(() => {
  if (!selectedBindingId.value) return []
  const binding = relationBindings.value.find(b => b.bindingId === selectedBindingId.value)
  if (!binding) return []

  // Try local dw_table_definitions first
  const table = lookupStore.tables.find((t: any) => t.id === binding.tableId)
  if (table) {
    const fields = (table as any).fieldDefinitions || (table as any).fields || []
    if (fields.length > 0) {
      return fields.map((f: any) => ({
        fieldName: f.fieldName,
        dataType: f.dataType,
        isPrimaryKey: f.isPrimaryKey ?? false,
        displayName: f.displayName || '',
        scale: f.scale,
      }))
    }
  }

  // Fall back to API-loaded fields (for deployed relation tables)
  return apiFields.value
})

async function loadFieldsFromApi(tableId: number) {
  fieldsLoading.value = true
  try {
    const res = await relationTableBindingApi.getAvailableTables()
    const tables = (res as any)?.data || res || []
    const rtTable = tables.find((t: any) => t.id === tableId)
    if (rtTable?.fieldDefinitions) {
      const fields = rtTable.fieldDefinitions.map((f: any) => ({
        fieldName: f.fieldName,
        dataType: f.dataType,
        isPrimaryKey: f.isPrimaryKey ?? false,
        displayName: f.displayName || '',
      }))
      apiFields.value = fields
      // Cache in lookupStore so FormDesigner preview can use it
      lookupStore.rtFieldCache[tableId] = fields
    } else {
      apiFields.value = []
    }
  } catch {
    apiFields.value = []
  } finally {
    fieldsLoading.value = false
  }
}

function parseModelValue() {
  try {
    const cfg = JSON.parse(props.modelValue || '{}')
    selectedBindingId.value = cfg.bindingId ?? null
    searchFields.value = cfg.searchFields ?? []
    displayFields.value = cfg.displayFields ?? []
    selectedDisplayField.value = cfg.selectedDisplayField || cfg.displayField || ''
    filterConditions.value = Array.isArray(cfg.filterConditions)
      ? cfg.filterConditions
        .map((condition: unknown) => normalizeLookupFilterCondition(condition))
        .filter((condition): condition is LookupFilterCondition => condition != null)
      : []
    showBackfillView.value = cfg.showBackfillView !== false
  } catch {
    selectedBindingId.value = null
    searchFields.value = []
    displayFields.value = []
    selectedDisplayField.value = ''
    filterConditions.value = []
    showBackfillView.value = true
  }
}

function emitUpdate() {
  const binding = relationBindings.value.find(b => b.bindingId === selectedBindingId.value)
  const cfg = {
    bindingId: selectedBindingId.value,
    tableId: binding?.tableId ?? null,
    tableName: binding?.tableName ?? '',
    searchFields: searchFields.value,
    displayFields: displayFields.value,
    selectedDisplayField: selectedDisplayField.value,
    filterConditions: filterConditions.value
      .filter(condition => condition.fieldName && condition.value !== '')
      .map(condition => ({
        fieldName: condition.fieldName,
        value: condition.value,
        matchType: normalizeLookupFilterMatchType(condition.matchType),
      })),
    showBackfillView: showBackfillView.value,
  }
  emit('update:modelValue', JSON.stringify(cfg))
}

function getFieldLabel(f: FieldInfo): string {
  const name = f.displayName || f.fieldName
  return f.isPrimaryKey ? `🔑 ${name} (PK)` : name
}

function handleBindingChange(val: number | null) {
  selectedBindingId.value = val
  searchFields.value = []
  displayFields.value = []
  selectedDisplayField.value = ''
  filterConditions.value = []
  apiFields.value = []
  if (val) {
    const binding = relationBindings.value.find(b => b.bindingId === val)
    if (binding) {
      // Check if fields are available locally
      const table = lookupStore.tables.find((t: any) => t.id === binding.tableId)
      const localFields = (table as any)?.fieldDefinitions || (table as any)?.fields || []
      if (localFields.length === 0) {
        // Load from API for deployed relation tables
        loadFieldsFromApi(binding.tableId).then(() => {
          autoSelectPK()
          emitUpdate()
        })
        return
      }
    }
    autoSelectPK()
  }
  emitUpdate()
}

function autoSelectPK() {
  const pkFields = availableFields.value
    .filter(f => f.isPrimaryKey)
    .map(f => f.fieldName)
  if (pkFields.length > 0) {
    searchFields.value = pkFields
  }
}

function handleSearchFieldsChange(val: string[]) {
  searchFields.value = val
  emitUpdate()
}

function handleDisplayFieldsChange(val: string[]) {
  displayFields.value = val
  if (selectedDisplayField.value && !availableFields.value.some(f => f.fieldName === selectedDisplayField.value)) {
    selectedDisplayField.value = ''
  }
  emitUpdate()
}

function handleSelectedDisplayFieldChange(val: string) {
  selectedDisplayField.value = val
  emitUpdate()
}

function getFieldInfo(fieldName: string): FieldInfo | undefined {
  return availableFields.value.find(f => f.fieldName === fieldName)
}

function getMatchOptionsForField(fieldName: string) {
  return getLookupFilterMatchOptions(getFieldInfo(fieldName)?.dataType)
}

function addFilterCondition() {
  filterConditions.value.push({ fieldName: '', value: '', matchType: 'eq' })
  emitUpdate()
}

function handleFilterFieldChange(condition: LookupFilterCondition) {
  const field = getFieldInfo(condition.fieldName)
  const allowed = getLookupFilterMatchOptions(field?.dataType).map(option => option.value)
  if (!allowed.includes(normalizeLookupFilterMatchType(condition.matchType))) {
    condition.matchType = 'eq'
  }
  condition.value = ''
  handleFilterConditionChange()
}

function parseFilterNumberValue(raw: string): number | undefined {
  if (raw.trim() === '') return undefined
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : undefined
}

function getNumericPrecision(field?: FieldInfo): number {
  const dt = (field?.dataType || '').toUpperCase()
  return dt.includes('DECIMAL') || dt.includes('NUMERIC') ? (field?.scale ?? 2) : 0
}

function removeFilterCondition(index: number) {
  filterConditions.value.splice(index, 1)
  emitUpdate()
}

function handleFilterConditionChange() {
  emitUpdate()
}

function handleShowBackfillViewChange(val: string | number | boolean) {
  showBackfillView.value = Boolean(val)
  emitUpdate()
}

// Parse modelValue whenever it changes
watch(() => props.modelValue, parseModelValue, { immediate: true })

// When binding is set from saved config, load fields if needed
watch(selectedBindingId, (val) => {
  if (val && availableFields.value.length === 0) {
    const binding = relationBindings.value.find(b => b.bindingId === val)
    if (binding) {
      loadFieldsFromApi(binding.tableId)
    }
  }
}, { immediate: true })
</script>

<template>
  <div class="lookup-binding-select">
    <div class="lookup-field-group">
      <label class="lookup-label">Relation Table</label>
      <el-select
        :model-value="selectedBindingId"
        clearable
        placeholder="Select relation table"
        style="width: 100%"
        @change="handleBindingChange"
      >
        <el-option
          v-for="b in relationBindings"
          :key="b.bindingId"
          :value="b.bindingId"
          :label="b.tableDescription ? `${b.tableName} (${b.tableDescription})` : b.tableName"
        />
        <template
          v-if="relationBindings.length === 0"
          #empty
        >
          <span class="el-select-dropdown__empty">No relation tables bound</span>
        </template>
      </el-select>
      <a
        v-if="selectedBindingId && lookupStore.switchToBinding"
        class="binding-nav-link"
        href="#"
        @click.prevent="goToViewDesign"
      >{{ t('form.lookupGoToViewDesign') }}</a>
    </div>

    <template v-if="selectedBindingId">
      <div class="lookup-field-group">
        <label class="lookup-label">Search Fields</label>
        <el-select
          :model-value="searchFields"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="Select search fields"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleSearchFieldsChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <label class="lookup-label">Display Fields</label>
        <el-select
          :model-value="displayFields"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="Select display fields"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleDisplayFieldsChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <label class="lookup-label">Selected Display Field</label>
        <el-select
          :model-value="selectedDisplayField"
          clearable
          placeholder="Field shown after selecting a row"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleSelectedDisplayFieldChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <div class="lookup-label-row">
          <label class="lookup-label">Fixed Filters</label>
          <el-button
            link
            type="primary"
            size="small"
            @click="addFilterCondition"
          >
            Add
          </el-button>
        </div>
        <div
          v-if="filterConditions.length === 0"
          class="lookup-empty-hint"
        >
          Optional: pre-filter rows by field value (exact or fuzzy) before lookup search.
        </div>
        <div
          v-for="(condition, index) in filterConditions"
          :key="index"
          class="lookup-filter-row"
        >
          <el-select
            v-model="condition.fieldName"
            filterable
            clearable
            placeholder="Field"
            :loading="fieldsLoading"
            @change="handleFilterFieldChange(condition)"
          >
            <el-option
              v-for="f in availableFields"
              :key="f.fieldName"
              :value="f.fieldName"
              :label="getFieldLabel(f)"
            />
          </el-select>
          <el-select
            v-model="condition.matchType"
            placeholder="Match"
            :disabled="!condition.fieldName"
            @change="handleFilterConditionChange"
          >
            <el-option
              v-for="option in getMatchOptionsForField(condition.fieldName)"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
          <el-select
            v-if="condition.fieldName && isBooleanDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            clearable
            placeholder="Value"
            @change="handleFilterConditionChange"
          >
            <el-option
              label="True"
              value="true"
            />
            <el-option
              label="False"
              value="false"
            />
          </el-select>
          <el-input-number
            v-else-if="condition.fieldName && isNumericDataType(getFieldInfo(condition.fieldName)?.dataType)"
            :model-value="parseFilterNumberValue(condition.value)"
            :precision="getNumericPrecision(getFieldInfo(condition.fieldName))"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(val: number | undefined) => { condition.value = serializeLookupFilterValue(val); handleFilterConditionChange() }"
          />
          <el-date-picker
            v-else-if="condition.fieldName && isDateDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="Value"
            style="width: 100%"
            @change="handleFilterConditionChange"
          />
          <el-date-picker
            v-else-if="condition.fieldName && isTimestampDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="Value"
            style="width: 100%"
            @change="handleFilterConditionChange"
          />
          <el-input
            v-else
            v-model="condition.value"
            placeholder="Value"
            clearable
            :disabled="!condition.fieldName"
            @input="handleFilterConditionChange"
          />
          <el-button
            link
            type="danger"
            size="small"
            @click="removeFilterCondition(index)"
          >
            Remove
          </el-button>
        </div>
      </div>

      <div class="lookup-field-group">
        <label class="lookup-label">Backfill View</label>
        <el-switch
          :model-value="showBackfillView"
          active-text="Yes"
          inactive-text="No"
          @change="handleShowBackfillViewChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.lookup-binding-select {
  width: 100%;
}
.lookup-field-group {
  margin-bottom: 8px;
}
.lookup-label {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.lookup-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.lookup-label-row .lookup-label {
  margin-bottom: 0;
}
.lookup-filter-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.9fr) minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.lookup-empty-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.binding-nav-link {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  text-decoration: none;
}
.binding-nav-link:hover {
  text-decoration: underline;
}
</style>

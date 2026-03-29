<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { lookupStore } from './lookupStore'
import { relationTableBindingApi } from '@/api/relationTable'

interface FieldInfo {
  fieldName: string
  dataType: string
  isPrimaryKey: boolean
  description?: string
}

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': (val: string) => void
}>()

const relationBindings = computed(() => lookupStore.relationBindings)

// Internal state
const selectedBindingId = ref<number | null>(null)
const searchFields = ref<string[]>([])
const displayFields = ref<string[]>([])
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
        description: f.description || f.comment || '',
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
        description: f.comment || '',
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
  } catch {
    selectedBindingId.value = null
    searchFields.value = []
    displayFields.value = []
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
  }
  emit('update:modelValue', JSON.stringify(cfg))
}

function getFieldLabel(f: FieldInfo): string {
  const name = f.description || f.fieldName
  return f.isPrimaryKey ? `🔑 ${name} (PK)` : name
}

function handleBindingChange(val: number | null) {
  selectedBindingId.value = val
  searchFields.value = []
  displayFields.value = []
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
        <template v-if="relationBindings.length === 0" #empty>
          <span class="el-select-dropdown__empty">No relation tables bound</span>
        </template>
      </el-select>
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
</style>

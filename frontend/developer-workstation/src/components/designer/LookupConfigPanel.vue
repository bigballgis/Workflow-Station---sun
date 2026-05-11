<template>
  <div class="lookup-config-panel">
    <el-form
      label-width="120px"
      label-position="top"
      size="small"
    >
      <el-form-item label="Relation Table">
        <el-select
          v-model="config.tableId"
          placeholder="Select bound table"
          style="width: 100%"
          @change="handleTableChange"
        >
          <el-option
            v-for="view in boundViews"
            :key="view.tableId"
            :label="view.displayName || view.tableName"
            :value="view.tableId"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="View Config">
        <el-select
          v-model="config.viewConfigId"
          placeholder="Select view config"
          style="width: 100%"
        >
          <el-option
            v-for="view in boundViews"
            :key="view.viewConfigId"
            :label="`${view.displayName || view.tableName} View`"
            :value="view.viewConfigId"
            :disabled="!view.viewConfigId"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Search Fields">
        <el-select
          v-model="searchFieldList"
          multiple
          placeholder="Select search fields"
          style="width: 100%"
        >
          <el-option
            v-for="field in availableFields"
            :key="field.fieldName"
            :label="field.fieldName"
            :value="field.fieldName"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Display Field">
        <el-select
          v-model="config.displayField"
          placeholder="Select display field"
          style="width: 100%"
        >
          <el-option
            v-for="field in availableFields"
            :key="field.fieldName"
            :label="field.fieldName"
            :value="field.fieldName"
          />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          Save Config
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  relationTableLookupApi,
  relationTableViewApi,
  type BoundViewDTO,
  type RelationFieldDTO,
  type LookupConfigDTO
} from '@/api/relationTable'

const props = defineProps<{
  formId: number
  componentId: string
}>()

const saving = ref(false)
const boundViews = ref<BoundViewDTO[]>([])
const availableFields = ref<RelationFieldDTO[]>([])
const config = ref<LookupConfigDTO>({
  viewConfigId: null,
  tableId: 0,
  searchFields: '[]',
  displayField: ''
})

const searchFieldList = computed({
  get: () => {
    try {
      return JSON.parse(config.value.searchFields || '[]')
    } catch {
      return []
    }
  },
  set: (val: string[]) => {
    config.value.searchFields = JSON.stringify(val)
  }
})

async function loadBoundViews() {
  try {
    const res = await relationTableLookupApi.getBoundViews(props.formId, props.componentId)
    boundViews.value = res.data || []
  } catch {
    boundViews.value = []
  }
}

async function loadExistingConfig() {
  try {
    const res = await relationTableLookupApi.getLookupConfig(props.formId, props.componentId)
    if (res.data) {
      config.value = {
        viewConfigId: res.data.viewConfigId,
        tableId: res.data.tableId,
        searchFields: res.data.searchFields || '[]',
        displayField: res.data.displayField || ''
      }
      if (res.data.tableId) {
        await loadFieldsForTable(res.data.tableId)
      }
    }
  } catch {
    // No existing config
  }
}

async function loadFieldsForTable(tableId: number) {
  // Find the binding for this table to get bindingId
  const view = boundViews.value.find(v => v.tableId === tableId)
  if (!view) return
  try {
    const res = await relationTableViewApi.getAvailableFields(props.formId, view.bindingId)
    availableFields.value = res.data || []
  } catch {
    availableFields.value = []
  }
}

async function handleTableChange(tableId: number) {
  config.value.viewConfigId = null
  config.value.searchFields = '[]'
  config.value.displayField = ''
  // Auto-select view config
  const view = boundViews.value.find(v => v.tableId === tableId)
  if (view?.viewConfigId) {
    config.value.viewConfigId = view.viewConfigId
  }
  await loadFieldsForTable(tableId)
}

async function handleSave() {
  saving.value = true
  try {
    await relationTableLookupApi.saveLookupConfig(props.formId, props.componentId, config.value)
    ElMessage.success('Lookup config saved')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || 'Failed to save')
  } finally {
    saving.value = false
  }
}

watch(() => props.formId, async () => {
  if (props.formId && props.componentId) {
    await loadBoundViews()
    await loadExistingConfig()
  }
}, { immediate: true })

onMounted(async () => {
  if (props.formId && props.componentId) {
    await loadBoundViews()
    await loadExistingConfig()
  }
})
</script>

<style lang="scss" scoped>
.lookup-config-panel {
  padding: 12px;
}
</style>

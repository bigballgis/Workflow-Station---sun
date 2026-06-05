<template>
  <div class="relation-view-designer">
    <div class="designer-header">
      <span class="title">View Designer - {{ tableName }}</span>
      <el-button
        type="primary"
        size="small"
        :loading="saving"
        @click="handleSave"
      >
        Save
      </el-button>
    </div>

    <div
      v-loading="loading"
      class="field-list table-scroll-wrap"
    >
      <el-table
        :data="viewFields"
        size="small"
        row-key="fieldName"
      >
        <el-table-column
          label="Visible"
          width="70"
        >
          <template #default="{ row }">
            <el-checkbox v-model="row.visible" />
          </template>
        </el-table-column>
        <el-table-column
          prop="fieldName"
          label="Field Name"
          min-width="120"
        />
        <el-table-column
          label="Display Label"
          min-width="140"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.displayLabel"
              size="small"
              placeholder="Display label"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Column Width"
          width="120"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.columnWidth"
              size="small"
              :min="50"
              :max="500"
              :step="10"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="Sort Order"
          width="100"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.sortOrder"
              size="small"
              :min="0"
              :max="100"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="viewFields.length === 0 && !loading"
        description="No fields available"
        :image-size="60"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  relationTableViewApi,
  type ViewFieldDTO,
  type RelationFieldDTO
} from '@/api/relationTable'

const props = defineProps<{
  formId: number
  bindingId: number
  tableName?: string
}>()

const loading = ref(false)
const saving = ref(false)
const viewFields = ref<(ViewFieldDTO & { visible: boolean })[]>([])

async function loadViewConfig() {
  loading.value = true
  try {
    // Load available fields
    const fieldsRes = await relationTableViewApi.getAvailableFields(props.formId, props.bindingId)
    const availableFields: RelationFieldDTO[] = fieldsRes.data || []

    // Load existing view config
    let existingFields: ViewFieldDTO[] = []
    try {
      const configRes = await relationTableViewApi.getViewConfig(props.formId, props.bindingId)
      existingFields = configRes.data?.viewFields || []
    } catch {
      // No existing config, use defaults
    }

    // Merge: existing config takes priority, add new fields
    const existingMap = new Map(existingFields.map(f => [f.fieldName, f]))
    viewFields.value = availableFields.map((field, index) => {
      const existing = existingMap.get(field.fieldName)
      return {
        fieldName: field.fieldName,
        displayLabel: existing?.displayLabel || field.displayName || field.fieldName,
        columnWidth: existing?.columnWidth || 150,
        sortOrder: existing?.sortOrder ?? index,
        visible: existing?.visible ?? true
      }
    })
  } catch {
    viewFields.value = []
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const fields: ViewFieldDTO[] = viewFields.value.map(f => ({
      fieldName: f.fieldName,
      displayLabel: f.displayLabel,
      columnWidth: f.columnWidth,
      sortOrder: f.sortOrder,
      visible: f.visible
    }))
    await relationTableViewApi.saveViewConfig(props.formId, props.bindingId, fields)
    ElMessage.success('View config saved')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || 'Failed to save')
  } finally {
    saving.value = false
  }
}

watch(() => props.bindingId, () => {
  if (props.bindingId) loadViewConfig()
}, { immediate: true })

onMounted(() => {
  if (props.bindingId) loadViewConfig()
})
</script>

<style lang="scss" scoped>
.relation-view-designer {
  .designer-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .title {
      font-weight: 500;
      font-size: 14px;
    }
  }
}
</style>

<template>
  <el-dialog
    v-model="visible"
    :title="t('common.export')"
    width="480px"
  >
    <div class="export-dialog-body">
      <div class="export-stat">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('audit.exportEstimate', { n: total }) }}</span>
      </div>
      <div class="export-fields-label">
        {{ t('audit.exportSelectFields') }}
      </div>
      <div class="export-fields">
        <el-checkbox
          v-model="selectAll"
          :indeterminate="indeterminate"
          @change="handleSelectAll"
        >
          {{ t('common.all') }}
        </el-checkbox>
        <el-divider style="margin: 8px 0" />
        <el-checkbox-group
          v-model="selectedFields"
          @change="handleFieldChange"
        >
          <el-checkbox
            v-for="f in exportFields"
            :key="f.key"
            :value="f.key"
          >
            {{ f.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        plain
        :loading="exporting"
        :disabled="selectedFields.length === 0"
        @click="emit('export', 'csv')"
      >
        <el-icon><Download /></el-icon>CSV
      </el-button>
      <el-button
        type="primary"
        :loading="exporting"
        :disabled="selectedFields.length === 0"
        @click="emit('export', 'excel')"
      >
        <el-icon><Download /></el-icon>Excel
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, InfoFilled } from '@element-plus/icons-vue'

const { t } = useI18n()

export interface ExportField {
  key: string
  label: string
}

const props = defineProps<{
  modelValue: boolean
  total: number
  exporting: boolean
  exportFields: ExportField[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:selectedFields': [value: string[]]
  'update:selectAll': [value: boolean]
  'update:indeterminate': [value: boolean]
  'export': [format: 'csv' | 'excel', fields: string[]]
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const selectedFields = ref<string[]>([])
const selectAll = ref(true)
const indeterminate = ref(false)

// Sync props → local state on dialog open
watch(() => props.modelValue, (v) => {
  if (v) {
    selectedFields.value = props.exportFields.map(f => f.key)
    selectAll.value = true
    indeterminate.value = false
    emit('update:selectedFields', [...selectedFields.value])
    emit('update:selectAll', true)
    emit('update:indeterminate', false)
  }
}, { immediate: true })

const handleSelectAll = (val: boolean) => {
  selectedFields.value = val ? props.exportFields.map(f => f.key) : []
  indeterminate.value = false
  emit('update:selectedFields', [...selectedFields.value])
  emit('update:selectAll', val)
  emit('update:indeterminate', false)
}

const handleFieldChange = (val: string[]) => {
  const total = props.exportFields.length
  selectAll.value = val.length === total
  indeterminate.value = val.length > 0 && val.length < total
  emit('update:selectedFields', [...val])
  emit('update:selectAll', selectAll.value)
  emit('update:indeterminate', indeterminate.value)
}
</script>

<style scoped>
.export-dialog-body { display: flex; flex-direction: column; gap: 12px; }

.export-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  font-size: 13px;
  color: #409eff;
}

.export-fields-label {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}

.export-fields {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 14px;
}

.export-fields :deep(.el-checkbox) {
  display: block;
  margin-bottom: 6px;
}
</style>

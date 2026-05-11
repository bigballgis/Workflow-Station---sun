<template>
  <el-dialog
    :model-value="modelValue"
    :title="'Version History - ' + (tableName || '')"
    width="800px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-table
      v-loading="loading"
      :data="versions"
      stripe
      empty-text="No versions found"
    >
      <el-table-column
        prop="versionNumber"
        label="Version"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          v{{ row.versionNumber }}
        </template>
      </el-table-column>
      <el-table-column
        prop="deployedBy"
        label="Deployed By"
        width="140"
      />
      <el-table-column
        prop="deployedAt"
        label="Deployed At"
        width="180"
      >
        <template #default="{ row }">
          {{ formatDate(row.deployedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="changeLog"
        label="Change Log"
        min-width="200"
      >
        <template #default="{ row }">
          {{ row.changeLog || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="Actions"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            link
            type="warning"
            size="small"
            @click="handleRollback(row)"
          >
            Rollback
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        Close
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  relationTableStructureApi,
  type RelationTableVersionResponse
} from '@/api/relationTable'

const props = defineProps<{
  modelValue: boolean
  tableId?: number
  tableName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'rollback-success': []
}>()

const loading = ref(false)
const versions = ref<RelationTableVersionResponse[]>([])

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const fetchVersions = async () => {
  if (!props.tableId) return
  loading.value = true
  try {
    versions.value = await relationTableStructureApi.getVersionHistory(props.tableId)
  } catch (e) {
    console.error('Failed to load versions:', e)
  } finally {
    loading.value = false
  }
}

const handleRollback = async (version: RelationTableVersionResponse) => {
  if (!props.tableId) return
  try {
    await ElMessageBox.confirm(
      `Rollback to version v${version.versionNumber}? This will update the table definition to match this version's snapshot.`,
      'Confirm Rollback',
      { type: 'warning' }
    )
    await relationTableStructureApi.rollback(props.tableId, { targetVersionId: version.id })
    ElMessage.success(`Rolled back to v${version.versionNumber}`)
    emit('update:modelValue', false)
    emit('rollback-success')
  } catch (e: unknown) {
    if (e !== 'cancel') {
      console.error('Rollback failed:', e)
    }
  }
}

watch(() => props.modelValue, (val) => {
  if (val) fetchVersions()
})
</script>

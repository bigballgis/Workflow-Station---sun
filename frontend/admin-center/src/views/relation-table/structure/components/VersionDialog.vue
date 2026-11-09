<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${t('form.titleVersionHistory')} - ${tableName || ''}`"
    width="800px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-table
      v-loading="loading"
      :data="versions"
      stripe
      :empty-text="t('relationTable.version.empty')"
    >
      <el-table-column
        prop="versionNumber"
        :label="t('form.labelVersion')"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          v{{ row.versionNumber }}
        </template>
      </el-table-column>
      <el-table-column
        prop="deployedBy"
        :label="t('form.labelDeployedBy')"
        width="140"
      />
      <el-table-column
        prop="deployedAt"
        :label="t('form.labelDeployedAt')"
        width="180"
      >
        <template #default="{ row }">
          {{ formatDate(row.deployedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="changeLog"
        :label="t('form.labelChangeLog')"
        min-width="200"
      >
        <template #default="{ row }">
          {{ row.changeLog || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="!readOnly"
        :label="t('common.actions')"
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
            {{ t('relationTable.structure.rollback') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifySuccess } from '@/utils/notify'
import { formatDate } from '@/utils/format'
import {
  relationTableStructureApi,
  type RelationTableVersionResponse
} from '@/api/relationTable'

const props = defineProps<{
  modelValue: boolean
  tableId?: number
  tableName?: string
  /** A table the user may not change (D-47): versions are listed, not rolled back. */
  readOnly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'rollback-success': []
}>()

const { t } = useI18n()
const loading = ref(false)
const versions = ref<RelationTableVersionResponse[]>([])

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
    await notifyConfirm(
      t('relationTable.version.rollbackConfirmMsg', { version: version.versionNumber }),
      t('relationTable.version.rollbackConfirmTitle'),
      { type: 'warning' }
    )
    await relationTableStructureApi.rollback(props.tableId, { targetVersionId: version.id })
    notifySuccess(t('relationTable.version.rolledBack', { version: version.versionNumber }))
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

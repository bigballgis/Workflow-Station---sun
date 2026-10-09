<template>
  <div class="version-manager table-scroll-wrap">
    <DesignerListTable
      :loading="loading"
      :storage-key="`${functionUnitId}:versions`"
      :columns="listColumns"
      :rows="() => store.versions"
      row-key="id"
      actions-align="center"
    >
      <template #cell-versionNumber="{ row }">
        <span>{{ row.versionNumber }}</span>
        <el-tag
          v-if="row.current"
          type="success"
          size="small"
          class="current-tag"
        >
          {{ t('version.currentActive') }}
        </el-tag>
      </template>
      <template #cell-createdAt="{ row }">
        {{ formatDate(row.createdAt) }}
      </template>
      <template #actions="{ row }">
        <div class="action-buttons">
          <el-button
            data-read-only-allowed
            size="small"
            link
            type="primary"
            @click="handleCompare(row)"
          >
            {{ t('common.compare') }}
          </el-button>
          <el-button
            size="small"
            link
            type="warning"
            :disabled="row.current"
            @click="handleRollback(row)"
          >
            {{ t('common.rollback') }}
          </el-button>
          <el-button
            data-read-only-allowed
            size="small"
            link
            type="success"
            @click="handleExport(row)"
          >
            {{ t('common.export') }}
          </el-button>
        </div>
      </template>
    </DesignerListTable>

    <VersionCompareDialog
      v-model="showCompareDialog"
      :function-unit-id="functionUnitId"
      :versions="store.versions"
      :initial-version-id="initialCompareVersionId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { Version } from '@/api/functionUnit'
import DesignerListTable from '@/components/designer-list/DesignerListTable.vue'
import VersionCompareDialog from '@/components/version/VersionCompareDialog.vue'
import type { DesignerListTableColumn } from '@/composables/useDesignerListGrid'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

const props = defineProps<{ functionUnitId: number }>()
const { t } = useI18n()
const store = useFunctionUnitStore()
const loading = ref(false)
const showCompareDialog = ref(false)
const initialCompareVersionId = ref<number>()

const formatDate = (date?: string): string => dayjs(date).format('YYYY-MM-DD HH:mm:ss')

const listColumns = computed<DesignerListTableColumn<Version>[]>(() => [
  { key: 'versionNumber', prop: 'versionNumber', label: t('version.versionNumber'), defaultWidth: 140 },
  { key: 'createdBy', prop: 'createdBy', label: t('version.publisher'), defaultWidth: 140,
    showOverflowTooltip: true },
  { key: 'createdAt', prop: 'createdAt', label: t('version.publishTime'), defaultWidth: 180,
    showOverflowTooltip: true, getValue: row => formatDate(row.createdAt) },
])

async function loadVersions(): Promise<void> {
  loading.value = true
  try {
    await Promise.all([store.fetchVersions(props.functionUnitId), store.fetchById(props.functionUnitId)])
  } finally {
    loading.value = false
  }
}

function handleCompare(row: Version): void {
  initialCompareVersionId.value = row.id
  showCompareDialog.value = true
}

async function handleRollback(row: Version): Promise<void> {
  if (row.current) {
    ElMessage.warning(t('version.cannotRollbackToCurrent', { version: row.versionNumber }))
    return
  }
  const currentVersion = store.current?.currentVersion
    || store.versions.find(version => version.current)?.versionNumber
    || store.versions[0]?.versionNumber || '?'
  await ElMessageBox.confirm(
    t('version.rollbackConfirmDetail', { targetVersion: row.versionNumber, currentVersion }),
    t('version.rollbackTitle'),
    { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
  )
  try {
    await store.rollback(props.functionUnitId, row.id)
    ElMessage.success(t('common.success'))
    await loadVersions()
  } catch (error: unknown) {
    ElMessage.error(resolveUserFacingHttpMessage(error, t) || t('common.error'))
  }
}

function handleExport(row: Version): void {
  window.open(`/api/v1/function-units/${props.functionUnitId}/versions/${row.id}/export`)
}

onMounted(loadVersions)
</script>

<style scoped>
.version-manager { min-height: 300px; }
.action-buttons { justify-content: center; gap: 8px; }
.action-buttons :deep(.el-button + .el-button) { margin-left: 0; }
.current-tag { margin-left: 8px; }
</style>

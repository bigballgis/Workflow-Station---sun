<template>
  <div class="page-container">
    <PageHeader :title="t('automationFlow.title')">
      <template #actions>
        <el-button @click="fetchList">
          <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
        </el-button>
        <el-button
          type="primary"
          @click="importDialogVisible = true"
        >
          <el-icon><Upload /></el-icon>{{ t('automationFlow.import') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card class="table-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          :placeholder="t('automationFlow.searchPlaceholder')"
          clearable
          style="width: 280px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <span class="flow-count">{{ t('automationFlow.total', { count: filteredList.length }) }}</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredList"
        stripe
        style="width: 100%"
      >
        <el-table-column
          prop="displayName"
          :label="t('automationFlow.displayName')"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="id"
          :label="t('automationFlow.flowId')"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <code>{{ row.id }}</code>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('automationFlow.flowKey')"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <code v-if="row.flowKey">{{ row.flowKey }}</code>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="projectName"
          :label="t('automationFlow.project')"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('automationFlow.status')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'ENABLED' ? 'success' : 'info'"
              size="small"
            >
              {{ row.status === 'ENABLED' ? t('automationFlow.enabled') : t('automationFlow.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('automationFlow.published')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.published ? 'success' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ row.published ? t('automationFlow.publishedYes') : t('automationFlow.publishedNo') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="ownerName"
          :label="t('automationFlow.owner')"
          width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.ownerName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="updated"
          :label="t('automationFlow.updated')"
          width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatDate(row.updated) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="exportingId === row.id"
              @click="handleExport(row)"
            >
              {{ t('automationFlow.export') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="importDialogVisible"
      :title="t('automationFlow.importTitle')"
      width="520px"
      @closed="resetImportDialog"
    >
      <el-form label-width="110px">
        <el-form-item :label="t('automationFlow.importFile')">
          <el-upload
            :show-file-list="true"
            :auto-upload="false"
            :limit="1"
            accept=".json"
            @change="onImportFileChange"
            @remove="importFile = null"
          >
            <el-button>{{ t('automationFlow.chooseFile') }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('automationFlow.publishLabel')">
          <el-switch v-model="importPublish" />
          <span class="import-hint">{{ t('automationFlow.publishHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!importFile"
          @click="handleImport"
        >
          {{ t('automationFlow.importConfirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadFile } from 'element-plus'
import { Refresh, Search, Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { formatDate } from '@/utils/format'
import { automationFlowApi, type AutomationFlowSummary } from '@/api/automationFlow'

const { t } = useI18n()

const loading = ref(false)
const keyword = ref('')
const flowList = ref<AutomationFlowSummary[]>([])
const exportingId = ref('')

const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importPublish = ref(true)
const importing = ref(false)

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return flowList.value
  return flowList.value.filter(f =>
    f.displayName.toLowerCase().includes(kw)
    || f.id.toLowerCase().includes(kw)
    || (f.flowKey ?? '').toLowerCase().includes(kw)
    || f.projectName.toLowerCase().includes(kw))
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await automationFlowApi.list()
    flowList.value = res.data ?? []
  } catch {
    ElMessage.error(t('automationFlow.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleExport = async (row: AutomationFlowSummary) => {
  exportingId.value = row.id
  try {
    const blob = await automationFlowApi.exportFlow(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `flow-${row.displayName.replace(/[^\w-]+/g, '-')}-${row.flowKey ?? row.id}.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('automationFlow.exportFailed'))
  } finally {
    exportingId.value = ''
  }
}

const onImportFileChange = (file: UploadFile) => {
  importFile.value = file.raw ?? null
}

const resetImportDialog = () => {
  importFile.value = null
  importPublish.value = true
}

const handleImport = async () => {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await automationFlowApi.importFlow(importFile.value, importPublish.value)
    const info = res.data
    ElMessage.success(t(
      info?.created ? 'automationFlow.importCreated' : 'automationFlow.importUpdated',
      { name: info?.displayName ?? '', id: info?.flowId ?? '' }
    ))
    importDialogVisible.value = false
    await fetchList()
  } catch {
    // request.ts 拦截器已 notify 具体错误
  } finally {
    importing.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.flow-count {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.import-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 10px;
}
</style>

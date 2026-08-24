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
        <!-- 窄面板下把次要元数据折进名称行第二行,避免 6 列硬塞进 500px 全被截断 -->
        <el-table-column
          prop="displayName"
          :label="t('automationFlow.displayName')"
          min-width="160"
          :show-overflow-tooltip="!isCompact"
        >
          <template #default="{ row }">
            <div class="flow-name">
              <span class="flow-name__title">{{ row.displayName }}</span>
              <span
                v-if="isCompact"
                class="flow-name__meta"
              >{{ compactMeta(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <!-- flowId 与迁移键同属"这条 flow 是谁"，合成一列：迁移来的才显示来源键，
             本环境原生 flow 只有一行 id，避免两列 21 位随机串互相干扰阅读 -->
        <el-table-column
          v-if="!isCompact"
          :label="t('automationFlow.flowId')"
          min-width="210"
        >
          <template #default="{ row }">
            <div class="flow-identity">
              <code class="flow-identity__id">{{ row.id }}</code>
              <span
                v-if="row.flowKey && row.flowKey !== row.id"
                class="flow-identity__origin"
                :title="row.flowKey"
              >{{ t('automationFlow.migratedFrom', { key: row.flowKey }) }}</span>
            </div>
          </template>
        </el-table-column>
        <!-- 发布态与启停本质是同一条就绪阶梯（草稿 → 已发布未启用 → 运行中），
             拆成两列会逼读者自己做组合判断 -->
        <el-table-column
          :label="t('automationFlow.state')"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="readiness(row).type"
              size="small"
              :effect="readiness(row).effect"
              disable-transitions
            >
              {{ t(readiness(row).labelKey) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="!isCompact"
          prop="projectName"
          :label="t('automationFlow.project')"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="!isCompact"
          prop="ownerName"
          :label="t('automationFlow.owner')"
          width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.ownerName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="!isCompact"
          prop="updated"
          :label="t('automationFlow.updated')"
          width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatDate(row.updated) }}
          </template>
        </el-table-column>
        <!-- fixed:主行动点必须一直够得着;停用/删除收进下拉——删除不可逆,多一次点击是有意的 -->
        <el-table-column
          :label="t('common.operation')"
          width="140"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                link
                type="primary"
                size="small"
                :loading="exportingId === row.id"
                @click="handleExport(row)"
              >
                {{ t('automationFlow.export') }}
              </el-button>
              <el-dropdown
                trigger="click"
                @command="(cmd: string) => handleRowCommand(cmd, row)"
              >
                <el-button
                  link
                  size="small"
                  :loading="actingId === row.id"
                  :aria-label="t('common.operation')"
                >
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="structure">
                      {{ t('automationFlow.viewStructure') }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="toggle"
                      :disabled="!row.published"
                    >
                      {{ row.status === 'ENABLED' ? t('automationFlow.disable') : t('automationFlow.enable') }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="delete"
                      divided
                    >
                      <span class="danger-item">{{ t('common.delete') }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <FlowStructureDialog
      v-model="structureDialogVisible"
      :flow-id="structureFlow?.id ?? ''"
      :flow-name="structureFlow?.displayName ?? ''"
    />

    <el-dialog
      v-model="importDialogVisible"
      :title="t('automationFlow.importTitle')"
      width="520px"
      @closed="resetImportDialog"
    >
      <!-- label-width="auto":弹窗表单统一规范,长 label 不折行且各行输入框左对齐 -->
      <el-form label-width="auto">
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
        <el-form-item
          v-if="connectionChecks.length > 0"
          :label="t('automationFlow.connectionsTitle')"
        >
          <div class="connection-list">
            <div
              v-for="item in connectionChecks"
              :key="item.externalId"
              class="connection-item"
            >
              <el-tag
                :type="item.exists ? 'success' : 'danger'"
                size="small"
                disable-transitions
              >
                {{ item.exists ? t('automationFlow.connectionExists') : t('automationFlow.connectionMissing') }}
              </el-tag>
              <code>{{ item.externalId }}</code>
              <span
                v-if="item.pieceName"
                class="connection-piece"
              >{{ shortPieceName(item.pieceName) }}</span>
            </div>
            <div
              v-if="hasMissingConnections"
              class="connection-warning"
            >
              {{ t('automationFlow.connectionsHint') }}
            </div>
          </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { MoreFilled, Refresh, Search, Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FlowStructureDialog from '@/components/automation-flow/FlowStructureDialog.vue'
import { formatDate } from '@/utils/format'
import {
  automationFlowApi,
  type AutomationFlowSummary,
  type ConnectionCheckItem,
  type FlowExportConnection
} from '@/api/automationFlow'

const { t } = useI18n()

const loading = ref(false)
const keyword = ref('')
const flowList = ref<AutomationFlowSummary[]>([])
const exportingId = ref('')
const actingId = ref('')

const structureDialogVisible = ref(false)
const structureFlow = ref<AutomationFlowSummary | null>(null)

const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importPublish = ref(true)
const importing = ref(false)
const connectionChecks = ref<ConnectionCheckItem[]>([])

const hasMissingConnections = computed(() =>
  connectionChecks.value.some(item => !item.exists))

/** \@activepieces/piece-x → piece-x,自研短名原样 */
const shortPieceName = (name: string) =>
  name.includes('/') ? name.split('/')[1] : name

/**
 * 密度自适应:管理端常在窄面板(内嵌浏览器/分屏)里打开,全列铺开需 ~950px。
 * 窄于阈值时只留「名称 / 就绪状态 / 导出」,其余元数据折进名称行第二行。
 */
const viewportWidth = ref(window.innerWidth)
const syncViewportWidth = () => {
  viewportWidth.value = window.innerWidth
}
const isCompact = computed(() => viewportWidth.value < 1180)

const compactMeta = (row: AutomationFlowSummary) =>
  [row.id, row.ownerName, formatDate(row.updated)].filter(Boolean).join(' · ')

/**
 * 就绪阶梯:webhook 只触发已发布版本,故「有无发布版本」是第一道门槛,
 * 启停是第二道。未发布时 status 无意义,一律归为草稿。
 */
const readiness = (row: AutomationFlowSummary) => {
  if (!row.published) {
    return { type: 'info' as const, effect: 'plain' as const, labelKey: 'automationFlow.stateDraft' }
  }
  return row.status === 'ENABLED'
    ? { type: 'success' as const, effect: 'light' as const, labelKey: 'automationFlow.stateLive' }
    : { type: 'warning' as const, effect: 'plain' as const, labelKey: 'automationFlow.stateStopped' }
}

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

const handleRowCommand = (command: string, row: AutomationFlowSummary) => {
  if (command === 'structure') {
    structureFlow.value = row
    structureDialogVisible.value = true
  } else if (command === 'toggle') {
    void handleToggle(row)
  } else if (command === 'delete') {
    void handleDelete(row)
  }
}

const handleToggle = async (row: AutomationFlowSummary) => {
  const enable = row.status !== 'ENABLED'
  actingId.value = row.id
  try {
    await automationFlowApi.setEnabled(row.id, enable)
    row.status = enable ? 'ENABLED' : 'DISABLED'
    ElMessage.success(t(enable ? 'automationFlow.enabled' : 'automationFlow.disabled',
      { name: row.displayName }))
  } catch {
    // 拦截器已提示（如未发布就启用会被 AP 拒绝）
  } finally {
    actingId.value = ''
  }
}

const handleDelete = async (row: AutomationFlowSummary) => {
  try {
    await ElMessageBox.confirm(
      t('automationFlow.deleteConfirm', { name: row.displayName }),
      t('common.delete'),
      { type: 'warning', confirmButtonText: t('common.delete') }
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await automationFlowApi.deleteFlow(row.id)
    ElMessage.success(t('automationFlow.deleted', { name: row.displayName }))
    await fetchList()
  } catch (e: unknown) {
    const status = (e as { status?: number })?.status
    // 409：被 FU 的 BPMN 引用，message 携带 FU 名称清单
    if (status !== 409) {
      return
    }
    const units = (e as { message?: string })?.message ?? ''
    try {
      await ElMessageBox.confirm(
        t('automationFlow.deleteInUse', { units }),
        t('common.delete'),
        { type: 'error', confirmButtonText: t('automationFlow.forceDelete') }
      )
      await automationFlowApi.deleteFlow(row.id, true)
      ElMessage.success(t('automationFlow.deleted', { name: row.displayName }))
      await fetchList()
    } catch {
      // 用户取消或强删失败（拦截器已提示）
    }
  } finally {
    actingId.value = ''
  }
}

const onImportFileChange = async (file: UploadFile) => {
  importFile.value = file.raw ?? null
  connectionChecks.value = []
  if (!file.raw) return
  // 预检:解析导出包里的 connection 清单,查本环境缺口(失败静默——导入时后端会给出具体错误)
  try {
    const pkg = JSON.parse(await file.raw.text()) as { connections?: FlowExportConnection[] }
    const ids = (pkg.connections ?? []).map(c => c.externalId).filter(Boolean)
    if (ids.length === 0) return
    const res = await automationFlowApi.connectionsCheck(ids)
    connectionChecks.value = res.data ?? []
  } catch {
    connectionChecks.value = []
  }
}

const resetImportDialog = () => {
  importFile.value = null
  importPublish.value = true
  connectionChecks.value = []
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

onMounted(() => {
  window.addEventListener('resize', syncViewportWidth)
  void fetchList()
})

onBeforeUnmount(() => window.removeEventListener('resize', syncViewportWidth))
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

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.danger-item {
  color: var(--el-color-danger);
}

.flow-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.flow-name__title,
.flow-name__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-name__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}

.flow-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.flow-identity__id,
.flow-identity__origin {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-identity__origin {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* macOS 的覆盖式滚动条默认隐藏,列宽超出面板时用户会以为表格卡死——常驻显示 */
.table-card :deep(.el-scrollbar__bar.is-horizontal) {
  opacity: 1;
  height: 8px;
}

.import-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 10px;
}

.connection-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.connection-item {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 22px;
}

.connection-piece {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.connection-warning {
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 1.4;
}
</style>

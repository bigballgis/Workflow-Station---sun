<template>
  <div class="page-container">
    <PageHeader :title="t('automationPiece.title')">
      <template #actions>
        <el-button @click="fetchList">
          <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
        </el-button>
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          accept=".tgz,.tar.gz"
          @change="handleImportFile"
        >
          <el-button
            type="primary"
            :loading="importing"
          >
            <el-icon><Upload /></el-icon>{{ t('automationPiece.import') }}
          </el-button>
        </el-upload>
      </template>
    </PageHeader>

    <el-card class="table-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          :placeholder="t('automationPiece.searchPlaceholder')"
          clearable
          style="width: 280px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <span class="piece-count">{{ t('automationPiece.total', { count: filteredList.length }) }}</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredList"
        stripe
        style="width: 100%"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="piece-detail">
              <p v-if="row.description" class="piece-desc">{{ row.description }}</p>
              <div v-if="row.actionNames.length" class="detail-line">
                <span class="detail-label">{{ t('automationPiece.actions') }}:</span>
                <el-tag
                  v-for="a in row.actionNames"
                  :key="a"
                  size="small"
                  class="detail-tag"
                >{{ a }}</el-tag>
              </div>
              <div v-if="row.triggerNames.length" class="detail-line">
                <span class="detail-label">{{ t('automationPiece.triggers') }}:</span>
                <el-tag
                  v-for="tr in row.triggerNames"
                  :key="tr"
                  size="small"
                  type="warning"
                  class="detail-tag"
                >{{ tr }}</el-tag>
              </div>
              <div class="detail-line">
                <span class="detail-label">{{ t('automationPiece.packageName') }}:</span>
                <code>{{ row.name }}</code>
              </div>
              <div class="detail-line">
                <span class="detail-label">{{ t('automationPiece.runtime') }}:</span>
                <el-tag
                  :type="row.hasArchive ? 'success' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ row.hasArchive ? t('automationPiece.runtimeArchive') : t('automationPiece.runtimeBaked') }}
                </el-tag>
                <span class="detail-hint">
                  {{ row.hasArchive ? t('automationPiece.runtimeArchiveTip') : t('automationPiece.runtimeBakedTip') }}
                </span>
              </div>
              <div v-if="row.authors.length" class="detail-line">
                <span class="detail-label">{{ t('automationPiece.authors') }}:</span>
                {{ row.authors.join(', ') }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="displayName"
          :label="t('automationPiece.displayName')"
          min-width="110"
          show-overflow-tooltip
        />
        <el-table-column
          prop="name"
          :label="t('automationPiece.packageName')"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <code>{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column
          prop="version"
          :label="t('automationPiece.version')"
          width="85"
          align="center"
        />
        <el-table-column
          :label="t('automationPiece.type')"
          width="105"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.pieceType === 'OFFICIAL' ? 'info' : 'success'"
              size="small"
            >
              {{ row.pieceType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.enabled')"
          width="85"
          align="center"
        >
          <template #default="{ row }">
            <el-switch
              :model-value="!row.disabled"
              :loading="togglingKey === rowKey(row)"
              @change="(val: string | number | boolean) => handleToggle(row, val as boolean)"
            />
          </template>
        </el-table-column>
        <el-table-column
          :label="t('automationPiece.actions')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.actionCount }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('automationPiece.triggers')"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.triggerCount }}
          </template>
        </el-table-column>
        <el-table-column
          prop="updated"
          :label="t('automationPiece.updated')"
          width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatDate(row.updated) }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.operation')"
          min-width="140"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="exportingKey === rowKey(row)"
              @click="handleExport(row)"
            >
              {{ t('automationPiece.export') }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :loading="deletingKey === rowKey(row)"
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Refresh, Search, Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { formatDate } from '@/utils/format'
import {
  automationPieceApi,
  exportFilename,
  type AutomationPieceSummary
} from '@/api/automationPiece'

const { t } = useI18n()

const loading = ref(false)
const keyword = ref('')
const pieceList = ref<AutomationPieceSummary[]>([])
const exportingKey = ref('')
const togglingKey = ref('')
const deletingKey = ref('')
const importing = ref(false)

const rowKey = (row: AutomationPieceSummary) => `${row.name}@${row.version}`

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return pieceList.value
  return pieceList.value.filter(p =>
    p.name.toLowerCase().includes(kw)
    || p.displayName.toLowerCase().includes(kw)
    || p.actionNames.some(a => a.toLowerCase().includes(kw))
    || p.triggerNames.some(tr => tr.toLowerCase().includes(kw)))
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await automationPieceApi.list()
    pieceList.value = res.data ?? []
  } catch {
    ElMessage.error(t('automationPiece.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleExport = async (row: AutomationPieceSummary) => {
  exportingKey.value = rowKey(row)
  try {
    const blob = await automationPieceApi.exportPiece(row.name, row.version)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = exportFilename(row)
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('automationPiece.exportFailed'))
  } finally {
    exportingKey.value = ''
  }
}

const handleImportFile = async (file: UploadFile) => {
  if (!file.raw) return
  importing.value = true
  try {
    const res = await automationPieceApi.importPiece(file.raw)
    const info = res.data
    ElMessage.success(t('automationPiece.importSuccess', {
      name: info?.displayName ?? '',
      version: info?.version ?? ''
    }))
    await fetchList()
  } catch {
    // request.ts 拦截器已 notify 具体错误
  } finally {
    importing.value = false
  }
}

const handleToggle = async (row: AutomationPieceSummary, enabled: boolean) => {
  togglingKey.value = rowKey(row)
  try {
    await automationPieceApi.togglePiece(row.name, !enabled)
    // 同名多版本共享一个启停开关(黑名单按包名),本地同步全部同名行
    pieceList.value.forEach(p => {
      if (p.name === row.name) p.disabled = !enabled
    })
  } catch {
    // 拦截器已提示
  } finally {
    togglingKey.value = ''
  }
}

const handleDelete = async (row: AutomationPieceSummary) => {
  try {
    // 烘焙件(OFFICIAL/REGISTRY)删的是目录元数据:镜像里的运行时包不受影响,
    // 且若重跑 pieces-seed.sql 或白名单未变,会再次出现 —— 用更重的警告文案
    const message = row.pieceType === 'OFFICIAL'
      ? t('automationPiece.deleteOfficialConfirm', { name: row.displayName, version: row.version })
      : t('automationPiece.deleteConfirm', { name: row.displayName, version: row.version })
    await ElMessageBox.confirm(message, t('common.delete'), {
      type: 'warning',
      confirmButtonText: t('common.delete')
    })
  } catch {
    return
  }
  deletingKey.value = rowKey(row)
  try {
    await automationPieceApi.deletePiece(row.name, row.version)
    ElMessage.success(t('automationPiece.deleted'))
    await fetchList()
  } catch (e: unknown) {
    const status = (e as { status?: number })?.status
    const refCount = (e as { message?: string })?.message
    if (status === 409) {
      try {
        await ElMessageBox.confirm(
          t('automationPiece.deleteInUse', { count: refCount ?? '?' }),
          t('common.delete'),
          { type: 'error', confirmButtonText: t('automationPiece.forceDelete') }
        )
        await automationPieceApi.deletePiece(row.name, row.version, true)
        ElMessage.success(t('automationPiece.deleted'))
        await fetchList()
      } catch {
        // 用户取消或强删失败(拦截器已提示)
      }
    }
  } finally {
    deletingKey.value = ''
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

.piece-count {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.piece-detail {
  padding: 8px 48px;
}

.piece-desc {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
}

.detail-line {
  margin-bottom: 6px;
  line-height: 24px;
}

.detail-label {
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}

.detail-tag {
  margin-right: 6px;
}

.detail-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 8px;
}
</style>

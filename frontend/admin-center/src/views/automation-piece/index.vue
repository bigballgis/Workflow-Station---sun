<template>
  <div class="page-container">
    <PageHeader :title="t('automationPiece.title')">
      <template #actions>
        <el-button @click="fetchList">
          <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
        </el-button>
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
          :label="t('automationPiece.runtime')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tooltip
              :content="row.hasArchive
                ? t('automationPiece.runtimeArchiveTip')
                : t('automationPiece.runtimeBakedTip')"
              placement="top"
            >
              <el-tag
                :type="row.hasArchive ? 'success' : 'info'"
                size="small"
                effect="plain"
              >
                {{ row.hasArchive ? t('automationPiece.runtimeArchive') : t('automationPiece.runtimeBaked') }}
              </el-tag>
            </el-tooltip>
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
          min-width="105"
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
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
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
</style>

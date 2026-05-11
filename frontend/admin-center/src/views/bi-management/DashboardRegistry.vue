<template>
  <div class="page-container">
    <PageHeader :title="t('bi.dashboard.pageTitle')">
      <template #actions>
        <el-button
          type="primary"
          :loading="syncing"
          @click="handleSync"
        >
          <el-icon><Refresh /></el-icon>{{ t('bi.dashboard.syncDashboards') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card class="search-card">
      <el-form
        :inline="true"
        :model="query"
        class="search-form"
      >
        <el-form-item :label="t('bi.dashboard.searchTitle')">
          <el-input
            v-model="query.title"
            :placeholder="t('bi.dashboard.searchTitlePlaceholder')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="t('bi.dashboard.searchTags')">
          <el-input
            v-model="query.tags"
            :placeholder="t('bi.dashboard.searchTagsPlaceholder')"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item :label="t('bi.dashboard.filterStatus')">
          <el-select
            v-model="query.status"
            :placeholder="t('bi.dashboard.filterStatusPlaceholder')"
            clearable
            style="width: 140px"
          >
            <el-option
              :label="t('bi.dashboard.statusActive')"
              value="ACTIVE"
            />
            <el-option
              :label="t('bi.dashboard.statusManualInactive')"
              value="MANUAL_INACTIVE"
            />
            <el-option
              :label="t('bi.dashboard.statusAutoInactive')"
              value="AUTO_INACTIVE"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="handleSearch"
          >
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshIcon /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="dashboards"
        stripe
        border
        table-layout="auto"
        style="width: 100%"
      >
        <el-table-column
          prop="dashboardTitle"
          :label="t('bi.dashboard.colDashboardTitle')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="embedId"
          :label="t('bi.dashboard.colEmbedId')"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="supersetDashboardUuid"
          :label="t('bi.dashboard.colSupersetUuid')"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="tags"
          :label="t('bi.dashboard.colTags')"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row.tags">{{ row.tags }}</span>
            <span
              v-else
              style="color: #c0c4cc"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('bi.dashboard.colDefaultLanding')"
          width="150"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.isDefaultLanding"
              type="success"
              size="small"
            >
              {{ t('bi.dashboard.yes') }}
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
            >
              {{ t('bi.dashboard.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('bi.dashboard.colStatus')"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="biDashboardStatusTagType(row.status) as 'success' | 'warning' | 'info'"
              size="small"
            >
              {{ t(biDashboardStatusKey(row.status)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="lastSyncedAt"
          :label="t('bi.dashboard.colLastSynced')"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('bi.dashboard.colActions')"
          width="220"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <div style="display: flex; align-items: center; justify-content: center; flex-wrap: nowrap; white-space: nowrap; gap: 4px;">
              <el-button
                link
                type="primary"
                size="small"
                @click="showEditDialog(row)"
              >
                {{ t('bi.dashboard.edit') }}
              </el-button>
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                type="warning"
                size="small"
                @click="handleToggleStatus(row)"
              >
                {{ t('bi.dashboard.disable') }}
              </el-button>
              <el-button
                v-else-if="row.status === 'MANUAL_INACTIVE'"
                link
                type="success"
                size="small"
                @click="handleToggleStatus(row)"
              >
                {{ t('bi.dashboard.enable') }}
              </el-button>
              <el-button
                v-else
                link
                type="info"
                size="small"
                disabled
              >
                {{ t('bi.dashboard.enable') }}
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >
                {{ t('bi.dashboard.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <DashboardEditDialog
      v-model="editDialogVisible"
      :edit-form="editForm"
      :edit-loading="editLoading"
      @submit="handleEditSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useBiDashboard } from '@/composables/modules/useBiDashboard'
import { biDashboardStatusKey, biDashboardStatusTagType } from '@/utils/format'
import DashboardEditDialog from './components/DashboardEditDialog.vue'

const { t } = useI18n()

const {
  loading, syncing, editLoading, dashboards, total, query,
  editDialogVisible, editForm,
  handleSearch, handleReset, handleSync,
  showEditDialog, handleEditSubmit, handleToggleStatus, handleDelete,
} = useBiDashboard()

onMounted(() => { handleSearch() })
onActivated(() => { handleSearch() })
</script>

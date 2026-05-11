<template>
  <div class="page-container">
    <PageHeader :title="t('bi.assignment.pageTitle')">
      <template #actions>
        <el-button
          type="primary"
          @click="showCreateDialog"
        >
          <el-icon><Plus /></el-icon>{{ t('bi.assignment.newAssignment') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card class="search-card">
      <el-form
        :inline="true"
        :model="query"
        class="search-form"
      >
        <el-form-item :label="t('bi.assignment.filterTargetType')">
          <el-select
            v-model="query.targetType"
            :placeholder="t('bi.assignment.placeholderTargetType')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="opt in targetTypeFilterOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('bi.assignment.filterDashboardTitle')">
          <el-input
            v-model="query.dashboardTitle"
            :placeholder="t('bi.assignment.placeholderDashboardTitle')"
            clearable
            style="width: 200px"
          />
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
        :data="assignments"
        stripe
        border
        table-layout="auto"
        style="width: 100%"
      >
        <el-table-column
          prop="dashboardTitle"
          :label="t('bi.assignment.colDashboardTitle')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('bi.assignment.colTargetType')"
          width="130"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="assignmentTargetTagType(row.targetType)"
              size="small"
            >
              {{ t(assignmentTargetTypeKey(row.targetType)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="targetName"
          :label="t('bi.assignment.colTargetName')"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('bi.assignment.colLayoutMode')"
          width="130"
          align="center"
        >
          <template #default="{ row }">
            {{ t(layoutModeKey(row.layoutMode)) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="displayOrder"
          :label="t('bi.assignment.colDisplayOrder')"
          width="120"
          align="center"
        />
        <el-table-column
          :label="t('bi.assignment.colDefault')"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.isDefault"
              type="success"
              size="small"
            >
              {{ t('bi.assignment.defaultYes') }}
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
            >
              {{ t('bi.assignment.defaultNo') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('bi.assignment.colActions')"
          width="140"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <div class="action-cell">
              <el-button
                link
                type="primary"
                size="small"
                @click="showEditDialog(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >
                {{ t('common.delete') }}
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

    <AssignmentFormDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :initial-row="editingRow"
      @success="handleSearch"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useBiAssignment } from '@/composables/modules/useBiAssignment'
import AssignmentFormDialog from './components/AssignmentFormDialog.vue'
import { assignmentTargetTagType, assignmentTargetTypeKey, layoutModeKey } from '@/utils/format'

const { t } = useI18n()

const {
  loading,
  assignments,
  total,
  query,
  dialogVisible,
  dialogMode,
  editingRow,
  targetTypeFilterOptions,
  handleSearch,
  handleReset,
  showCreateDialog,
  showEditDialog,
  handleDelete
} = useBiAssignment()

onMounted(() => {
  handleSearch()
})
</script>



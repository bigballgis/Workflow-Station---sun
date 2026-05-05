<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('bi.assignment.pageTitle') }}</span>
      <div class="header-actions">
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('bi.assignment.newAssignment') }}
        </el-button>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
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
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshIcon /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="assignments" v-loading="loading" stripe border table-layout="auto" style="width: 100%">
        <el-table-column
          prop="dashboardTitle"
          :label="t('bi.assignment.colDashboardTitle')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column :label="t('bi.assignment.colTargetType')" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="targetTypeTagType(row.targetType)" size="small">{{ targetTypeText(row.targetType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="targetName"
          :label="t('bi.assignment.colTargetName')"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column :label="t('bi.assignment.colLayoutMode')" width="130" align="center">
          <template #default="{ row }">
            {{ layoutModeText(row.layoutMode) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="displayOrder"
          :label="t('bi.assignment.colDisplayOrder')"
          width="120"
          align="center"
        />
        <el-table-column :label="t('bi.assignment.colDefault')" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small">{{ t('bi.assignment.defaultYes') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ t('bi.assignment.defaultNo') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('bi.assignment.colActions')" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button link type="primary" size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
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
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import {
  biManagementApi,
  type DashboardAssignmentResponse,
  type AssignmentTargetType,
  type LayoutMode,
  type AssignmentListParams
} from '@/api/biManagement'
import AssignmentFormDialog from './components/AssignmentFormDialog.vue'

const { t } = useI18n()

const loading = ref(false)
const assignments = ref<DashboardAssignmentResponse[]>([])
const total = ref(0)

const query = reactive<AssignmentListParams & { page: number; size: number }>({
  targetType: undefined,
  dashboardTitle: '',
  page: 1,
  size: 20
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingRow = ref<DashboardAssignmentResponse | null>(null)

const ASSIGNMENT_TARGET_TYPES: AssignmentTargetType[] = ['USER', 'ROLE', 'BUSINESS_UNIT']

const targetTypeFilterOptions = computed(() =>
  ASSIGNMENT_TARGET_TYPES.map((value) => ({
    value,
    label:
      value === 'USER'
        ? t('bi.assignment.targetTypeUser')
        : value === 'ROLE'
          ? t('bi.assignment.targetTypeRole')
          : t('bi.assignment.targetTypeBusinessUnit')
  }))
)

const targetTypeText = (type: AssignmentTargetType): string => {
  const map: Record<AssignmentTargetType, string> = {
    USER: t('bi.assignment.targetTypeUser'),
    ROLE: t('bi.assignment.targetTypeRole'),
    BUSINESS_UNIT: t('bi.assignment.targetTypeBusinessUnit')
  }
  return map[type] || type
}

const targetTypeTagType = (type: AssignmentTargetType) => {
  const map: Record<AssignmentTargetType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    USER: 'primary',
    ROLE: 'success',
    BUSINESS_UNIT: 'warning'
  }
  return map[type] || ('info' as const)
}

const layoutModeText = (mode: LayoutMode): string => {
  const map: Record<LayoutMode, string> = {
    SINGLE: t('bi.assignment.layoutModeSingle'),
    MULTI: t('bi.assignment.layoutModeMulti'),
    WIDGET: t('bi.assignment.layoutModeWidget')
  }
  return map[mode] || mode
}

const handleSearch = async () => {
  loading.value = true
  try {
    const params: AssignmentListParams = {
      targetType: query.targetType || undefined,
      dashboardTitle: query.dashboardTitle || undefined,
      page: query.page - 1,
      size: query.size
    }
    const result = await biManagementApi.assignment.list(params)
    assignments.value = result.content
    total.value = result.totalElements
  } catch (error) {
    console.error('assignment list failed', error)
    ElMessage.error(t('bi.assignment.queryFailed'))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  Object.assign(query, { targetType: undefined, dashboardTitle: '', page: 1 })
  handleSearch()
}

const showCreateDialog = () => {
  dialogMode.value = 'create'
  editingRow.value = null
  dialogVisible.value = true
}

const showEditDialog = (row: DashboardAssignmentResponse) => {
  dialogMode.value = 'edit'
  editingRow.value = row
  dialogVisible.value = true
}

const handleDelete = async (row: DashboardAssignmentResponse) => {
  try {
    await ElMessageBox.confirm(
      t('bi.assignment.deleteConfirm', { title: row.dashboardTitle, target: row.targetName }),
      t('bi.assignment.deleteConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        confirmButtonClass: 'el-button--danger'
      }
    )
    await biManagementApi.assignment.delete(row.id)
    ElMessage.success(t('bi.assignment.deleteSuccess'))
    handleSearch()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('assignment delete failed', error)
      ElMessage.error(t('bi.assignment.deleteFailed'))
    }
  }
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }

  .header-actions {
    display: flex;
    gap: 12px;
  }
}

.search-card {
  margin-bottom: 20px;

  .search-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}

.table-card {
  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
</style>

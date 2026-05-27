<template>
  <div class="page-container">
    <PageHeader title="Table Data Management" />

    <div class="data-layout">
      <!-- Left: Table list -->
      <div class="table-list-panel">
        <div class="panel-title">
          Deployed Tables
        </div>
        <div style="padding: 6px 8px;">
          <el-input
            v-model="tableSearchKeyword"
            placeholder="Search tables..."
            clearable
            size="small"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <el-menu
          v-loading="tableListLoading"
          :default-active="selectedTableId ? String(selectedTableId) : ''"
          @select="handleSelectTable"
        >
          <el-menu-item
            v-for="t in filteredTables"
            :key="t.id"
            :index="String(t.id)"
          >
            <span>{{ t.displayName || t.tableName }}</span>
          </el-menu-item>
        </el-menu>
        <el-empty
          v-if="!tableListLoading && filteredTables.length === 0"
          description="No tables available"
          :image-size="60"
        />
      </div>

      <!-- Right: Data grid -->
      <div class="data-grid-panel">
        <template v-if="selectedTable">
          <div class="grid-toolbar">
            <el-input
              v-model="searchKeyword"
              placeholder="Search..."
              clearable
              style="width: 240px; margin-right: 12px;"
              @keyup.enter="fetchData"
              @clear="fetchData"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button
              type="primary"
              @click="openAddDialog"
            >
              <el-icon><Plus /></el-icon> Add
            </el-button>
            <el-button
              :loading="exporting"
              @click="handleExport"
            >
              <el-icon><Download /></el-icon> Export CSV
            </el-button>
          </div>

          <el-alert
            v-if="fetchDataError"
            :title="fetchDataError"
            type="error"
            show-icon
            :closable="false"
            style="margin-bottom: 12px;"
          />

          <el-table
            v-loading="dataLoading"
            :data="dataRows"
            stripe
            style="width: 100%;"
            border
          >
            <!-- Field columns from table structure -->
            <el-table-column
              v-for="field in fieldColumns"
              :key="field.fieldName"
              :prop="'data.' + field.fieldName"
              :label="field.fieldName"
              :min-width="120"
              sortable
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.data?.[field.fieldName] ?? '' }}
              </template>
            </el-table-column>
            <!-- System columns -->
            <el-table-column
              prop="data.created_at"
              label="Created At"
              width="170"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ formatHKT(row.data?.created_at) }}
              </template>
            </el-table-column>
            <el-table-column
              prop="data.created_by"
              label="Created By"
              width="120"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.data?.created_by ?? '' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="data.updated_at"
              label="Updated At"
              width="170"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ formatHKT(row.data?.updated_at) }}
              </template>
            </el-table-column>
            <el-table-column
              prop="data.updated_by"
              label="Updated By"
              width="120"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.data?.updated_by ?? '' }}
              </template>
            </el-table-column>
            <el-table-column
              label="Status"
              width="100"
            >
              <template #default="{ row }">
                <el-tag
                  :type="isRowDisabled(row) ? 'danger' : 'success'"
                  size="small"
                >
                  {{ isRowDisabled(row) ? 'Inactive' : 'Active' }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- Action column -->
            <el-table-column
              label="Actions"
              width="240"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="openEditDialog(row)"
                >
                  Edit
                </el-button>
                <el-button
                  v-if="isRowDisabled(row)"
                  link
                  type="success"
                  size="small"
                  @click="handleEnable(row)"
                >
                  Active
                </el-button>
                <el-button
                  v-else
                  link
                  type="warning"
                  size="small"
                  @click="handleDisable(row)"
                >
                  Inactive
                </el-button>
                <el-button
                  link
                  type="danger"
                  size="small"
                  @click="handleDelete(row)"
                >
                  Delete
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="totalElements > 0"
            style="margin-top: 16px; justify-content: flex-end;"
            background
            layout="total, sizes, prev, pager, next"
            :total="totalElements"
            :page-size="pageSize"
            :current-page="currentPage"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </template>
        <el-empty
          v-else
          description="Select a table from the left panel"
        />
      </div>
    </div>

    <!-- Add/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? 'Add Record' : 'Edit Record'"
      width="600px"
      destroy-on-close
    >
      <el-form
        :model="formData"
        label-width="140px"
        label-position="left"
      >
        <el-form-item
          v-for="field in fieldColumns"
          :key="field.fieldName"
          :label="field.fieldName"
          :required="!field.nullable"
        >
          <el-switch
            v-if="field.dataType === 'BOOLEAN'"
            v-model="formData[field.fieldName]"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
          <el-input-number
            v-else-if="isNumericType(field.dataType)"
            v-model="formData[field.fieldName]"
            :precision="field.dataType === 'DECIMAL' ? (field.scale || 2) : 0"
            style="width: 100%;"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
          <el-date-picker
            v-else-if="field.dataType === 'DATE'"
            v-model="formData[field.fieldName]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
          <el-date-picker
            v-else-if="field.dataType === 'TIMESTAMP'"
            v-model="formData[field.fieldName]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
          <el-input
            v-else-if="field.dataType === 'TEXT'"
            v-model="formData[field.fieldName]"
            type="textarea"
            :rows="3"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
          <el-input
            v-else
            v-model="formData[field.fieldName]"
            :maxlength="field.length || undefined"
            :disabled="dialogMode === 'edit' && field.isPrimaryKey"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          Cancel
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSaveRecord"
        >
          Save
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated } from 'vue'
import { Search, Download, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRelationTableData } from '@/composables/modules/useRelationTableData'

const {
  tableListLoading, dataLoading, exporting, saving,
  selectedTableId, searchKeyword, tableSearchKeyword, currentPage, pageSize, totalElements, dataRows,
  fetchDataError, dialogVisible, dialogMode, formData,
  selectedTable, fieldColumns, filteredTables,
  isNumericType, isRowDisabled,
  fetchData, handleSelectTable, handlePageChange, handleSizeChange,
  openAddDialog, openEditDialog, handleSaveRecord, handleDisable, handleEnable, handleDelete,
  formatHKT, handleExport, init, refresh,
} = useRelationTableData()

onMounted(init)
onActivated(refresh)
</script>

<style scoped>
.page-container { padding: 20px; }
.data-layout { display: flex; gap: 16px; height: calc(100vh - 140px); }
.table-list-panel {
  width: 220px; flex-shrink: 0;
  border: 1px solid var(--el-border-color-light); border-radius: 4px; overflow-y: auto;
}
.table-list-panel :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}
.table-list-panel :deep(.el-menu-item.is-active)::before { display: none; }
.panel-title { padding: 12px 16px; font-weight: 600; font-size: 14px; border-bottom: 1px solid var(--el-border-color-light); }
.data-grid-panel { flex: 1; min-width: 0; overflow: auto; }
.grid-toolbar { display: flex; align-items: center; margin-bottom: 12px; gap: 8px; }
</style>

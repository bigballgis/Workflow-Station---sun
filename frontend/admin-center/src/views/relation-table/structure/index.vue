<template>
  <div class="page-container">
    <PageHeader title="Table Structure">
      <template #actions>
        <el-button
          @click="router.push('/relation-tables/structure/er-diagram')"
        >
          <el-icon><Share /></el-icon>View ER Diagram
        </el-button>
        <el-button
          type="primary"
          @click="router.push('/relation-tables/structure/create')"
        >
          <el-icon><Plus /></el-icon>Create Table
        </el-button>
      </template>
    </PageHeader>

    <div class="structure-layout">
      <!-- Left: Function Unit groups -->
      <div class="fu-list-panel">
        <div class="panel-title">
          {{ t('relationTable.functionUnit') }}
        </div>
        <el-menu
          :default-active="selectedGroupKey"
          @select="(index: string) => (selectedGroupKey = index)"
        >
          <el-menu-item index="">
            <span>{{ t('relationTable.allFunctionUnits') }}</span>
          </el-menu-item>
          <el-menu-item
            v-for="group in groupedTableList"
            :key="group.key"
            :index="group.key"
          >
            <el-tooltip
              :content="group.label || t('relationTable.ungrouped')"
              placement="top"
              :show-after="400"
            >
              <span class="group-title">{{ group.label || t('relationTable.ungrouped') }} ({{ group.tables.length }})</span>
            </el-tooltip>
          </el-menu-item>
        </el-menu>
      </div>

      <!-- Right: Table list -->
      <div class="table-card">
        <el-table
          v-loading="loading"
          :data="filteredTableList"
          stripe
          scrollbar-always-on
          class="table-fixed-actions"
          style="width: 100%"
        >
          <el-table-column
            prop="displayName"
            label="Display Name"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="currentVersion"
            label="Version"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              v{{ row.currentVersion }}
            </template>
          </el-table-column>
          <el-table-column
            prop="status"
            label="Status"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusTagType(row.status)"
                size="small"
              >
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="Enable"
            width="80"
            align="center"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="enableLoadingMap[row.id]"
                @change="(val: string | number | boolean) => handleToggleEnabled(row, val as boolean)"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="Portal"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.portalVisible"
                :loading="portalLoadingMap[row.id]"
                :disabled="!row.enabled"
                @change="(val: string | number | boolean) => handleTogglePortalVisibility(row, val as boolean)"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="createdAt"
            label="Created At"
            width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="updatedAt"
            label="Updated At"
            width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ formatDate(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="Actions"
            width="240"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <div class="action-cell">
                <el-button
                  link
                  type="warning"
                  size="small"
                  @click="handleEdit(row)"
                >
                  Edit
                </el-button>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="handleDeploy(row)"
                >
                  Deploy
                </el-button>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="handleVersions(row)"
                >
                  Version
                </el-button>
                <el-dropdown
                  trigger="click"
                  @command="(cmd: string) => handleActionCommand(cmd, row)"
                >
                  <el-button
                    link
                    type="primary"
                    size="small"
                  >
                    More<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="erDiagram">
                        ER Diagram
                      </el-dropdown-item>
                      <el-dropdown-item command="compare">
                        Compare
                      </el-dropdown-item>
                      <el-dropdown-item command="rollback">
                        Rollback
                      </el-dropdown-item>
                      <el-dropdown-item command="access">
                        Access
                      </el-dropdown-item>
                      <el-dropdown-item
                        command="delete"
                        divided
                      >
                        <span class="danger-item">Delete</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Version History Dialog -->
    <VersionDialog
      v-model="showVersionDialog"
      :table-id="currentTable?.id"
      :table-name="currentTable?.tableName"
      @rollback-success="fetchTableList"
    />

    <!-- Access Config Dialog -->
    <AccessConfigDialog
      v-model="showAccessDialog"
      :table-id="currentTable?.id"
      :table-name="currentTable?.tableName"
    />

    <!-- Version Compare Dialog -->
    <VersionCompareDialog
      v-model="showCompareDialog"
      :table-id="currentTable?.id"
      :table-name="currentTable?.tableName"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, Share, ArrowDown } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { relationTableStatusType as statusTagType, formatDate } from '@/utils/format'
import VersionDialog from './components/VersionDialog.vue'
import AccessConfigDialog from './components/AccessConfigDialog.vue'
import VersionCompareDialog from './components/VersionCompareDialog.vue'
import { useRelationTable } from '@/composables/modules/useRelationTable'

const router = useRouter()
const { t } = useI18n()

const {
  loading,
  filteredTableList,
  groupedTableList,
  selectedGroupKey,
  enableLoadingMap,
  portalLoadingMap,
  currentTable,
  showVersionDialog,
  showAccessDialog,
  showCompareDialog,
  fetchTableList,
  handleToggleEnabled,
  handleTogglePortalVisibility,
  handleAccess,
  handleDeploy,
  handleVersions,
  handleEdit,
  handleRollback,
  handleCompare,
  handleDelete,
} = useRelationTable()

function handleActionCommand(command: string, row: any) {
  switch (command) {
    case 'erDiagram':
      router.push(`/relation-tables/structure/${row.id}/er-diagram`)
      break
    case 'compare':
      handleCompare(row)
      break
    case 'rollback':
      handleRollback(row)
      break
    case 'access':
      handleAccess(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

onMounted(() => {
  fetchTableList()
})

onActivated(() => {
  fetchTableList()
})
</script>

<style scoped>
.danger-item {
  color: var(--el-color-danger);
}
.structure-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 220px);
  min-height: 480px;
}
.fu-list-panel {
  width: 240px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow-y: auto;
  background: var(--el-bg-color);
}
.fu-list-panel :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}
.panel-title {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.group-title {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.table-card {
  flex: 1;
  min-width: 0;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  background: var(--el-bg-color);
}
</style>

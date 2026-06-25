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

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="tableList"
        stripe
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
          prop="createdBy"
          label="Created By"
          width="110"
          show-overflow-tooltip
        />
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
          prop="updatedBy"
          label="Updated By"
          width="110"
          show-overflow-tooltip
        />
        <el-table-column
          label="Actions"
          width="470"
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
                @click="router.push(`/relation-tables/structure/${row.id}/er-diagram`)"
              >
                ER Diagram
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >
                Delete
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
                type="danger"
                size="small"
                @click="handleRollback(row)"
              >
                Rollback
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="handleVersions(row)"
              >
                Version
              </el-button>
              <el-button
                link
                type="info"
                size="small"
                @click="handleCompare(row)"
              >
                Compare
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="handleAccess(row)"
              >
                Access
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
import { Plus, Share } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { relationTableStatusType as statusTagType, formatDate } from '@/utils/format'
import VersionDialog from './components/VersionDialog.vue'
import AccessConfigDialog from './components/AccessConfigDialog.vue'
import VersionCompareDialog from './components/VersionCompareDialog.vue'
import { useRelationTable } from '@/composables/modules/useRelationTable'

const router = useRouter()

const {
  loading,
  tableList,
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

onMounted(() => {
  fetchTableList()
})

onActivated(() => {
  fetchTableList()
})
</script>

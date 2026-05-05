<template>
  <div class="page-container">
    <PageHeader :title="t('menu.organization')">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog()">
          <el-icon><Plus /></el-icon>{{ t('organization.createBusinessUnit') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="tree-card">
          <el-input v-model="filterText" :placeholder="t('organization.searchBusinessUnit')" clearable class="search-input">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-scrollbar height="calc(100vh - 280px)">
            <el-tree
              ref="treeRef"
              :data="orgStore.businessUnitTree"
              :props="{ label: 'name', children: 'children' }"
              :filter-node-method="filterNode"
              node-key="id"
              default-expand-all
              highlight-current
              draggable
              :indent="24"
              @node-click="handleNodeClick"
              @node-drop="handleNodeDrop"
            >
              <template #default="{ node, data }">
                <div class="tree-node">
                  <div class="node-content">
                    <el-icon class="node-icon"><OfficeBuilding /></el-icon>
                    <span class="node-label">{{ node.label }}</span>
                    <el-tag v-if="data.memberCount" size="small" type="info" class="member-tag">{{ data.memberCount }} {{ t('role.people') }}</el-tag>
                  </div>
                  <div class="node-actions">
                    <el-button link type="primary" size="small" @click.stop="showCreateDialog(data)" :title="t('organization.createBusinessUnit')">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                    <el-button link type="primary" size="small" @click.stop="showEditDialog(data)" :title="t('common.edit')">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button link type="danger" size="small" @click.stop="handleDelete(data)" :title="t('common.delete')">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </template>
            </el-tree>
          </el-scrollbar>
        </el-card>
      </el-col>
      
      <el-col :span="14">
        <el-card v-if="selectedBusinessUnit" class="detail-card">
          <template #header>
            <div class="detail-header">
              <div class="header-left">
                <span class="header-title">{{ selectedBusinessUnit.name }}</span>
                <el-tag :type="selectedBusinessUnit.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ selectedBusinessUnit.status === 'ACTIVE' ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </div>
              <div class="header-actions">
                <el-button type="primary" size="small" @click="showMembersDialog">{{ t('organization.members') }}</el-button>
                <el-button type="primary" size="small" @click="showRolesDialog">{{ t('organization.eligibleRoles') }}</el-button>
                <el-button type="primary" size="small" @click="showApproversDialog">{{ t('organization.approvers') }}</el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('organization.businessUnitCode')">{{ selectedBusinessUnit.code }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.parentBusinessUnit')">{{ selectedBusinessUnit.parentName || t('common.noData') }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.memberCount')">{{ selectedBusinessUnit.memberCount || 0 }} {{ t('role.people') }}</el-descriptions-item>
          </el-descriptions>
          
          <!-- 成员和审批人两列布局 -->
          <el-row :gutter="20" class="lists-section">
            <el-col :span="12" class="list-col">
              <div class="section-header">
                <h4>{{ t('organization.members') }}</h4>
              </div>
              <div class="list-container">
                <el-scrollbar>
                  <el-table :data="businessUnitMembers" stripe size="small" :show-header="businessUnitMembers.length > 0">
                    <el-table-column :label="t('user.username')" min-width="100">
                      <template #default="{ row }">
                        <el-button link type="primary" @click="showUserDetail(row.id)">{{ row.username }}</el-button>
                      </template>
                    </el-table-column>
                    <el-table-column prop="fullName" :label="t('user.fullName')" min-width="80" />
                  </el-table>
                  <el-empty v-if="businessUnitMembers.length === 0" :description="t('common.noData')" :image-size="50" />
                </el-scrollbar>
              </div>
            </el-col>
            <el-col :span="12" class="list-col">
              <div class="section-header">
                <h4>{{ t('organization.approvers') }}</h4>
              </div>
              <div class="list-container">
                <el-scrollbar>
                  <el-table :data="businessUnitApprovers" stripe size="small" :show-header="businessUnitApprovers.length > 0">
                    <el-table-column :label="t('user.username')" min-width="100">
                      <template #default="{ row }">
                        <el-button link type="primary" @click="showUserDetail(row.userId)">{{ row.userName }}</el-button>
                      </template>
                    </el-table-column>
                    <el-table-column prop="userFullName" :label="t('user.fullName')" min-width="80" />
                  </el-table>
                  <el-empty v-if="businessUnitApprovers.length === 0" :description="t('organization.noApprovers')" :image-size="50" />
                </el-scrollbar>
              </div>
            </el-col>
          </el-row>
        </el-card>
        <el-card v-else class="empty-card">
          <el-empty :description="t('common.noData')" />
        </el-card>
      </el-col>
    </el-row>
    
    <BusinessUnitFormDialog v-model="dialogVisible" :businessUnit="currentBusinessUnit" :parent="parentBusinessUnit" @success="handleFormSuccess" />
    <BusinessUnitRolesDialog v-model="rolesDialogVisible" :businessUnit="selectedBusinessUnit" />
    <BusinessUnitApproversDialog v-model="approversDialogVisible" :businessUnit="selectedBusinessUnit" @success="fetchApprovers" />
    <BusinessUnitMembersDialog v-model="membersDialogVisible" :businessUnit="selectedBusinessUnit" @success="handleMembersChange" />
    <UserDetailDialog v-model="userDetailVisible" :userId="selectedUserId" />
  </div>
</template>

<script setup lang="ts">
import { watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, OfficeBuilding } from '@element-plus/icons-vue'
import { useOrganizationStore } from '@/stores/organization'
import BusinessUnitFormDialog from './components/BusinessUnitFormDialog.vue'
import BusinessUnitRolesDialog from './components/BusinessUnitRolesDialog.vue'
import BusinessUnitApproversDialog from './components/BusinessUnitApproversDialog.vue'
import BusinessUnitMembersDialog from './components/BusinessUnitMembersDialog.vue'
import UserDetailDialog from '@/views/user/components/UserDetailDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useTabRefresh } from '@/composables/useTabRefresh'
import { useBusinessUnit } from '@/composables/modules/useBusinessUnit'

const { t } = useI18n()
const orgStore = useOrganizationStore()

const {
  treeRef,
  filterText,
  selectedBusinessUnit,
  businessUnitMembers,
  businessUnitApprovers,
  dialogVisible,
  rolesDialogVisible,
  approversDialogVisible,
  membersDialogVisible,
  userDetailVisible,
  selectedUserId,
  currentBusinessUnit,
  parentBusinessUnit,
  filterNode,
  fetchMembers,
  fetchApprovers,
  handleNodeClick,
  handleNodeDrop,
  handleFormSuccess,
  handleDelete,
  showCreateDialog,
  showEditDialog,
  showRolesDialog,
  showApproversDialog,
  showMembersDialog,
  showUserDetail,
  handleMembersChange,
  refreshDetail,
} = useBusinessUnit()

watch(filterText, (val) => treeRef.value?.filter(val))

/** 从门户等其它页返回时刷新树与右侧成员，与后端成员/角色变更对齐 */
const refreshWhenTabVisible = async () => {
  await orgStore.fetchTree()
  if (!selectedBusinessUnit.value?.id) return
  await Promise.all([fetchMembers(), fetchApprovers()])
  await refreshDetail()
}

useTabRefresh(refreshWhenTabVisible)

onMounted(() => {
  orgStore.fetchTree()
})
</script>

<style scoped lang="scss">
.tree-card {
  height: calc(100vh - 180px);
  
  :deep(.el-card__body) {
    padding: 16px;
    height: calc(100% - 20px);
    display: flex;
    flex-direction: column;
  }
}

.search-input {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.tree-node {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px 4px 0;
  
  .node-content {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .node-icon {
      color: #409eff;
      font-size: 16px;
    }
    
    .node-label {
      font-size: 14px;
    }
    
    .member-tag {
      margin-left: 4px;
    }
  }
  
  .node-actions {
    display: none;
    gap: 4px;
  }
  
  &:hover .node-actions {
    display: inline-flex;
  }
}

:deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 4px;
  
  &:hover {
    background-color: #f5f7fa;
  }
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: #ecf5ff;
}

.detail-card {
  height: calc(100vh - 180px);
  
  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: calc(100% - 60px);
    overflow: hidden;
  }
  
  .detail-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .header-title {
        font-size: 16px;
        font-weight: 500;
      }
    }
    
    .header-actions {
      display: flex;
      gap: 8px;
    }
  }
}

.lists-section {
  margin-top: 20px;
  flex: 1;
  min-height: 0;
  
  .list-col {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
  
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    flex-shrink: 0;
    
    h4 {
      margin: 0;
      font-size: 14px;
      color: #606266;
    }
  }
  
  .list-container {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    flex: 1;
    min-height: 0;
    overflow: hidden;
    
    :deep(.el-scrollbar) {
      height: 100%;
    }
    
    :deep(.el-empty) {
      padding: 40px 0;
    }
  }
}

.empty-card {
  height: calc(100vh - 180px);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

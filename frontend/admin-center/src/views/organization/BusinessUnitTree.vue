<template>
  <div class="page-container">
    <PageHeader :title="t('menu.organization')">
      <template #actions>
        <el-button
          type="primary"
          @click="showCreateDialog()"
        >
          <el-icon><Plus /></el-icon>{{ t('organization.createBusinessUnit') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="tree-card">
          <el-input
            v-model="filterText"
            :placeholder="t('organization.searchBusinessUnit')"
            clearable
            class="search-input"
          >
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
                    <el-icon class="node-icon">
                      <OfficeBuilding />
                    </el-icon>
                    <span class="node-label">{{ node.label }}</span>
                    <el-tag
                      v-if="data.memberCount"
                      size="small"
                      type="info"
                      class="member-tag"
                    >
                      {{ data.memberCount }} {{ t('role.people') }}
                    </el-tag>
                  </div>
                  <div class="node-actions">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      :title="t('organization.createBusinessUnit')"
                      @click.stop="showCreateDialog(data)"
                    >
                      <el-icon><Plus /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="primary"
                      size="small"
                      :title="t('common.edit')"
                      @click.stop="showEditDialog(data)"
                    >
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      :title="t('common.delete')"
                      @click.stop="handleDelete(data)"
                    >
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
        <el-card
          v-if="selectedBusinessUnit"
          class="detail-card"
        >
          <template #header>
            <div class="detail-header">
              <div class="header-left">
                <span class="header-title">{{ selectedBusinessUnit.name }}</span>
                <el-tag
                  :type="selectedBusinessUnit.status === 'ACTIVE' ? 'success' : 'info'"
                  size="small"
                >
                  {{ selectedBusinessUnit.status === 'ACTIVE' ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </div>
              <div class="header-actions">
                <el-button
                  size="small"
                  @click="showMembersDialog"
                >
                  {{ t('organization.members') }}
                </el-button>
                <el-button
                  size="small"
                  @click="showRolesDialog"
                >
                  {{ t('organization.eligibleRoles') }}
                </el-button>
                <el-button
                  size="small"
                  @click="showApproversDialog"
                >
                  {{ t('organization.approvers') }}
                </el-button>
              </div>
            </div>
          </template>
          <div class="fact-strip">
            <div class="fact">
              <span class="fact-label">{{ t('organization.businessUnitCode') }}</span>
              <span class="fact-value">{{ selectedBusinessUnit.code }}</span>
            </div>
            <div class="fact">
              <span class="fact-label">{{ t('organization.parentBusinessUnit') }}</span>
              <span class="fact-value">{{ selectedBusinessUnit.parentName || t('common.noData') }}</span>
            </div>
            <div class="fact">
              <span class="fact-label">{{ t('organization.memberCount') }}</span>
              <span class="fact-value">{{ selectedBusinessUnit.memberCount || 0 }} {{ t('role.people') }}</span>
            </div>
          </div>
          
          <!-- 成员和审批人两列布局 -->
          <el-row
            :gutter="20"
            class="lists-section"
          >
            <el-col
              :span="12"
              class="list-col"
            >
              <div class="section-header">
                <h4>{{ t('organization.members') }}</h4>
              </div>
              <div class="list-container">
                <el-scrollbar>
                  <el-table
                    :data="businessUnitMembers"
                    stripe
                    size="small"
                    :show-header="businessUnitMembers.length > 0"
                  >
                    <el-table-column
                      :label="t('user.username')"
                      min-width="100"
                    >
                      <template #default="{ row }">
                        <el-button
                          link
                          type="primary"
                          @click="showUserDetail(row.id)"
                        >
                          {{ row.username }}
                        </el-button>
                      </template>
                    </el-table-column>
                    <el-table-column
                      prop="fullName"
                      :label="t('user.fullName')"
                      min-width="80"
                    />
                  </el-table>
                  <el-empty
                    v-if="businessUnitMembers.length === 0"
                    :description="t('common.noData')"
                    :image-size="50"
                  />
                </el-scrollbar>
              </div>
            </el-col>
            <el-col
              :span="12"
              class="list-col"
            >
              <div class="section-header">
                <h4>{{ t('organization.approvers') }}</h4>
              </div>
              <div class="list-container">
                <el-scrollbar>
                  <el-table
                    :data="businessUnitApprovers"
                    stripe
                    size="small"
                    :show-header="businessUnitApprovers.length > 0"
                  >
                    <el-table-column
                      :label="t('user.username')"
                      min-width="100"
                    >
                      <template #default="{ row }">
                        <el-button
                          link
                          type="primary"
                          @click="showUserDetail(row.userId)"
                        >
                          {{ row.userName }}
                        </el-button>
                      </template>
                    </el-table-column>
                    <el-table-column
                      prop="userFullName"
                      :label="t('user.fullName')"
                      min-width="80"
                    />
                  </el-table>
                  <el-empty
                    v-if="businessUnitApprovers.length === 0"
                    :description="t('organization.noApprovers')"
                    :image-size="50"
                  />
                </el-scrollbar>
              </div>
            </el-col>
          </el-row>
        </el-card>
        <el-card
          v-else
          class="empty-card"
        >
          <div class="empty-invite">
            <span class="empty-icon">
              <el-icon :size="22"><OfficeBuilding /></el-icon>
            </span>
            <p class="empty-text">
              {{ t('organization.selectHint') }}
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <BusinessUnitFormDialog
      v-model="dialogVisible"
      :business-unit="currentBusinessUnit"
      :parent="parentBusinessUnit"
      @success="handleFormSuccess"
    />
    <BusinessUnitRolesDialog
      v-model="rolesDialogVisible"
      :business-unit="selectedBusinessUnit"
    />
    <BusinessUnitApproversDialog
      v-model="approversDialogVisible"
      :business-unit="selectedBusinessUnit"
      @success="fetchApprovers"
    />
    <BusinessUnitMembersDialog
      v-model="membersDialogVisible"
      :business-unit="selectedBusinessUnit"
      @success="handleMembersChange"
    />
    <UserDetailDialog
      v-model="userDetailVisible"
      :user-id="selectedUserId"
    />
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
  min-width: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px 4px 0;

  .node-content {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;

    .node-icon {
      color: var(--ws-text-muted);
      font-size: 16px;
      flex-shrink: 0;
    }

    .node-label {
      font-size: 14px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .member-tag {
      margin-left: 4px;
      flex-shrink: 0;
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
  height: 40px;
  margin: 1px 0;
  border-radius: 10px;

  &:hover {
    background-color: #f5f7fa;
  }
}

// 选中节点：红字 + 红色浅底，与侧栏激活态同一语言
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--primary-soft);

  .node-icon,
  .node-label {
    color: var(--primary-color);
  }

  .node-label {
    font-weight: 600;
  }
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
        font-size: 18px;
        font-weight: 700;
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

    // 与表格表头同一套小号大写标签语言
    h4 {
      margin: 0;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: #8f8f8a;
    }
  }

  .list-container {
    border: 1px solid var(--ws-card-border);
    border-radius: 10px;
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

// 事实条：标签在上、值在下，替代带边框的 descriptions 网格
.fact-strip {
  display: flex;
  gap: 48px;
  padding: 4px 0 16px;
  border-bottom: 1px solid var(--ws-line);

  .fact {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }

  .fact-label {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #8f8f8a;
    white-space: nowrap;
  }

  .fact-value {
    font-size: 15px;
    font-weight: 600;
    color: var(--ws-text);
  }
}

.empty-card {
  height: calc(100vh - 180px);
  display: flex;
  align-items: center;
  justify-content: center;
}

// 空态：指引而非「No Data」
.empty-invite {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;

  .empty-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: 14px;
    background: var(--primary-soft);
    color: var(--primary-color);
  }

  .empty-text {
    margin: 0;
    font-size: 14px;
    color: var(--ws-text-secondary);
  }
}
</style>

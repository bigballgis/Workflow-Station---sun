<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">组织架构</span>
      <el-button type="primary" @click="showCreateDialog()">
        <el-icon><Plus /></el-icon>{{ t('organization.createDepartment') }}
      </el-button>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card class="tree-card">
          <el-input v-model="filterText" placeholder="搜索部门" clearable class="search-input">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-scrollbar height="calc(100vh - 280px)">
            <el-tree
              ref="treeRef"
              :data="orgStore.departmentTree"
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
                    <el-tag v-if="data.memberCount" size="small" type="info" class="member-tag">{{ data.memberCount }}人</el-tag>
                  </div>
                  <div class="node-actions">
                    <el-button link type="primary" size="small" @click.stop="showCreateDialog(data)" title="添加子部门">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                    <el-button link type="primary" size="small" @click.stop="showEditDialog(data)" title="编辑">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button link type="danger" size="small" @click.stop="handleDelete(data)" title="删除">
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
        <el-card v-if="selectedDepartment" class="detail-card">
          <template #header>
            <div class="detail-header">
              <span>{{ selectedDepartment.name }}</span>
              <el-tag :type="selectedDepartment.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ selectedDepartment.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('organization.departmentCode')">{{ selectedDepartment.code }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.parentDepartment')">{{ selectedDepartment.parentName || '无' }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.leader')">{{ selectedDepartment.leaderName || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="副经理">{{ selectedDepartment.secondaryManagerName || '未设置' }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.memberCount')">{{ selectedDepartment.memberCount || 0 }} 人</el-descriptions-item>
          </el-descriptions>
          
          <div class="members-section">
            <h4>部门成员</h4>
            <el-table :data="departmentMembers" max-height="300" stripe>
              <el-table-column prop="fullName" label="姓名" min-width="80" />
              <el-table-column prop="employeeId" label="工号" min-width="100" />
              <el-table-column prop="username" label="用户名" min-width="100" />
              <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            </el-table>
          </div>
        </el-card>
        <el-card v-else class="empty-card">
          <el-empty description="点击左侧部门查看详情" />
        </el-card>
      </el-col>
    </el-row>
    
    <DepartmentFormDialog v-model="dialogVisible" :department="currentDepartment" :parent="parentDepartment" @success="orgStore.fetchTree" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, OfficeBuilding } from '@element-plus/icons-vue'
import { useOrganizationStore } from '@/stores/organization'
import { Department, organizationApi } from '@/api/organization'
import DepartmentFormDialog from './components/DepartmentFormDialog.vue'

const { t } = useI18n()
const orgStore = useOrganizationStore()

const treeRef = ref()
const filterText = ref('')
const selectedDepartment = ref<Department | null>(null)
const departmentMembers = ref<any[]>([])
const dialogVisible = ref(false)
const currentDepartment = ref<Department | null>(null)
const parentDepartment = ref<Department | null>(null)

const filterNode = (value: string, data: any) => !value || data.name.includes(value)

watch(filterText, (val) => treeRef.value?.filter(val))

const handleNodeClick = async (data: Department) => {
  // 获取部门详情（包含父部门名称和管理者名称）
  try {
    const detail = await organizationApi.getById(data.id)
    selectedDepartment.value = detail
  } catch (e) {
    selectedDepartment.value = data
  }
  const result = await organizationApi.getMembers(data.id, { page: 0, size: 50 })
  departmentMembers.value = result.content || []
}

const handleNodeDrop = async (draggingNode: any, dropNode: any, dropType: string) => {
  const newParentId = dropType === 'inner' ? dropNode.data.id : dropNode.data.parentId
  await orgStore.moveDepartment(draggingNode.data.id, { newParentId })
  ElMessage.success('移动成功')
}

const showCreateDialog = (parent?: Department) => {
  currentDepartment.value = null
  parentDepartment.value = parent || null
  dialogVisible.value = true
}

const showEditDialog = (dept: Department) => {
  currentDepartment.value = dept
  parentDepartment.value = null
  dialogVisible.value = true
}

const handleDelete = async (dept: Department) => {
  await ElMessageBox.confirm(t('organization.deleteConfirm'), '提示', { type: 'warning' })
  try {
    await orgStore.deleteDepartment(dept.id)
    ElMessage.success(t('common.success'))
    if (selectedDepartment.value?.id === dept.id) selectedDepartment.value = null
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(() => orgStore.fetchTree())
</script>

<style scoped lang="scss">
.tree-card {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.search-input {
  margin-bottom: 16px;
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
  .detail-header {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 16px;
    font-weight: 500;
  }
}

.members-section {
  margin-top: 24px;
  
  h4 {
    margin: 0 0 12px 0;
    font-size: 14px;
    color: #606266;
  }
}

.empty-card {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

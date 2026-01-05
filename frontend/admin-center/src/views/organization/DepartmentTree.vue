<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.department') }}</span>
      <el-button type="primary" @click="showCreateDialog()">
        <el-icon><Plus /></el-icon>{{ t('organization.createDepartment') }}
      </el-button>
    </div>
    
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card>
          <template #header>组织架构树</template>
          <el-input v-model="filterText" placeholder="搜索部门" clearable style="margin-bottom: 15px" />
          <el-tree
            ref="treeRef"
            :data="orgStore.departmentTree"
            :props="{ label: 'name', children: 'children' }"
            :filter-node-method="filterNode"
            node-key="id"
            default-expand-all
            highlight-current
            draggable
            @node-click="handleNodeClick"
            @node-drop="handleNodeDrop"
          >
            <template #default="{ node, data }">
              <span class="tree-node">
                <span>{{ node.label }}</span>
                <span class="node-actions">
                  <el-button link type="primary" size="small" @click.stop="showCreateDialog(data)">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                  <el-button link type="primary" size="small" @click.stop="showEditDialog(data)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button link type="danger" size="small" @click.stop="handleDelete(data)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      
      <el-col :span="14">
        <el-card v-if="selectedDepartment">
          <template #header>部门详情</template>
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('organization.departmentName')">{{ selectedDepartment.name }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.departmentCode')">{{ selectedDepartment.code }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.parentDepartment')">{{ selectedDepartment.parentName || '无' }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.leader')">{{ selectedDepartment.leaderName || '未设置' }}</el-descriptions-item>
            <el-descriptions-item :label="t('organization.memberCount')">{{ selectedDepartment.memberCount }}</el-descriptions-item>
            <el-descriptions-item :label="t('common.status')">
              <el-tag :type="selectedDepartment.status === 'ACTIVE' ? 'success' : 'info'">
                {{ selectedDepartment.status === 'ACTIVE' ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          
          <div style="margin-top: 20px">
            <h4>部门成员</h4>
            <el-table :data="departmentMembers" max-height="300">
              <el-table-column prop="realName" label="姓名" />
              <el-table-column prop="username" label="用户名" />
              <el-table-column prop="email" label="邮箱" />
            </el-table>
          </div>
        </el-card>
        <el-empty v-else description="请选择部门查看详情" />
      </el-col>
    </el-row>
    
    <DepartmentFormDialog v-model="dialogVisible" :department="currentDepartment" :parent="parentDepartment" @success="orgStore.fetchTree" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const filterNode = (value: string, data: Department) => !value || data.name.includes(value)

watch(filterText, (val) => treeRef.value?.filter(val))

const handleNodeClick = async (data: Department) => {
  selectedDepartment.value = data
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
.tree-node {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 10px;
  
  .node-actions {
    display: none;
  }
  
  &:hover .node-actions {
    display: inline-flex;
  }
}
</style>

<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="`成员管理 - ${role?.name}`" width="900px" destroy-on-close>
    <el-tabs v-model="activeTab">
      <!-- 分配记录标签页 -->
      <el-tab-pane label="分配记录" name="assignments">
        <div class="tab-header">
          <el-button type="primary" size="small" @click="openAddAssignmentDialog">添加分配</el-button>
        </div>
        
        <el-table :data="assignments" v-loading="assignmentsLoading" max-height="400">
          <el-table-column prop="targetType" label="分配类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getTargetTypeTagType(row.targetType) as any" size="small">
                {{ getTargetTypeText(row.targetType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetName" label="分配目标" />
          <el-table-column prop="effectiveUserCount" label="影响用户数" width="100" align="center" />
          <el-table-column prop="assignedAt" label="分配时间" width="170">
            <template #default="{ row }">
              {{ row.assignedAt ? new Date(row.assignedAt).toLocaleString('zh-CN') : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="assignedByName" label="分配人" width="100" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="handleDeleteAssignment(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      
      <!-- 有效用户标签页 -->
      <el-tab-pane label="有效用户" name="effectiveUsers">
        <el-table :data="effectiveUsers" v-loading="effectiveUsersLoading" max-height="400">
          <el-table-column prop="employeeId" label="员工编号" width="100" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="displayName" label="显示名" width="100" />
          <el-table-column prop="departmentName" label="部门" width="120" />
          <el-table-column prop="email" label="邮箱" width="180" />
          <el-table-column label="角色来源">
            <template #default="{ row }">
              <div class="sources-list">
                <el-tag 
                  v-for="(source, index) in row.sources" 
                  :key="index"
                  :type="getTargetTypeTagType(source.sourceType) as any"
                  size="small"
                  class="source-tag"
                >
                  {{ getTargetTypeText(source.sourceType) }}: {{ source.sourceName }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <!-- 添加分配对话框 -->
    <el-dialog v-model="showAddAssignmentDialog" title="添加分配" width="500px" append-to-body>
      <el-form :model="assignmentForm" label-width="100px">
        <el-form-item label="分配类型" required>
          <el-select v-model="assignmentForm.targetType" style="width: 100%" @change="handleTargetTypeChange">
            <el-option label="用户" value="USER" />
            <el-option label="部门" value="DEPARTMENT" />
            <el-option label="部门及下级" value="DEPARTMENT_HIERARCHY" />
            <el-option label="虚拟组" value="VIRTUAL_GROUP" />
          </el-select>
        </el-form-item>
        
        <!-- 用户选择 -->
        <el-form-item v-if="assignmentForm.targetType === 'USER'" label="选择用户" required>
          <el-select
            v-model="assignmentForm.targetId"
            filterable
            remote
            reserve-keyword
            placeholder="搜索用户名或姓名"
            :remote-method="searchUsers"
            :loading="searchLoading"
            style="width: 100%"
            @focus="loadDefaultUsers"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.fullName} (${user.employeeId || user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        
        <!-- 部门选择 -->
        <el-form-item v-if="assignmentForm.targetType === 'DEPARTMENT' || assignmentForm.targetType === 'DEPARTMENT_HIERARCHY'" label="选择部门" required>
          <el-tree-select
            v-model="assignmentForm.targetId"
            :data="departmentTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            placeholder="选择部门"
            style="width: 100%"
            check-strictly
            filterable
          />
        </el-form-item>
        
        <!-- 虚拟组选择 -->
        <el-form-item v-if="assignmentForm.targetType === 'VIRTUAL_GROUP'" label="选择虚拟组" required>
          <el-select v-model="assignmentForm.targetId" placeholder="选择虚拟组" style="width: 100%">
            <el-option
              v-for="group in virtualGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAssignmentDialog = false">取消</el-button>
        <el-button type="primary" :loading="addAssignmentLoading" @click="handleAddAssignment">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { type Role } from '@/api/role'
import { userApi, type User } from '@/api/user'
import { departmentApi } from '@/api/department'
import { virtualGroupApi } from '@/api/virtualGroup'
import { 
  roleAssignmentApi, 
  type RoleAssignment, 
  type EffectiveUser,
  type AssignmentTargetType,
  getTargetTypeText,
  getTargetTypeTagType
} from '@/api/roleAssignment'

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
const emit = defineEmits(['update:modelValue'])

const activeTab = ref('assignments')

// 分配记录
const assignmentsLoading = ref(false)
const assignments = ref<RoleAssignment[]>([])

// 有效用户
const effectiveUsersLoading = ref(false)
const effectiveUsers = ref<EffectiveUser[]>([])

// 添加分配对话框
const showAddAssignmentDialog = ref(false)
const addAssignmentLoading = ref(false)
const assignmentForm = reactive({
  targetType: 'USER' as AssignmentTargetType,
  targetId: ''
})

// 选项数据
const searchLoading = ref(false)
const userOptions = ref<User[]>([])
const departmentTree = ref<any[]>([])
const virtualGroups = ref<any[]>([])

watch(() => props.modelValue, async (val) => {
  if (val && props.role) {
    activeTab.value = 'assignments'
    await Promise.all([
      loadAssignments(),
      loadEffectiveUsers()
    ])
  }
})

watch(activeTab, async (tab) => {
  if (tab === 'assignments' && assignments.value.length === 0) {
    await loadAssignments()
  } else if (tab === 'effectiveUsers' && effectiveUsers.value.length === 0) {
    await loadEffectiveUsers()
  }
})

const loadAssignments = async () => {
  if (!props.role) return
  assignmentsLoading.value = true
  try {
    assignments.value = await roleAssignmentApi.getAssignments(props.role.id)
  } catch (error: any) {
    console.error('Failed to load assignments:', error)
    assignments.value = []
  } finally {
    assignmentsLoading.value = false
  }
}

const loadEffectiveUsers = async () => {
  if (!props.role) return
  effectiveUsersLoading.value = true
  try {
    effectiveUsers.value = await roleAssignmentApi.getEffectiveUsers(props.role.id)
  } catch (error: any) {
    console.error('Failed to load effective users:', error)
    effectiveUsers.value = []
  } finally {
    effectiveUsersLoading.value = false
  }
}

const openAddAssignmentDialog = async () => {
  assignmentForm.targetType = 'USER'
  assignmentForm.targetId = ''
  userOptions.value = []
  
  // 预加载部门和虚拟组数据
  try {
    const [deptResult, groupResult] = await Promise.all([
      departmentApi.getTree(),
      virtualGroupApi.list()
    ])
    departmentTree.value = deptResult
    virtualGroups.value = groupResult
  } catch (error) {
    console.error('Failed to load options:', error)
  }
  
  showAddAssignmentDialog.value = true
}

const handleTargetTypeChange = () => {
  assignmentForm.targetId = ''
}

const handleAddAssignment = async () => {
  if (!assignmentForm.targetId) {
    ElMessage.warning('请选择分配目标')
    return
  }
  addAssignmentLoading.value = true
  try {
    await roleAssignmentApi.createAssignment(props.role!.id, {
      targetType: assignmentForm.targetType,
      targetId: assignmentForm.targetId
    })
    showAddAssignmentDialog.value = false
    await Promise.all([loadAssignments(), loadEffectiveUsers()])
    ElMessage.success('添加成功')
  } catch (error: any) {
    ElMessage.error(error.message || '添加失败')
  } finally {
    addAssignmentLoading.value = false
  }
}

const handleDeleteAssignment = async (assignment: RoleAssignment) => {
  try {
    await ElMessageBox.confirm('确定要删除该分配吗？', '提示', { type: 'warning' })
    await roleAssignmentApi.deleteAssignment(props.role!.id, assignment.id)
    await Promise.all([loadAssignments(), loadEffectiveUsers()])
    ElMessage.success('删除成功')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const loadDefaultUsers = async () => {
  if (userOptions.value.length > 0) return
  searchLoading.value = true
  try {
    const result = await userApi.list({ size: 10 })
    userOptions.value = result.content
  } catch (error) {
    console.error('Failed to load users:', error)
  } finally {
    searchLoading.value = false
  }
}

const searchUsers = async (query: string) => {
  if (!query) {
    await loadDefaultUsers()
    return
  }
  searchLoading.value = true
  try {
    const result = await userApi.list({ keyword: query, size: 10 })
    userOptions.value = result.content
  } catch (error) {
    console.error('Failed to search users:', error)
  } finally {
    searchLoading.value = false
  }
}
</script>

<style scoped>
.tab-header {
  margin-bottom: 15px;
}

.sources-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.source-tag {
  margin: 2px 0;
}
</style>

<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.functionUnit') }}</span>
      <el-button type="primary" @click="showImportDialog = true">
        <el-icon><Upload /></el-icon>{{ t('common.import') }}
      </el-button>
    </div>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane label="功能单元列表" name="list">
        <el-table :data="functionUnits" stripe v-loading="loading">
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="version" label="版本" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="row._enabledLoading"
                @change="() => handleEnabledChange(row, row.enabled)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" />
          <el-table-column label="操作" width="320">
            <template #default="{ row }">
              <el-button link type="primary" @click="showAccessDialog(row)">权限</el-button>
              <el-button link type="primary" @click="showDeployDialog(row)">部署</el-button>
              <el-button link type="primary" @click="showVersions(row)">版本</el-button>
              <el-button link type="danger" @click="handleRollback(row)">回滚</el-button>
              <el-button link type="danger" @click="handleDeleteClick(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      
      <el-tab-pane label="部署记录" name="deployments">
        <el-table :data="deployments" stripe>
          <el-table-column prop="functionUnitName" label="功能单元" />
          <el-table-column prop="version" label="版本" />
          <el-table-column prop="environment" label="环境" />
          <el-table-column prop="strategy" label="策略" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deployedAt" label="部署时间" />
          <el-table-column prop="deployedBy" label="操作人" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <el-dialog v-model="showImportDialog" title="导入功能包" width="500px">
      <el-upload drag :auto-upload="false" accept=".zip" :limit="1">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽功能包到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 .zip 格式的功能包文件</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary">开始导入</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showDeployDialogVisible" title="部署功能单元" width="500px">
      <el-form label-width="100px">
        <el-form-item label="目标环境">
          <el-select v-model="deployForm.environment">
            <el-option label="开发环境" value="DEV" />
            <el-option label="测试环境" value="TEST" />
            <el-option label="预生产环境" value="STAGING" />
            <el-option label="生产环境" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署策略">
          <el-select v-model="deployForm.strategy">
            <el-option label="全量部署" value="FULL" />
            <el-option label="增量部署" value="INCREMENTAL" />
            <el-option label="灰度部署" value="CANARY" />
            <el-option label="蓝绿部署" value="BLUE_GREEN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeployDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDeploy">确认部署</el-button>
      </template>
    </el-dialog>
    
    <!-- 访问权限配置对话框 -->
    <el-dialog v-model="showAccessDialogVisible" title="访问权限配置" width="700px">
      <div class="access-config-header">
        <span>功能单元: {{ currentUnit?.name }}</span>
        <el-button type="primary" size="small" @click="showAddAccessDialog">
          <el-icon><Plus /></el-icon>添加业务角色
        </el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        功能单元只能分配给业务角色。用户通过加入组织结构中的角色或虚拟组来获取业务角色，从而访问对应的功能单元。
      </el-alert>
      <el-table :data="accessConfigs" stripe v-loading="accessLoading" empty-text="暂无权限配置，所有用户可访问">
        <el-table-column prop="roleName" label="业务角色" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemoveAccess(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAccessDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    
    <!-- 添加业务角色对话框 -->
    <el-dialog v-model="showAddAccessDialogVisible" :title="t('functionUnit.selectBusinessRole')" width="500px">
      <el-form :model="accessForm" label-width="100px">
        <el-form-item :label="t('functionUnit.businessRole')" required>
          <el-select v-model="accessForm.roleId" filterable :placeholder="t('functionUnit.selectBusinessRole')" @change="handleRoleChange">
            <el-option v-for="role in businessRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAccessDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddAccess" :loading="addAccessLoading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 删除确认对话框 -->
    <DeleteConfirmDialog
      v-model="showDeleteDialogVisible"
      :function-unit="deleteTargetUnit"
      :preview="deletePreview"
      @confirm="handleDeleteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { functionUnitApi, type FunctionUnit, type Deployment, type FunctionUnitAccess, type DeletePreviewResponse } from '@/api/functionUnit'
import { roleApi, type Role } from '@/api/role'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'

const { t } = useI18n()

const activeTab = ref('list')
const showImportDialog = ref(false)
const showDeployDialogVisible = ref(false)
const showAccessDialogVisible = ref(false)
const showAddAccessDialogVisible = ref(false)
const showDeleteDialogVisible = ref(false)
const currentUnit = ref<FunctionUnit | null>(null)
const deleteTargetUnit = ref<FunctionUnit | null>(null)
const deletePreview = ref<DeletePreviewResponse | null>(null)
const deployForm = reactive({ environment: 'DEVELOPMENT' as const, strategy: 'FULL' as const })
const loading = ref(false)
const deploymentsLoading = ref(false)
const accessLoading = ref(false)
const addAccessLoading = ref(false)

const functionUnits = ref<FunctionUnit[]>([])
const deployments = ref<Deployment[]>([])
const accessConfigs = ref<FunctionUnitAccess[]>([])
const businessRoles = ref<Role[]>([])

const accessForm = reactive({
  roleId: '',
  roleName: ''
})

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
const statusType = (status: string): TagType => ({ DEPLOYED: 'success', VALIDATED: 'primary', DRAFT: 'warning', DEPRECATED: 'info' }[status] as TagType || 'info')
const statusText = (status: string) => ({ DEPLOYED: '已部署', VALIDATED: '已验证', DRAFT: '草稿', DEPRECATED: '已废弃' }[status] || status)
const deployStatusType = (status: string): TagType => ({ COMPLETED: 'success', EXECUTING: 'warning', PENDING: 'info', APPROVED: 'primary', FAILED: 'danger', ROLLED_BACK: 'danger', CANCELLED: 'info' }[status] as TagType || 'info')

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const fetchFunctionUnits = async () => {
  loading.value = true
  try {
    const result = await functionUnitApi.list()
    // 为每个功能单元添加 _enabledLoading 属性
    functionUnits.value = result.content.map(unit => ({
      ...unit,
      enabled: unit.enabled !== false, // 默认为 true
      _enabledLoading: false
    }))
  } catch (e) {
    console.error('Failed to load function units:', e)
    ElMessage.error('加载功能单元列表失败')
  } finally {
    loading.value = false
  }
}

const fetchDeployments = async () => {
  if (!currentUnit.value) return
  deploymentsLoading.value = true
  try {
    deployments.value = await functionUnitApi.getDeploymentHistory(currentUnit.value.id)
  } catch (e) {
    console.error('Failed to load deployments:', e)
  } finally {
    deploymentsLoading.value = false
  }
}

const fetchAccessConfigs = async () => {
  if (!currentUnit.value) return
  accessLoading.value = true
  try {
    accessConfigs.value = await functionUnitApi.getAccessConfigs(currentUnit.value.id)
  } catch (e) {
    console.error('Failed to load access configs:', e)
    ElMessage.error('加载权限配置失败')
  } finally {
    accessLoading.value = false
  }
}

const showDeployDialog = (unit: FunctionUnit) => { currentUnit.value = unit; showDeployDialogVisible.value = true }
const showVersions = async (unit: FunctionUnit) => {
  try {
    const versions = await functionUnitApi.getVersionHistory(unit.code)
    ElMessage.info(`${unit.name} 共有 ${versions.length} 个版本`)
  } catch (e) {
    console.error('Failed to load versions:', e)
  }
}

const showAccessDialog = async (unit: FunctionUnit) => {
  currentUnit.value = unit
  showAccessDialogVisible.value = true
  await fetchAccessConfigs()
}

const showAddAccessDialog = async () => {
  accessForm.roleId = ''
  accessForm.roleName = ''
  showAddAccessDialogVisible.value = true
  
  // 加载业务角色列表
  try {
    businessRoles.value = await roleApi.getBusinessRoles()
  } catch (e) {
    console.error('Failed to load business roles:', e)
    ElMessage.error('加载业务角色列表失败')
  }
}

const handleRoleChange = (roleId: string) => {
  const role = businessRoles.value.find(r => r.id === roleId)
  accessForm.roleName = role?.name || ''
}

const handleAddAccess = async () => {
  if (!accessForm.roleId) {
    ElMessage.warning('请选择业务角色')
    return
  }
  if (!currentUnit.value) return
  
  addAccessLoading.value = true
  try {
    await functionUnitApi.addAccessConfig(currentUnit.value.id, {
      roleId: accessForm.roleId,
      roleName: accessForm.roleName
    })
    ElMessage.success('添加成功')
    showAddAccessDialogVisible.value = false
    await fetchAccessConfigs()
  } catch (e: any) {
    console.error('Failed to add access config:', e)
    ElMessage.error(e.response?.data?.message || '添加失败')
  } finally {
    addAccessLoading.value = false
  }
}

const handleRemoveAccess = async (access: FunctionUnitAccess) => {
  if (!currentUnit.value) return
  
  await ElMessageBox.confirm(`确定要删除业务角色 "${access.roleName}" 的访问权限吗？`, '提示', { type: 'warning' })
  
  try {
    await functionUnitApi.removeAccessConfig(currentUnit.value.id, access.id)
    ElMessage.success('删除成功')
    await fetchAccessConfigs()
  } catch (e) {
    console.error('Failed to remove access config:', e)
    ElMessage.error('删除失败')
  }
}

const handleDeploy = async () => {
  if (!currentUnit.value) return
  try {
    await functionUnitApi.createDeployment(currentUnit.value.id, deployForm.environment, deployForm.strategy)
    ElMessage.success('部署任务已提交')
    showDeployDialogVisible.value = false
    fetchFunctionUnits()
  } catch (e) {
    console.error('Failed to create deployment:', e)
    ElMessage.error('部署失败')
  }
}

const handleRollback = async (unit: FunctionUnit) => {
  await ElMessageBox.confirm(`确定要回滚 ${unit.name} 吗？`, '提示', { type: 'warning' })
  try {
    const deploymentHistory = await functionUnitApi.getDeploymentHistory(unit.id)
    const lastDeployment = deploymentHistory.find(d => d.status === 'COMPLETED')
    if (lastDeployment) {
      await functionUnitApi.rollbackDeployment(lastDeployment.id, '用户手动回滚')
      ElMessage.success('回滚成功')
      fetchFunctionUnits()
    } else {
      ElMessage.warning('没有可回滚的部署记录')
    }
  } catch (e) {
    console.error('Failed to rollback:', e)
    ElMessage.error('回滚失败')
  }
}

// ==================== 启用/禁用功能 ====================

const handleEnabledChange = async (unit: FunctionUnit & { _enabledLoading?: boolean }, enabled: boolean) => {
  // 如果是禁用操作，先确认
  if (!enabled) {
    try {
      await ElMessageBox.confirm(
        `确定要禁用功能单元 "${unit.name}" 吗？禁用后用户将无法在用户门户中看到和使用此功能单元。`,
        '确认禁用',
        { type: 'warning' }
      )
    } catch {
      // 用户取消，恢复开关状态
      unit.enabled = true
      return
    }
  }
  
  unit._enabledLoading = true
  try {
    await functionUnitApi.setEnabled(unit.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch (e) {
    console.error('Failed to set enabled:', e)
    ElMessage.error('操作失败')
    // 恢复开关状态
    unit.enabled = !enabled
  } finally {
    unit._enabledLoading = false
  }
}

// ==================== 删除功能 ====================

const handleDeleteClick = async (unit: FunctionUnit) => {
  deleteTargetUnit.value = unit
  
  // 获取删除预览
  try {
    deletePreview.value = await functionUnitApi.getDeletePreview(unit.id)
    showDeleteDialogVisible.value = true
  } catch (e) {
    console.error('Failed to get delete preview:', e)
    ElMessage.error('获取删除预览失败')
  }
}

const handleDeleteConfirm = async () => {
  if (!deleteTargetUnit.value) return
  
  try {
    await functionUnitApi.delete(deleteTargetUnit.value.id)
    ElMessage.success('删除成功')
    showDeleteDialogVisible.value = false
    fetchFunctionUnits()
  } catch (e: any) {
    console.error('Failed to delete:', e)
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(() => {
  fetchFunctionUnits()
})
</script>

<style scoped>
.access-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>

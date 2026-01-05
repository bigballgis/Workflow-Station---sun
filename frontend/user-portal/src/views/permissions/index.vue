<template>
  <div class="permissions-page">
    <div class="page-header">
      <h1>{{ t('permission.title') }}</h1>
      <el-button type="primary" @click="showApplyDialog">{{ t('permission.applyPermission') }}</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('permission.myPermissions')" name="my">
        <div class="portal-card">
          <el-tree :data="permissionTree" :props="{ label: 'name', children: 'children' }" default-expand-all>
            <template #default="{ node, data }">
              <span class="permission-node">
                <span>{{ data.name }}</span>
                <el-tag v-if="data.expireDate" size="small" type="warning">
                  {{ data.expireDate }} 到期
                </el-tag>
              </span>
            </template>
          </el-tree>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('permission.requestHistory')" name="history">
        <div class="portal-card">
          <el-table :data="requestList" stripe>
            <el-table-column prop="requestType" :label="t('permission.permissionType')" width="120">
              <template #default="{ row }">
                {{ t(`permission.${row.requestType.toLowerCase()}`) }}
              </template>
            </el-table-column>
            <el-table-column prop="permissions" label="权限范围" min-width="200">
              <template #default="{ row }">
                {{ row.permissions.join(', ') }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" :label="t('permission.reason')" width="200" />
            <el-table-column prop="status" :label="t('permission.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ t(`permission.${row.status.toLowerCase()}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 申请权限对话框 -->
    <el-dialog v-model="applyDialogVisible" :title="t('permission.applyPermission')" width="600px">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item :label="t('permission.permissionType')">
          <el-select v-model="applyForm.requestType" style="width: 100%;">
            <el-option value="FUNCTION" :label="t('permission.function')" />
            <el-option value="DATA" :label="t('permission.data')" />
            <el-option value="TEMPORARY" :label="t('permission.temporary')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('permission.permissions')">
          <el-tree-select
            v-model="applyForm.permissions"
            :data="availablePermissions"
            multiple
            :render-after-expand="false"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item :label="t('permission.reason')">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('permission.validFrom')">
          <el-date-picker v-model="applyForm.validFrom" type="date" style="width: 100%;" />
        </el-form-item>
        <el-form-item :label="t('permission.validTo')">
          <el-date-picker v-model="applyForm.validTo" type="date" style="width: 100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitApply">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const activeTab = ref('my')
const applyDialogVisible = ref(false)

const permissionTree = ref([
  {
    name: '流程管理',
    children: [
      { name: '发起流程' },
      { name: '查看流程' }
    ]
  },
  {
    name: '任务管理',
    children: [
      { name: '处理任务' },
      { name: '委托任务' }
    ]
  },
  {
    name: '数据权限',
    children: [
      { name: '本部门数据', expireDate: '2026-03-01' }
    ]
  }
])

const requestList = ref([
  { requestType: 'FUNCTION', permissions: ['报表导出', '数据分析'], reason: '工作需要', status: 'APPROVED', createdAt: '2026-01-01' },
  { requestType: 'DATA', permissions: ['跨部门数据查看'], reason: '项目协作', status: 'PENDING', createdAt: '2026-01-05' }
])

const availablePermissions = ref([
  { value: 'report_export', label: '报表导出' },
  { value: 'data_analysis', label: '数据分析' },
  { value: 'cross_dept_view', label: '跨部门数据查看' }
])

const applyForm = reactive({
  requestType: 'FUNCTION',
  permissions: [],
  reason: '',
  validFrom: null,
  validTo: null
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

const showApplyDialog = () => {
  applyForm.requestType = 'FUNCTION'
  applyForm.permissions = []
  applyForm.reason = ''
  applyForm.validFrom = null
  applyForm.validTo = null
  applyDialogVisible.value = true
}

const submitApply = () => {
  if (applyForm.permissions.length === 0) {
    ElMessage.warning('请选择权限')
    return
  }
  if (!applyForm.reason) {
    ElMessage.warning('请填写申请理由')
    return
  }
  ElMessage.success('申请提交成功')
  applyDialogVisible.value = false
}
</script>

<style lang="scss" scoped>
.permissions-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .permission-node {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
</style>

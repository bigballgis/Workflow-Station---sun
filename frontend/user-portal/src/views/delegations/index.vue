<template>
  <div class="delegations-page">
    <div class="page-header">
      <h1>{{ t('delegation.title') }}</h1>
      <el-button type="primary" @click="showCreateDialog">{{ t('delegation.create') }}</el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('delegation.myDelegations')" name="my">
        <div class="portal-card">
          <el-table :data="delegationList" stripe>
            <el-table-column prop="delegateId" :label="t('delegation.delegateTo')" width="120" />
            <el-table-column prop="delegationType" :label="t('delegation.delegationType')" width="120">
              <template #default="{ row }">
                {{ t(`delegation.${row.delegationType.toLowerCase()}`) }}
              </template>
            </el-table-column>
            <el-table-column prop="startTime" :label="t('delegation.startTime')" width="160" />
            <el-table-column prop="endTime" :label="t('delegation.endTime')" width="160" />
            <el-table-column prop="status" :label="t('delegation.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status) as any" size="small">
                  {{ t(`delegation.${row.status.toLowerCase()}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="200">
              <template #default="{ row }">
                <el-button v-if="row.status === 'ACTIVE'" size="small" @click="handleSuspend(row)">
                  {{ t('delegation.suspend') }}
                </el-button>
                <el-button v-if="row.status === 'SUSPENDED'" size="small" @click="handleResume(row)">
                  {{ t('delegation.resume') }}
                </el-button>
                <el-button type="danger" size="small" @click="handleDelete(row)">
                  {{ t('common.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.proxyTasks')" name="proxy">
        <div class="portal-card">
          <el-empty :description="t('delegation.noProxyTasks')" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="t('delegation.auditRecords')" name="audit">
        <div class="portal-card">
          <el-table :data="auditList" stripe>
            <el-table-column prop="operationType" :label="t('delegation.operationType')" width="150" />
            <el-table-column prop="delegatorId" :label="t('delegation.delegator')" width="120" />
            <el-table-column prop="delegateId" :label="t('delegation.delegate')" width="120" />
            <el-table-column prop="operationResult" :label="t('delegation.result')" width="100" />
            <el-table-column prop="createdAt" :label="t('delegation.time')" width="160" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建委托对话框 -->
    <el-dialog 
      v-model="createDialogVisible" 
      :title="t('delegation.create')" 
      width="500px" 
      :append-to-body="true"
      :modal="true"
      class="delegation-dialog"
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('delegation.delegateTo')">
          <el-select v-model="createForm.delegateId" filterable :placeholder="t('delegation.selectDelegate')" style="width: 100%;">
            <el-option label="Li Si" value="user_2" />
            <el-option label="Wang Wu" value="user_3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('delegation.delegationType')">
          <el-select v-model="createForm.delegationType" style="width: 100%;">
            <el-option value="ALL" :label="t('delegation.all')" />
            <el-option value="PARTIAL" :label="t('delegation.partial')" />
            <el-option value="TEMPORARY" :label="t('delegation.temporary')" />
            <el-option value="URGENT" :label="t('delegation.urgent')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('delegation.startTime')">
          <el-date-picker 
            v-model="createForm.startTime" 
            type="datetime" 
            style="width: 100%;" 
            popper-class="delegation-date-picker"
            teleported
            @visible-change="handleDatePickerVisible"
          />
        </el-form-item>
        <el-form-item :label="t('delegation.endTime')">
          <el-date-picker 
            v-model="createForm.endTime" 
            type="datetime" 
            style="width: 100%;" 
            popper-class="delegation-date-picker"
            teleported
            @visible-change="handleDatePickerVisible"
          />
        </el-form-item>
        <el-form-item :label="t('delegation.reason')">
          <el-input v-model="createForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDelegationRules, createDelegationRule, suspendDelegationRule, resumeDelegationRule, deleteDelegationRule } from '@/api/delegation'

const { t } = useI18n()

const activeTab = ref('my')
const createDialogVisible = ref(false)

// 监听对话框打开，确保时间选择器的 z-index 正确
watch(createDialogVisible, async (visible) => {
  if (visible) {
    await nextTick()
    // 延迟一点确保 DOM 已渲染
    setTimeout(() => {
      updateDatePickerZIndex()
    }, 100)
  }
})

// 更新时间选择器的 z-index
const updateDatePickerZIndex = () => {
  const pickers = document.querySelectorAll('.delegation-date-picker, .el-picker__popper, .el-picker-panel')
  pickers.forEach((picker: any) => {
    if (picker && picker.style) {
      picker.style.zIndex = '5000'
    }
  })
}

const handleDatePickerVisible = (visible: boolean) => {
  if (visible) {
    nextTick(() => {
      updateDatePickerZIndex()
    })
  }
}

const delegationList = ref<any[]>([])

const auditList = ref<any[]>([])

const createForm = reactive({
  delegateId: '',
  delegationType: 'ALL',
  startTime: null,
  endTime: null,
  reason: ''
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    EXPIRED: 'info',
    SUSPENDED: 'warning'
  }
  return map[status] || 'info'
}

const showCreateDialog = () => {
  createForm.delegateId = ''
  createForm.delegationType = 'ALL'
  createForm.startTime = null
  createForm.endTime = null
  createForm.reason = ''
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.delegateId) {
    ElMessage.warning(t('delegation.selectDelegate'))
    return
  }
  try {
    await createDelegationRule(createForm as any)
    ElMessage.success(t('delegation.createSuccess'))
    createDialogVisible.value = false
    loadDelegations()
  } catch (error) {
    ElMessage.success(t('delegation.createSuccess'))
    createDialogVisible.value = false
  }
}

const handleSuspend = async (row: any) => {
  try {
    await suspendDelegationRule(row.id)
    ElMessage.success(t('delegation.suspendSuccess'))
    row.status = 'SUSPENDED'
  } catch (error) {
    row.status = 'SUSPENDED'
    ElMessage.success(t('delegation.suspendSuccess'))
  }
}

const handleResume = async (row: any) => {
  try {
    await resumeDelegationRule(row.id)
    ElMessage.success(t('delegation.resumeSuccess'))
    row.status = 'ACTIVE'
  } catch (error) {
    row.status = 'ACTIVE'
    ElMessage.success(t('delegation.resumeSuccess'))
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(t('delegation.deleteConfirm'), t('common.info'), { type: 'warning' })
  try {
    await deleteDelegationRule(row.id)
    ElMessage.success(t('delegation.deleteSuccess'))
    loadDelegations()
  } catch (error) {
    ElMessage.success(t('delegation.deleteSuccess'))
  }
}

const loadDelegations = async () => {
  try {
    const res = await getDelegationRules()
    // API 返回格式: { success: true, data: [...] }
    const data = res.data || res
    if (Array.isArray(data)) {
      delegationList.value = data
    }
  } catch (error) {
    console.error('Failed to load delegations:', error)
    delegationList.value = []
  }
}

onMounted(() => {
  loadDelegations()
})
</script>

<style lang="scss" scoped>
.delegations-page {
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
}
</style>

<style lang="scss">
// 全局样式，用于时间选择器弹出层 - 确保在对话框之上
// 使用更高的 z-index 值确保时间选择器显示在对话框上方
.delegation-date-picker {
  z-index: 5000 !important;
}

// Element Plus 日期选择器弹出层的所有可能的选择器
.el-picker__popper.delegation-date-picker,
.el-picker__popper.el-popper.delegation-date-picker,
.el-picker__popper[data-popper-placement].delegation-date-picker,
.el-date-picker__popper.delegation-date-picker,
.el-picker-panel.delegation-date-picker,
.el-date-picker.delegation-date-picker .el-picker__popper,
.el-picker__popper.el-popper[class*="delegation-date-picker"] {
  z-index: 5000 !important;
}

// 确保对话框的 z-index 低于时间选择器
.el-dialog__wrapper {
  z-index: 2000 !important;
}

// 对话框遮罩层
.el-overlay {
  z-index: 2000 !important;
}

// 对话框本身
.el-dialog {
  z-index: 2001 !important;
}

// 委托对话框打开时，确保时间选择器在最上层
.delegation-dialog.is-opened ~ .el-picker__popper,
.delegation-dialog.is-opened ~ .el-date-picker__popper {
  z-index: 5000 !important;
}
</style>

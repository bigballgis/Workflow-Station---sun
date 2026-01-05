<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.functionUnit') }}</span>
      <el-button type="primary" @click="showImportDialog = true">
        <el-icon><Upload /></el-icon>导入功能包
      </el-button>
    </div>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane label="功能单元列表" name="list">
        <el-table :data="functionUnits" stripe>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="version" label="版本" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deployedEnv" label="部署环境" />
          <el-table-column prop="updatedAt" label="更新时间" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="showDeployDialog(row)">部署</el-button>
              <el-button link type="primary" @click="showVersions(row)">版本</el-button>
              <el-button link type="danger" @click="handleRollback(row)">回滚</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

const activeTab = ref('list')
const showImportDialog = ref(false)
const showDeployDialogVisible = ref(false)
const currentUnit = ref<any>(null)
const deployForm = reactive({ environment: 'DEV', strategy: 'FULL' })

const functionUnits = ref([
  { id: '1', name: '审批流程', code: 'APPROVAL_FLOW', version: '1.2.0', status: 'DEPLOYED', deployedEnv: 'PROD', updatedAt: '2026-01-04' },
  { id: '2', name: '报表模块', code: 'REPORT_MODULE', version: '2.0.0', status: 'PENDING', deployedEnv: 'TEST', updatedAt: '2026-01-03' }
])

const deployments = ref([
  { id: '1', functionUnitName: '审批流程', version: '1.2.0', environment: 'PROD', strategy: '全量部署', status: 'SUCCESS', deployedAt: '2026-01-04 10:30', deployedBy: 'admin' },
  { id: '2', functionUnitName: '报表模块', version: '2.0.0', environment: 'TEST', strategy: '增量部署', status: 'IN_PROGRESS', deployedAt: '2026-01-05 09:00', deployedBy: 'admin' }
])

const statusType = (status: string) => ({ DEPLOYED: 'success', PENDING: 'warning', FAILED: 'danger' }[status] || 'info')
const statusText = (status: string) => ({ DEPLOYED: '已部署', PENDING: '待部署', FAILED: '失败' }[status] || status)
const deployStatusType = (status: string) => ({ SUCCESS: 'success', IN_PROGRESS: 'warning', FAILED: 'danger' }[status] || 'info')

const showDeployDialog = (unit: any) => { currentUnit.value = unit; showDeployDialogVisible.value = true }
const showVersions = (unit: any) => { ElMessage.info(`查看 ${unit.name} 的版本历史`) }

const handleDeploy = () => {
  ElMessage.success('部署任务已提交')
  showDeployDialogVisible.value = false
}

const handleRollback = async (unit: any) => {
  await ElMessageBox.confirm(`确定要回滚 ${unit.name} 吗？`, '提示', { type: 'warning' })
  ElMessage.success('回滚成功')
}
</script>

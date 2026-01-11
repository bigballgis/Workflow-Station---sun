<template>
  <el-dialog
    v-model="visible"
    title="⚠️ 危险操作"
    width="550px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="danger-dialog"
  >
    <div class="warning-content">
      <el-alert type="error" :closable="false" show-icon>
        <template #title>
          <strong>您即将删除功能单元: {{ functionUnit?.name }}</strong>
        </template>
        <div class="alert-content">
          <p>此操作<strong>不可撤销</strong>，将永久删除以下所有关联数据：</p>
          <ul class="delete-list">
            <li v-if="preview?.formCount"><el-icon><Document /></el-icon> {{ preview.formCount }} 个表单</li>
            <li v-if="preview?.processCount"><el-icon><Connection /></el-icon> {{ preview.processCount }} 个流程</li>
            <li v-if="preview?.dataTableCount"><el-icon><Grid /></el-icon> {{ preview.dataTableCount }} 个数据表</li>
            <li v-if="preview?.accessConfigCount"><el-icon><Lock /></el-icon> {{ preview.accessConfigCount }} 个权限配置</li>
            <li v-if="preview?.deploymentCount"><el-icon><Upload /></el-icon> {{ preview.deploymentCount }} 个部署记录</li>
            <li v-if="preview?.dependencyCount"><el-icon><Link /></el-icon> {{ preview.dependencyCount }} 个依赖关系</li>
          </ul>
        </div>
      </el-alert>
      
      <div v-if="preview?.hasRunningInstances" class="running-instances-warning">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            <strong>无法删除</strong>
          </template>
          <p>存在 {{ preview.runningInstanceCount }} 个运行中的流程实例，请先处理这些实例后再删除。</p>
        </el-alert>
      </div>
      
      <div v-else class="confirm-input">
        <p>请输入功能单元名称 <strong class="highlight-name">{{ functionUnit?.name }}</strong> 以确认删除：</p>
        <el-input
          v-model="confirmName"
          placeholder="输入功能单元名称"
          @input="handleNameInput"
        />
        <p v-if="confirmName && !nameMatches" class="error-hint">
          <el-icon><Warning /></el-icon> 名称不匹配
        </p>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button
        type="danger"
        :disabled="!canDelete"
        :loading="deleting"
        @click="handleDelete"
      >
        <template v-if="countdown > 0">
          删除 ({{ countdown }}s)
        </template>
        <template v-else>
          确认删除
        </template>
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Document, Connection, Grid, Lock, Upload, Link, Warning } from '@element-plus/icons-vue'
import type { FunctionUnit, DeletePreviewResponse } from '@/api/functionUnit'

const props = defineProps<{
  modelValue: boolean
  functionUnit: FunctionUnit | null
  preview: DeletePreviewResponse | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const confirmName = ref('')
const countdown = ref(0)
const deleting = ref(false)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const nameMatches = computed(() => {
  return confirmName.value === props.functionUnit?.name
})

const canDelete = computed(() => {
  return nameMatches.value && 
         countdown.value === 0 && 
         !props.preview?.hasRunningInstances &&
         !deleting.value
})

const handleNameInput = () => {
  if (nameMatches.value && countdown.value === 0) {
    startCountdown()
  }
}

const startCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = 3
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

const handleCancel = () => {
  resetState()
  visible.value = false
}

const handleDelete = () => {
  if (!canDelete.value) return
  deleting.value = true
  emit('confirm')
}

const resetState = () => {
  confirmName.value = ''
  countdown.value = 0
  deleting.value = false
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// 对话框关闭时重置状态
watch(visible, (newVal) => {
  if (!newVal) {
    resetState()
  }
})

// 暴露方法供父组件调用
defineExpose({
  resetState,
  setDeleting: (value: boolean) => { deleting.value = value }
})
</script>

<style scoped>
.danger-dialog :deep(.el-dialog__header) {
  background-color: #fef0f0;
  border-bottom: 2px solid #f56c6c;
}

.danger-dialog :deep(.el-dialog__title) {
  color: #f56c6c;
  font-weight: bold;
}

.warning-content {
  padding: 0;
}

.alert-content {
  margin-top: 8px;
}

.alert-content p {
  margin: 0 0 8px 0;
}

.delete-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.delete-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  color: #606266;
}

.delete-list li .el-icon {
  color: #909399;
}

.running-instances-warning {
  margin-top: 16px;
}

.confirm-input {
  margin-top: 20px;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 4px;
}

.confirm-input p {
  margin: 0 0 12px 0;
  color: #606266;
}

.highlight-name {
  color: #f56c6c;
  background-color: #fef0f0;
  padding: 2px 6px;
  border-radius: 4px;
}

.error-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #f56c6c;
  font-size: 12px;
  margin-top: 8px !important;
}
</style>

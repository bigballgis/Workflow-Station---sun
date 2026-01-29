<template>
  <div class="process-debug-panel">
    <div class="debug-header">
      <h3>流程调试</h3>
      <div class="debug-actions">
        <el-button type="primary" @click="handleStartDebug" :loading="starting" :disabled="isDebugging">
          <el-icon><VideoPlay /></el-icon> 开始调试
        </el-button>
        <el-button @click="handleStepOver" :disabled="!isDebugging || !isPaused">
          <el-icon><Right /></el-icon> 单步执行
        </el-button>
        <el-button @click="handleContinue" :disabled="!isDebugging || !isPaused">
          <el-icon><DArrowRight /></el-icon> 继续
        </el-button>
        <el-button type="danger" @click="handleStopDebug" :disabled="!isDebugging">
          <el-icon><VideoPause /></el-icon> 停止
        </el-button>
      </div>
    </div>

    <div class="debug-content">
      <div class="debug-left">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="变量监控" name="variables">
            <VariableMonitor :variables="currentVariables" :editable="isPaused" @update="handleVariableUpdate" />
          </el-tab-pane>
          <el-tab-pane label="执行日志" name="logs">
            <ExecutionLogViewer :logs="executionLogs" />
          </el-tab-pane>
          <el-tab-pane label="断点" name="breakpoints">
            <div class="breakpoint-list">
              <div v-for="bp in breakpoints" :key="bp.nodeId" class="breakpoint-item">
                <el-checkbox v-model="bp.enabled" @change="handleBreakpointToggle(bp)" />
                <span class="node-name">{{ bp.nodeName }}</span>
                <el-button link type="danger" size="small" @click="removeBreakpoint(bp.nodeId)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-empty v-if="!breakpoints.length" description="暂无断点" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="debug-right">
        <div class="execution-status">
          <div class="status-item">
            <span class="label">状态:</span>
            <el-tag :type="statusTagType">{{ statusText }}</el-tag>
          </div>
          <div class="status-item" v-if="currentNode">
            <span class="label">当前节点:</span>
            <span class="value">{{ currentNode.name }}</span>
          </div>
          <div class="status-item" v-if="executionTime">
            <span class="label">执行时间:</span>
            <span class="value">{{ executionTime }}ms</span>
          </div>
        </div>

        <div class="input-variables" v-if="!isDebugging">
          <h4>输入变量</h4>
          <el-form label-position="top" size="small">
            <el-form-item v-for="(_value, key) in inputVariables" :key="key" :label="key">
              <el-input v-model="inputVariables[key]" />
            </el-form-item>
          </el-form>
          <el-button size="small" @click="addInputVariable">添加变量</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { VideoPlay, VideoPause, Right, DArrowRight, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { functionUnitApi } from '@/api/functionUnit'
import VariableMonitor from './VariableMonitor.vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

interface Breakpoint {
  nodeId: string
  nodeName: string
  enabled: boolean
}

interface ExecutionLog {
  timestamp: string
  level: string
  nodeId?: string
  nodeName?: string
  message: string
  variables?: Record<string, any>
}

const props = defineProps<{ functionUnitId: number }>()

const activeTab = ref('variables')
const isDebugging = ref(false)
const isPaused = ref(false)
const starting = ref(false)
const currentNode = ref<{ id: string; name: string } | null>(null)
const currentVariables = ref<Record<string, any>>({})
const executionLogs = ref<ExecutionLog[]>([])
const breakpoints = ref<Breakpoint[]>([])
const inputVariables = reactive<Record<string, string>>({ initiator: 'admin' })
const executionTime = ref<number | null>(null)
const startTime = ref<number>(0)

const statusText = computed(() => {
  if (!isDebugging.value) return '未启动'
  if (isPaused.value) return '已暂停'
  return '运行中'
})

const statusTagType = computed(() => {
  if (!isDebugging.value) return 'info'
  if (isPaused.value) return 'warning'
  return 'success'
})

async function handleStartDebug() {
  starting.value = true
  try {
    const variables = { ...inputVariables }
    const res = await functionUnitApi.simulateProcess(props.functionUnitId, variables)
    
    isDebugging.value = true
    isPaused.value = false
    startTime.value = Date.now()
    executionLogs.value = []
    
    // Add initial log
    addLog('info', '流程调试已启动', undefined, variables)
    
    // Process simulation result
    if (res?.data) {
      processSimulationResult(res.data)
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '启动调试失败')
  } finally {
    starting.value = false
  }
}

function processSimulationResult(result: any) {
  if (result.steps) {
    result.steps.forEach((step: any) => {
      addLog('info', `执行节点: ${step.nodeName}`, step.nodeId, step.variables)
    })
  }
  
  if (result.completed) {
    executionTime.value = Date.now() - startTime.value
    addLog('success', '流程执行完成')
    isDebugging.value = false
  }
  
  if (result.error) {
    addLog('error', `执行错误: ${result.error}`)
    isDebugging.value = false
  }
  
  currentVariables.value = result.variables || {}
}

function handleStepOver() {
  addLog('info', '单步执行...')
  // Simulate step execution
  isPaused.value = true
}

function handleContinue() {
  isPaused.value = false
  addLog('info', '继续执行...')
}

function handleStopDebug() {
  isDebugging.value = false
  isPaused.value = false
  executionTime.value = Date.now() - startTime.value
  addLog('warning', '调试已停止')
}

function handleVariableUpdate(key: string, value: any) {
  currentVariables.value[key] = value
  addLog('info', `变量 ${key} 已更新为: ${JSON.stringify(value)}`)
}

function handleBreakpointToggle(bp: Breakpoint) {
  addLog('info', `断点 ${bp.nodeName} ${bp.enabled ? '已启用' : '已禁用'}`)
}

function removeBreakpoint(nodeId: string) {
  const index = breakpoints.value.findIndex(bp => bp.nodeId === nodeId)
  if (index > -1) {
    breakpoints.value.splice(index, 1)
  }
}

function addInputVariable() {
  ElMessageBox.prompt('请输入变量名', '添加变量', {
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    if (value) {
      inputVariables[value] = ''
    }
  }).catch(() => {})
}

function addLog(level: string, message: string, nodeId?: string, variables?: Record<string, any>) {
  executionLogs.value.push({
    timestamp: new Date().toISOString(),
    level,
    nodeId,
    nodeName: nodeId ? `Node-${nodeId}` : undefined,
    message,
    variables
  })
}
</script>

<style lang="scss" scoped>
.process-debug-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.debug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e6e6e6;
  
  h3 { margin: 0; }
  
  .debug-actions {
    display: flex;
    gap: 8px;
  }
}

.debug-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.debug-left {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  border-right: 1px solid #e6e6e6;
}

.debug-right {
  width: 300px;
  padding: 16px;
  overflow-y: auto;
}

.execution-status {
  margin-bottom: 20px;
  
  .status-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    
    .label {
      color: #909399;
      min-width: 70px;
    }
  }
}

.input-variables {
  h4 {
    margin: 0 0 12px;
    font-size: 14px;
  }
}

.breakpoint-list {
  .breakpoint-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border-bottom: 1px solid #f0f0f0;
    
    .node-name {
      flex: 1;
    }
  }
}
</style>

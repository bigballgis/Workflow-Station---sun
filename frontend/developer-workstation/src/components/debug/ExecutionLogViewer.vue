<template>
  <div class="execution-log-viewer">
    <div class="log-toolbar">
      <el-input v-model="searchText" placeholder="搜索日志..." size="small" clearable style="width: 200px;">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="levelFilter" placeholder="日志级别" size="small" clearable style="width: 120px;">
        <el-option label="全部" value="" />
        <el-option label="信息" value="info" />
        <el-option label="成功" value="success" />
        <el-option label="警告" value="warning" />
        <el-option label="错误" value="error" />
      </el-select>
      <el-checkbox v-model="autoScroll" size="small">自动滚动</el-checkbox>
      <el-button size="small" @click="handleClear">
        <el-icon><Delete /></el-icon> 清空
      </el-button>
      <el-button size="small" @click="handleExport">
        <el-icon><Download /></el-icon> 导出
      </el-button>
    </div>

    <div ref="logContainerRef" class="log-container">
      <div v-for="(log, index) in filteredLogs" :key="index" class="log-item" :class="log.level">
        <span class="log-time">{{ formatTime(log.timestamp) }}</span>
        <el-tag :type="levelTagType(log.level)" size="small" class="log-level">
          {{ levelLabel(log.level) }}
        </el-tag>
        <span v-if="log.nodeName" class="log-node">[{{ log.nodeName }}]</span>
        <span class="log-message">{{ log.message }}</span>
        <el-button v-if="log.variables" link size="small" @click="showVariables(log)">
          <el-icon><View /></el-icon>
        </el-button>
      </div>
      <el-empty v-if="!filteredLogs.length" description="暂无日志" />
    </div>

    <!-- Variables Dialog -->
    <el-dialog v-model="showVariablesDialog" title="变量快照" width="500px">
      <pre class="variables-preview">{{ JSON.stringify(selectedLogVariables, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Search, Delete, Download, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

interface ExecutionLog {
  timestamp: string
  level: string
  nodeId?: string
  nodeName?: string
  message: string
  variables?: Record<string, any>
}

const props = defineProps<{
  logs: ExecutionLog[]
}>()

const emit = defineEmits<{
  (e: 'clear'): void
}>()

const searchText = ref('')
const levelFilter = ref('')
const autoScroll = ref(true)
const logContainerRef = ref<HTMLElement>()
const showVariablesDialog = ref(false)
const selectedLogVariables = ref<Record<string, any>>({})

const filteredLogs = computed(() => {
  let result = props.logs
  
  if (levelFilter.value) {
    result = result.filter(log => log.level === levelFilter.value)
  }
  
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = result.filter(log => 
      log.message.toLowerCase().includes(search) ||
      log.nodeName?.toLowerCase().includes(search)
    )
  }
  
  return result
})

watch(() => props.logs.length, () => {
  if (autoScroll.value) {
    nextTick(() => {
      if (logContainerRef.value) {
        logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
      }
    })
  }
})

function formatTime(timestamp: string): string {
  return dayjs(timestamp).format('HH:mm:ss.SSS')
}

function levelTagType(level: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    info: 'info',
    success: 'success',
    warning: 'warning',
    error: 'danger'
  }
  return map[level] || 'info'
}

function levelLabel(level: string): string {
  const map: Record<string, string> = {
    info: '信息',
    success: '成功',
    warning: '警告',
    error: '错误'
  }
  return map[level] || level
}

function showVariables(log: ExecutionLog) {
  selectedLogVariables.value = log.variables || {}
  showVariablesDialog.value = true
}

function handleClear() {
  emit('clear')
}

function handleExport() {
  const data = props.logs.map(log => 
    `[${formatTime(log.timestamp)}] [${log.level.toUpperCase()}] ${log.nodeName ? `[${log.nodeName}] ` : ''}${log.message}`
  ).join('\n')
  
  const blob = new Blob([data], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `execution_log_${Date.now()}.txt`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}
</script>

<style lang="scss" scoped>
.execution-log-viewer {
  .log-toolbar {
    display: flex;
    gap: 10px;
    align-items: center;
    margin-bottom: 12px;
  }
  
  .log-container {
    max-height: 400px;
    overflow-y: auto;
    background: #1e1e1e;
    border-radius: 4px;
    padding: 12px;
    font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
    font-size: 12px;
  }
  
  .log-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    color: #d4d4d4;
    
    &.info { .log-message { color: #9cdcfe; } }
    &.success { .log-message { color: #4ec9b0; } }
    &.warning { .log-message { color: #dcdcaa; } }
    &.error { .log-message { color: #f48771; } }
    
    .log-time {
      color: #6a9955;
      min-width: 90px;
    }
    
    .log-level {
      min-width: 50px;
    }
    
    .log-node {
      color: #ce9178;
    }
    
    .log-message {
      flex: 1;
    }
  }
  
  .variables-preview {
    background: #f5f7fa;
    padding: 16px;
    border-radius: 4px;
    max-height: 300px;
    overflow: auto;
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 12px;
    margin: 0;
  }
}
</style>

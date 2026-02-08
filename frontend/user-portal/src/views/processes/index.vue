<template>
  <div class="processes-page">
    <div class="page-header">
      <h1>{{ t('process.title') }}</h1>
    </div>

    <el-row :gutter="20">
      <!-- 常用流程 -->
      <el-col :span="24" v-if="favoriteProcesses.length > 0">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('process.favorites') }}</span>
          </div>
          <div class="process-grid">
            <div
              v-for="process in favoriteProcesses"
              :key="process.key"
              class="process-card"
              @click="startProcess(process)"
            >
              <el-icon :size="32" color="var(--hsbc-red)"><Document /></el-icon>
              <span class="process-name">{{ process.name }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 全部流程 -->
      <el-col :span="24">
        <div class="portal-card">
          <div class="card-header">
            <span class="card-title">{{ t('process.all') }}</span>
            <el-input
              v-model="searchKeyword"
              :placeholder="t('common.search')"
              clearable
              style="width: 200px;"
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
          
          <el-skeleton :loading="loading" animated :count="3">
            <template #template>
              <div class="process-grid">
                <el-skeleton-item v-for="i in 6" :key="i" variant="rect" style="height: 120px; border-radius: 8px;" />
              </div>
            </template>
            <template #default>
              <div v-if="allProcesses.length === 0" class="empty-state">
                <el-empty :description="t('process.noAccessibleProcesses')">
                  <template #image>
                    <el-icon :size="60" color="#909399"><Lock /></el-icon>
                  </template>
                  <el-text type="info" size="small">{{ t('process.contactAdminForAccess') }}</el-text>
                </el-empty>
              </div>
              <div v-else class="process-grid">
                <div
                  v-for="process in allProcesses"
                  :key="process.key"
                  class="process-card"
                  @click="startProcess(process)"
                >
                  <el-icon :size="32" :color="getProcessColor(process.category)">
                    <component :is="getProcessIcon(process.category)" />
                  </el-icon>
                  <span class="process-name">{{ process.name }}</span>
                  <span class="process-version" v-if="process.version">v{{ process.version }}</span>
                  <span class="process-desc">{{ process.description }}</span>
                  <el-tag v-if="process.isFavorite" size="small" type="warning" class="favorite-tag">
                    <el-icon><Star /></el-icon>
                  </el-tag>
                </div>
              </div>
            </template>
          </el-skeleton>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, Search, Calendar, Money, ShoppingCart, Location, Clock, Files, Star, Tickets, Lock } from '@element-plus/icons-vue'
import { processApi, type ProcessDefinition } from '@/api/process'

const { t } = useI18n()
const router = useRouter()

const searchKeyword = ref('')
const loading = ref(false)
const allProcesses = ref<ProcessDefinition[]>([])

// 收藏的流程
const favoriteProcesses = computed(() => {
  return allProcesses.value.filter(p => p.isFavorite)
})

// 根据分类获取图标
const getProcessIcon = (category: string) => {
  const iconMap: Record<string, any> = {
    '人事': Calendar,
    'HR': Calendar,
    '财务': Money,
    'Finance': Money,
    '采购': ShoppingCart,
    'Procurement': ShoppingCart,
    '出差': Location,
    'Travel': Location,
    '加班': Clock,
    'Overtime': Clock,
    '合同': Files,
    'Contract': Files,
    '业务流程': Tickets,
    'Business Process': Tickets,
  }
  return iconMap[category] || Document
}

// 根据分类获取颜色
const getProcessColor = (category: string) => {
  const colorMap: Record<string, string> = {
    '人事': 'var(--hsbc-red)',
    'HR': 'var(--hsbc-red)',
    '财务': 'var(--success-green)',
    'Finance': 'var(--success-green)',
    '采购': 'var(--warning-orange)',
    'Procurement': 'var(--warning-orange)',
    '出差': 'var(--info-blue)',
    'Travel': 'var(--info-blue)',
    '加班': '#722ed1',
    'Overtime': '#722ed1',
    '合同': '#13c2c2',
    'Contract': '#13c2c2',
    '业务流程': '#1890ff',
    'Business Process': '#1890ff',
  }
  return colorMap[category] || 'var(--hsbc-red)'
}

// 语义化版本比较（用于防御性去重）
const compareSemanticVersions = (v1: string, v2: string): number => {
  const parts1 = v1.split('.').map(Number)
  const parts2 = v2.split('.').map(Number)
  for (let i = 0; i < 3; i++) {
    const a = parts1[i] || 0
    const b = parts2[i] || 0
    if (a !== b) return a - b
  }
  return 0
}

// 防御性去重：按 key(code) 分组，保留版本号最高的记录
const deduplicateByKey = (definitions: ProcessDefinition[]): ProcessDefinition[] => {
  const map = new Map<string, ProcessDefinition>()
  for (const def of definitions) {
    const existing = map.get(def.key)
    if (!existing || compareSemanticVersions(def.version, existing.version) > 0) {
      map.set(def.key, def)
    }
  }
  return Array.from(map.values())
}

// 加载流程定义列表
const loadProcessDefinitions = async () => {
  loading.value = true
  try {
    const response = await processApi.getDefinitions({
      keyword: searchKeyword.value || undefined
    })
    // API 返回的是 { success: true, data: [...] } 格式
    const raw = response.data || response || []
    // 防御性去重：确保每个 key 只显示一条记录
    allProcesses.value = deduplicateByKey(raw)
  } catch (error: any) {
    console.error('Failed to load process definitions:', error)
    ElMessage.error(t('task.loadFailed'))
    allProcesses.value = []
  } finally {
    loading.value = false
  }
}

// 搜索防抖
let searchTimer: ReturnType<typeof setTimeout> | null = null
const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadProcessDefinitions()
  }, 300)
}

const startProcess = (process: ProcessDefinition) => {
  router.push(`/processes/start/${process.key}`)
}

onMounted(() => {
  loadProcessDefinitions()
})
</script>

<style lang="scss" scoped>
.processes-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .process-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 16px;
  }
  
  .process-card {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 24px 16px;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    min-height: 160px;
    
    &:hover {
      border-color: var(--hsbc-red);
      box-shadow: 0 4px 12px rgba(219, 0, 17, 0.1);
    }
    
    .process-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
      text-align: center;
      word-break: break-word;
      width: 100%;
    }
    
    .process-version {
      font-size: 11px;
      color: var(--text-secondary);
      background: var(--bg-secondary, #f5f5f5);
      padding: 1px 6px;
      border-radius: 4px;
    }
    
    .process-desc {
      font-size: 12px;
      color: var(--text-secondary);
      text-align: center;
      width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      line-height: 1.5;
      max-height: 3em;
    }
    
    .favorite-tag {
      position: absolute;
      top: 8px;
      right: 8px;
    }
  }
  
  .empty-state {
    padding: 40px 0;
  }
}
</style>

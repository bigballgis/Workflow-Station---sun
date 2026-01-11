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
                <el-empty description="暂无可发起的流程" />
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
import { Document, Search, Calendar, Money, ShoppingCart, Location, Clock, Files, Star, Tickets } from '@element-plus/icons-vue'
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
    '财务': Money,
    '采购': ShoppingCart,
    '出差': Location,
    '加班': Clock,
    '合同': Files,
    '业务流程': Tickets,
  }
  return iconMap[category] || Document
}

// 根据分类获取颜色
const getProcessColor = (category: string) => {
  const colorMap: Record<string, string> = {
    '人事': 'var(--hsbc-red)',
    '财务': 'var(--success-green)',
    '采购': 'var(--warning-orange)',
    '出差': 'var(--info-blue)',
    '加班': '#722ed1',
    '合同': '#13c2c2',
    '业务流程': '#1890ff',
  }
  return colorMap[category] || 'var(--hsbc-red)'
}

// 加载流程定义列表
const loadProcessDefinitions = async () => {
  loading.value = true
  try {
    const response = await processApi.getDefinitions({
      keyword: searchKeyword.value || undefined
    })
    // API 返回的是 { success: true, data: [...] } 格式
    allProcesses.value = response.data || response || []
  } catch (error: any) {
    console.error('Failed to load process definitions:', error)
    ElMessage.error('加载流程列表失败')
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
    
    &:hover {
      border-color: var(--hsbc-red);
      box-shadow: 0 4px 12px rgba(219, 0, 17, 0.1);
    }
    
    .process-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
    }
    
    .process-desc {
      font-size: 12px;
      color: var(--text-secondary);
      text-align: center;
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

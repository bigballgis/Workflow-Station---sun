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
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
          <div class="process-grid">
            <div
              v-for="process in filteredProcesses"
              :key="process.key"
              class="process-card"
              @click="startProcess(process)"
            >
              <el-icon :size="32" :color="process.color"><component :is="process.icon" /></el-icon>
              <span class="process-name">{{ process.name }}</span>
              <span class="process-desc">{{ process.description }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Document, Search, Calendar, Money, ShoppingCart, Plane, Clock, Files } from '@element-plus/icons-vue'

const { t } = useI18n()
const router = useRouter()

const searchKeyword = ref('')

const favoriteProcesses = ref([
  { key: 'leave', name: '请假申请', icon: Calendar, color: 'var(--hsbc-red)' },
  { key: 'expense', name: '报销申请', icon: Money, color: 'var(--success-green)' }
])

const allProcesses = ref([
  { key: 'leave', name: '请假申请', description: '员工请假审批流程', icon: Calendar, color: 'var(--hsbc-red)' },
  { key: 'expense', name: '报销申请', description: '费用报销审批流程', icon: Money, color: 'var(--success-green)' },
  { key: 'purchase', name: '采购申请', description: '物资采购审批流程', icon: ShoppingCart, color: 'var(--warning-orange)' },
  { key: 'travel', name: '出差申请', description: '出差审批流程', icon: Plane, color: 'var(--info-blue)' },
  { key: 'overtime', name: '加班申请', description: '加班审批流程', icon: Clock, color: '#722ed1' },
  { key: 'contract', name: '合同审批', description: '合同签署审批流程', icon: Files, color: '#13c2c2' }
])

const filteredProcesses = computed(() => {
  if (!searchKeyword.value) return allProcesses.value
  return allProcesses.value.filter(p => 
    p.name.includes(searchKeyword.value) || p.description.includes(searchKeyword.value)
  )
})

const startProcess = (process: any) => {
  router.push(`/processes/start/${process.key}`)
}
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
  }
}
</style>

<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <el-form :inline="true">
          <el-form-item>
            <el-input v-model="searchKeyword" placeholder="搜索图标" clearable @change="loadIcons" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="selectedCategory" placeholder="选择分类" clearable @change="loadIcons">
              <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :data="uploadData"
          :show-file-list="false"
          :on-success="handleUploadSuccess"
          accept=".svg"
        >
          <el-button type="primary">{{ $t('icon.upload') }}</el-button>
        </el-upload>
      </div>

      <div class="icon-grid" v-loading="loading">
        <div v-for="icon in icons" :key="icon.id" class="icon-item" @click="handleSelectIcon(icon)">
          <div class="icon-preview" v-html="icon.svgContent"></div>
          <div class="icon-name">{{ icon.name }}</div>
        </div>
        <div v-if="!icons.length && !loading" class="no-data">
          {{ $t('common.noData') }}
        </div>
      </div>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        layout="total, prev, pager, next"
        @change="loadIcons"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>

    <!-- Icon Detail Dialog -->
    <el-dialog v-model="showDetailDialog" :title="selectedIcon?.name" width="500px">
      <div v-if="selectedIcon" class="icon-detail">
        <div class="icon-large-preview" v-html="selectedIcon.svgContent"></div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">{{ selectedIcon.name }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel(selectedIcon.category) }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ selectedIcon.fileSize }} bytes</el-descriptions-item>
          <el-descriptions-item label="描述">{{ selectedIcon.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
        <el-button type="danger" @click="handleDeleteIcon">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const icons = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const searchKeyword = ref('')
const selectedCategory = ref('')
const pagination = reactive({ page: 1, size: 24 })
const showDetailDialog = ref(false)
const selectedIcon = ref<any>(null)

const categories = ['SYSTEM', 'BUSINESS', 'ACTION', 'STATUS', 'CUSTOM']

const categoryLabel = (cat: string) => {
  const map: Record<string, string> = {
    SYSTEM: '系统',
    BUSINESS: '业务',
    ACTION: '动作',
    STATUS: '状态',
    CUSTOM: '自定义'
  }
  return map[cat] || cat
}

const uploadUrl = computed(() => '/api/v1/icons')
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`
}))
const uploadData = computed(() => ({
  category: selectedCategory.value || 'CUSTOM'
}))

async function loadIcons() {
  loading.value = true
  try {
    const res = await api.get('/icons', {
      params: {
        keyword: searchKeyword.value || undefined,
        category: selectedCategory.value || undefined,
        page: pagination.page - 1,
        size: pagination.size
      }
    })
    icons.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

function handleSelectIcon(icon: any) {
  selectedIcon.value = icon
  showDetailDialog.value = true
}

function handleUploadSuccess() {
  ElMessage.success('上传成功')
  loadIcons()
}

async function handleDeleteIcon() {
  await ElMessageBox.confirm('确定要删除该图标吗？', '提示', { type: 'warning' })
  await api.delete(`/icons/${selectedIcon.value.id}`)
  ElMessage.success('删除成功')
  showDetailDialog.value = false
  loadIcons()
}

onMounted(loadIcons)
</script>

<style lang="scss" scoped>
.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    border-color: #DB0011;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

.icon-preview {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  
  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}

.icon-name {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.no-data {
  grid-column: 1 / -1;
  text-align: center;
  color: #909399;
  padding: 40px;
}

.icon-detail {
  text-align: center;
}

.icon-large-preview {
  width: 120px;
  height: 120px;
  margin: 0 auto 20px;
  
  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}
</style>

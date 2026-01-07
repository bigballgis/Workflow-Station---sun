<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <el-form :inline="true" class="filter-form">
          <el-form-item>
            <el-input v-model="searchKeyword" placeholder="搜索图标" clearable @change="loadIcons" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="selectedCategory" placeholder="选择分类" clearable @change="loadIcons" style="width: 140px">
              <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="allTags.length" class="tag-filter-item">
            <span class="tag-label">标签:</span>
            <el-tag
              v-for="tag in allTags"
              :key="tag"
              :type="selectedTag === tag ? '' : 'info'"
              :effect="selectedTag === tag ? 'dark' : 'plain'"
              class="tag-item"
              @click="handleTagClick(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-tag v-if="selectedTag" type="danger" effect="plain" class="tag-item" @click="clearTagFilter">
              清除
            </el-tag>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="showUploadDialog = true">{{ $t('icon.upload') }}</el-button>
      </div>

      <div class="icon-grid" v-loading="loading">
        <div v-for="icon in icons" :key="icon.id" class="icon-item" @click="handleSelectIcon(icon)">
          <div class="icon-preview" v-html="sanitizeSvg(icon.svgContent)"></div>
          <div class="icon-name">{{ icon.name }}</div>
        </div>
        <div v-if="!icons.length && !loading" class="no-data">
          {{ $t('common.noData') }}
        </div>
      </div>

      <div class="pagination-wrapper">
        <el-pagination
          v-if="total > pagination.size"
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="total"
          layout="total, prev, pager, next"
          @change="loadIcons"
        />
      </div>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="showUploadDialog" title="上传图标" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="图标文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".svg"
            :on-change="handleFileChange"
          >
            <el-button>选择SVG文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="uploadForm.name" placeholder="图标名称" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="uploadForm.category">
            <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="uploadForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>

    <!-- Icon Detail Dialog -->
    <el-dialog v-model="showDetailDialog" :title="selectedIcon?.name" width="500px">
      <div v-if="selectedIcon" class="icon-detail">
        <div class="icon-large-preview" v-html="sanitizeSvg(selectedIcon.svgContent)"></div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">{{ selectedIcon.name }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel(selectedIcon.category) }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ selectedIcon.fileSize }} bytes</el-descriptions-item>
          <el-descriptions-item label="标签">{{ selectedIcon.tags || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
        <el-button type="danger" @click="handleDeleteIcon" :loading="deleting">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { iconApi, type Icon } from '@/api/icon'

const icons = ref<Icon[]>([])
const loading = ref(false)
const uploading = ref(false)
const deleting = ref(false)
const total = ref(0)
const searchKeyword = ref('')
const selectedCategory = ref('')
const selectedTag = ref('')
const allTags = ref<string[]>([])
const pagination = reactive({ page: 1, size: 100 })
const showDetailDialog = ref(false)
const showUploadDialog = ref(false)
const selectedIcon = ref<Icon | null>(null)
const uploadRef = ref()
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({
  name: '',
  category: 'GENERAL',
  tags: ''
})

const categories = ['APPROVAL', 'CREDIT', 'ACCOUNT', 'PAYMENT', 'CUSTOMER', 'COMPLIANCE', 'OPERATION', 'GENERAL']

const categoryLabel = (cat: string) => {
  const map: Record<string, string> = {
    APPROVAL: '审批流程',
    CREDIT: '信贷业务',
    ACCOUNT: '账户服务',
    PAYMENT: '支付结算',
    CUSTOMER: '客户管理',
    COMPLIANCE: '合规风控',
    OPERATION: '运营管理',
    GENERAL: '通用图标'
  }
  return map[cat] || cat
}

// 清理 SVG 内容，移除可能导致显示问题的元素
function sanitizeSvg(svg: string): string {
  if (!svg) return ''
  let result = svg
  // 移除 <title> 元素
  result = result.replace(/<title[^>]*>[\s\S]*?<\/title>/gi, '')
  // 移除 <defs> 元素（包含 <style> 定义，防止样式泄漏）
  result = result.replace(/<defs[\s\S]*?<\/defs>/gi, '')
  // 将 class="cls-1" 替换为内联样式 fill="#fff"
  result = result.replace(/class="cls-1"/gi, 'fill="#fff"')
  // 将 class="cls-2" 替换为内联样式 fill="#db0011"
  result = result.replace(/class="cls-2"/gi, 'fill="#db0011"')
  return result
}

async function loadIcons() {
  loading.value = true
  try {
    const res = await iconApi.list({
      keyword: searchKeyword.value || undefined,
      category: selectedCategory.value || undefined,
      tag: selectedTag.value || undefined,
      page: pagination.page - 1,
      size: pagination.size
    })
    icons.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTags() {
  try {
    const res = await iconApi.getTags()
    allTags.value = res.data || []
  } catch (e) {
    // ignore
  }
}

function handleTagClick(tag: string) {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  loadIcons()
}

function clearTagFilter() {
  selectedTag.value = ''
  loadIcons()
}

function handleSelectIcon(icon: Icon) {
  selectedIcon.value = icon
  showDetailDialog.value = true
}

function handleFileChange(file: UploadFile) {
  uploadFile.value = file.raw || null
  if (file.name && !uploadForm.name) {
    uploadForm.name = file.name.replace('.svg', '')
  }
}

async function handleUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  if (!uploadForm.name) {
    ElMessage.warning('请输入名称')
    return
  }
  
  uploading.value = true
  try {
    await iconApi.upload(uploadFile.value, uploadForm.name, uploadForm.category, uploadForm.tags)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    uploadFile.value = null
    Object.assign(uploadForm, { name: '', category: 'GENERAL', tags: '' })
    uploadRef.value?.clearFiles()
    loadIcons()
    loadTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleDeleteIcon() {
  if (!selectedIcon.value) return
  
  await ElMessageBox.confirm('确定要删除该图标吗？', '提示', { type: 'warning' })
  
  deleting.value = true
  try {
    // Check if icon is in use
    const usageRes = await iconApi.checkUsage(selectedIcon.value.id)
    if (usageRes.data) {
      ElMessage.warning('该图标正在被使用，无法删除')
      return
    }
    
    await iconApi.delete(selectedIcon.value.id)
    ElMessage.success('删除成功')
    showDetailDialog.value = false
    loadIcons()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  loadIcons()
  loadTags()
})
</script>

<style lang="scss" scoped>
.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 0 !important;
}

.tag-label {
  font-size: 14px;
  color: #606266;
  margin-right: 4px;
}

.tag-item {
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    transform: scale(1.05);
  }
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 12px;
  flex: 1;
  align-content: start;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 8px;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  aspect-ratio: 1;
  
  &:hover {
    border-color: #DB0011;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }
}

.icon-preview {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  
  :deep(svg) {
    max-width: 100%;
    max-height: 100%;
    width: auto;
    height: auto;
  }
}

.icon-name {
  margin-top: 6px;
  font-size: 11px;
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
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
  
  :deep(svg) {
    max-width: 100%;
    max-height: 100%;
    width: auto;
    height: auto;
  }
}

.pagination-wrapper {
  margin-top: auto;
  padding-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>

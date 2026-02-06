<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <el-form :inline="true" class="filter-form">
          <el-form-item class="no-margin">
            <el-input v-model="searchKeyword" :placeholder="t('icon.search')" clearable @change="loadIcons" />
          </el-form-item>
          <el-form-item class="no-margin">
            <el-select v-model="selectedCategory" :placeholder="t('icon.selectCategory')" clearable @change="loadIcons" style="width: 140px">
              <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="allTags.length" class="tag-filter-item no-margin">
            <span class="tag-label">{{ t('icon.tags') }}:</span>
            <el-tag
              v-for="tag in allTags"
              :key="tag"
              :type="selectedTag === tag ? 'danger' : 'info'"
              :effect="selectedTag === tag ? 'dark' : 'plain'"
              class="tag-item"
              @click="handleTagClick(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-tag v-if="selectedTag" type="danger" effect="plain" class="tag-item" @click="clearTagFilter">
              {{ t('icon.clear') }}
            </el-tag>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="showUploadDialog = true">{{ t('icon.upload') }}</el-button>
      </div>

      <div class="icon-grid" v-loading="loading">
        <div v-for="icon in icons" :key="icon.id" class="icon-item" @click="handleSelectIcon(icon)">
          <div class="icon-preview" v-html="sanitizeSvg(icon.svgContent)"></div>
          <div class="icon-name">{{ icon.name }}</div>
        </div>
        <div v-if="!icons.length && !loading" class="no-data">
          {{ t('common.noData') }}
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
    <el-dialog v-model="showUploadDialog" :title="t('icon.uploadIcon')" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item :label="t('icon.iconFile')" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".svg"
            :on-change="handleFileChange"
          >
            <el-button>{{ t('icon.selectFile') }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item :label="t('icon.name')" required>
          <el-input v-model="uploadForm.name" :placeholder="t('icon.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('icon.category')" required>
          <el-select v-model="uploadForm.category">
            <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('icon.tags')">
          <el-input v-model="uploadForm.tags" :placeholder="t('icon.tagsPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">{{ t('icon.upload') }}</el-button>
      </template>
    </el-dialog>

    <!-- Icon Detail Dialog -->
    <el-dialog v-model="showDetailDialog" :title="selectedIcon?.name" width="500px">
      <div v-if="selectedIcon" class="icon-detail">
        <div class="icon-large-preview" v-html="sanitizeSvg(selectedIcon.svgContent)"></div>
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('icon.name')">{{ selectedIcon.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('icon.category')">{{ categoryLabel(selectedIcon.category) }}</el-descriptions-item>
          <el-descriptions-item :label="t('icon.size')">{{ selectedIcon.fileSize }} bytes</el-descriptions-item>
          <el-descriptions-item :label="t('icon.tags')">{{ selectedIcon.tags || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">{{ t('common.close') }}</el-button>
        <el-button type="danger" @click="handleDeleteIcon" :loading="deleting">{{ t('common.delete') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { iconApi, type Icon } from '@/api/icon'

const { t } = useI18n()

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
    APPROVAL: t('icon.categoryApproval'),
    CREDIT: t('icon.categoryCredit'),
    ACCOUNT: t('icon.categoryAccount'),
    PAYMENT: t('icon.categoryPayment'),
    CUSTOMER: t('icon.categoryCustomer'),
    COMPLIANCE: t('icon.categoryCompliance'),
    OPERATION: t('icon.categoryOperation'),
    GENERAL: t('icon.categoryGeneral')
  }
  return map[cat] || cat
}

// Clean SVG content, remove elements that may cause display issues
function sanitizeSvg(svg: string): string {
  if (!svg) return ''
  let result = svg
  // Remove <title> elements
  result = result.replace(/<title[^>]*>[\s\S]*?<\/title>/gi, '')
  // Remove <style> elements (prevent style leakage to global)
  result = result.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
  // Remove <defs> elements (contains <style> definitions, prevent style leakage)
  result = result.replace(/<defs[\s\S]*?<\/defs>/gi, '')
  // 将 class="cls-1" 替换为内联样式 fill="#fff"
  result = result.replace(/class="cls-1"/gi, 'fill="#fff"')
  // 将 class="cls-2" 替换为内联样式 fill="#db0011"
  result = result.replace(/class="cls-2"/gi, 'fill="#db0011"')
  // 移除所有 class 属性，防止样式冲突
  result = result.replace(/\s+class="[^"]*"/gi, '')
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
    ElMessage.error(e.response?.data?.message || t('icon.loadFailed'))
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
    ElMessage.warning(t('icon.selectFileFirst'))
    return
  }
  if (!uploadForm.name) {
    ElMessage.warning(t('icon.enterName'))
    return
  }
  
  uploading.value = true
  try {
    await iconApi.upload(uploadFile.value, uploadForm.name, uploadForm.category, uploadForm.tags)
    ElMessage.success(t('icon.uploadSuccess'))
    showUploadDialog.value = false
    uploadFile.value = null
    Object.assign(uploadForm, { name: '', category: 'GENERAL', tags: '' })
    uploadRef.value?.clearFiles()
    loadIcons()
    loadTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('icon.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

async function handleDeleteIcon() {
  if (!selectedIcon.value) return
  
  await ElMessageBox.confirm(t('common.confirm'), t('common.confirm'), { type: 'warning' })
  
  deleting.value = true
  try {
    // Check if icon is in use
    const usageRes = await iconApi.checkUsage(selectedIcon.value.id)
    if (usageRes.data) {
      ElMessage.warning(t('common.error'))
      return
    }
    
    await iconApi.delete(selectedIcon.value.id)
    ElMessage.success(t('common.success'))
    showDetailDialog.value = false
    loadIcons()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.error'))
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
.no-margin {
  margin-bottom: 0 !important;
}

.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  
  :deep(.el-input__inner) {
    font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif !important;
  }
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
  min-height: 200px;
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

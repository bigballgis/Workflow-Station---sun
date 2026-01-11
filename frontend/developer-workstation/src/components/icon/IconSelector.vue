<template>
  <el-dialog 
    v-model="dialogVisible" 
    :title="$t('icon.selectIcon')" 
    width="700px"
    @close="handleClose"
  >
    <div class="icon-selector">
      <!-- Search and Filter -->
      <div class="icon-selector__header">
        <el-input 
          v-model="searchKeyword" 
          :placeholder="$t('icon.searchPlaceholder')" 
          clearable 
          @input="handleSearch"
          style="width: 200px;"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select 
          v-model="selectedCategory" 
          :placeholder="$t('icon.category')" 
          clearable 
          @change="loadIcons"
          style="width: 120px;"
        >
          <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
        </el-select>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          {{ $t('icon.upload') }}
        </el-button>
      </div>

      <!-- Icon Grid -->
      <div class="icon-selector__grid" v-loading="loading">
        <div 
          v-for="icon in icons" 
          :key="icon.id" 
          class="icon-item"
          :class="{ 'icon-item--selected': selectedIconId === icon.id }"
          @click="handleSelectIcon(icon)"
          :title="icon.name"
        >
          <div class="icon-item__preview" v-html="sanitizeSvg(icon.svgContent)"></div>
          <div class="icon-item__name">{{ icon.name }}</div>
        </div>
        <div v-if="!icons.length && !loading" class="icon-selector__empty">
          {{ $t('common.noData') }}
        </div>
      </div>

      <!-- Pagination -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="total"
        layout="total, prev, pager, next"
        @change="loadIcons"
        style="margin-top: 16px; justify-content: center;"
      />
    </div>

    <template #footer>
      <el-button @click="handleClear">{{ $t('icon.clear') }}</el-button>
      <el-button @click="handleClose">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleConfirm">{{ $t('common.confirm') }}</el-button>
    </template>

    <!-- Upload Dialog -->
    <el-dialog v-model="showUploadDialog" :title="$t('icon.upload')" width="500px" append-to-body>
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item :label="$t('icon.file')" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".svg"
            :on-change="handleFileChange"
          >
            <el-button>{{ $t('icon.selectFile') }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item :label="$t('icon.name')" required>
          <el-input v-model="uploadForm.name" :placeholder="$t('icon.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('icon.category')" required>
          <el-select v-model="uploadForm.category" style="width: 100%;">
            <el-option v-for="cat in categories" :key="cat" :label="categoryLabel(cat)" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('icon.tags')">
          <el-input v-model="uploadForm.tags" :placeholder="$t('icon.tagsPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">{{ $t('icon.upload') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import { iconApi, type Icon } from '@/api/icon'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  modelValue?: number | null
  visible: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [iconId: number | null]
  'update:visible': [visible: boolean]
  'select': [icon: Icon | null]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const icons = ref<Icon[]>([])
const loading = ref(false)
const uploading = ref(false)
const total = ref(0)
const searchKeyword = ref('')
const selectedCategory = ref('')
const selectedIconId = ref<number | null>(null)
const pagination = reactive({ page: 1, size: 20 })
const showUploadDialog = ref(false)
const uploadRef = ref()
const uploadFile = ref<File | null>(null)
const uploadForm = reactive({
  name: '',
  category: 'GENERAL',
  tags: ''
})

const categories = ['APPROVAL', 'CREDIT', 'ACCOUNT', 'PAYMENT', 'CUSTOMER', 'COMPLIANCE', 'OPERATION', 'GENERAL']

// 清理 SVG 内容，移除可能导致显示问题的元素
function sanitizeSvg(svg: string): string {
  if (!svg) return ''
  let result = svg
  // 移除 <title> 元素
  result = result.replace(/<title[^>]*>[\s\S]*?<\/title>/gi, '')
  // 移除 <style> 元素（防止样式泄漏到全局）
  result = result.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
  // 移除 <defs> 元素（包含 <style> 定义，防止样式泄漏）
  result = result.replace(/<defs[\s\S]*?<\/defs>/gi, '')
  // 移除所有 class 属性，防止样式冲突
  result = result.replace(/\s+class="[^"]*"/gi, '')
  return result
}

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

let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.page = 1
    loadIcons()
  }, 300)
}

async function loadIcons() {
  loading.value = true
  try {
    const res = await iconApi.list({
      keyword: searchKeyword.value || undefined,
      category: selectedCategory.value || undefined,
      page: pagination.page - 1,
      size: pagination.size
    })
    icons.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleSelectIcon(icon: Icon) {
  selectedIconId.value = icon.id
}

function handleClear() {
  selectedIconId.value = null
  emit('update:modelValue', null)
  emit('select', null)
  dialogVisible.value = false
}

function handleClose() {
  dialogVisible.value = false
}

function handleConfirm() {
  emit('update:modelValue', selectedIconId.value)
  const selectedIcon = icons.value.find(i => i.id === selectedIconId.value) || null
  emit('select', selectedIcon)
  dialogVisible.value = false
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
    const res = await iconApi.upload(uploadFile.value, uploadForm.name, uploadForm.category, uploadForm.tags)
    ElMessage.success(t('icon.uploadSuccess'))
    showUploadDialog.value = false
    uploadFile.value = null
    Object.assign(uploadForm, { name: '', category: 'GENERAL', tags: '' })
    uploadRef.value?.clearFiles()
    
    // Auto-select the newly uploaded icon
    if (res.data) {
      selectedIconId.value = res.data.id
    }
    loadIcons()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('icon.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    selectedIconId.value = props.modelValue || null
    loadIcons()
  }
})
</script>

<style lang="scss" scoped>
.icon-selector {
  &__header {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }
  
  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
    gap: 12px;
    min-height: 200px;
    max-height: 400px;
    overflow-y: auto;
    padding: 4px;
  }
  
  &__empty {
    grid-column: 1 / -1;
    text-align: center;
    color: #909399;
    padding: 40px;
  }
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: #f5f7fa;
  
  &:hover {
    border-color: #c0c4cc;
  }
  
  &--selected {
    border-color: #DB0011;
    background-color: #fff5f5;
  }
  
  &__preview {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    
    :deep(svg) {
      width: 100%;
      height: 100%;
    }
  }
  
  &__name {
    margin-top: 6px;
    font-size: 11px;
    color: #606266;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
}
</style>

<template>
  <div class="page-container">
    <div class="card">
      <!-- Filter Panel -->
      <div class="filter-panel">
        <div class="filter-left">
          <el-input 
            v-model="searchForm.name" 
            :placeholder="$t('functionUnit.name')" 
            clearable 
            style="width: 200px;"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select 
            v-model="searchForm.status" 
            :placeholder="$t('functionUnit.status')" 
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option :label="$t('functionUnit.draft')" value="DRAFT" />
            <el-option :label="$t('functionUnit.published')" value="PUBLISHED" />
            <el-option :label="$t('functionUnit.archived')" value="ARCHIVED" />
          </el-select>
          <el-select
            v-model="searchForm.tags"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :placeholder="$t('functionUnit.filterByTags')"
            style="width: 200px;"
            @change="handleSearch"
          >
            <el-option 
              v-for="tag in availableTags" 
              :key="tag" 
              :label="tag" 
              :value="tag" 
            />
          </el-select>
          <el-button @click="handleSearch">{{ $t('common.search') }}</el-button>
          <span class="result-count" v-if="filteredList.length !== store.list.length">
            {{ $t('functionUnit.showingResults', { count: filteredList.length, total: store.list.length }) }}
          </span>
        </div>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          {{ $t('functionUnit.create') }}
        </el-button>
      </div>

      <!-- Loading Skeleton -->
      <div v-if="store.loading" class="function-unit-grid">
        <div v-for="i in 6" :key="i" class="skeleton-card">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" style="height: 120px;" />
              <div style="padding: 16px;">
                <el-skeleton-item variant="h3" style="width: 60%;" />
                <el-skeleton-item variant="text" style="margin-top: 8px;" />
                <el-skeleton-item variant="text" style="width: 80%; margin-top: 4px;" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else-if="store.list.length === 0" class="empty-state">
        <el-empty :description="$t('functionUnit.noData')">
          <el-button type="primary" @click="showCreateDialog = true">
            {{ $t('functionUnit.create') }}
          </el-button>
        </el-empty>
      </div>

      <!-- No Results State -->
      <div v-else-if="filteredList.length === 0" class="empty-state">
        <el-empty :description="$t('functionUnit.noResults')">
          <el-button @click="clearFilters">{{ $t('functionUnit.clearFilters') }}</el-button>
        </el-empty>
      </div>

      <!-- Grid Layout -->
      <div v-else class="function-unit-grid">
        <FunctionUnitCard
          v-for="item in filteredList"
          :key="item.id"
          :item="item"
          :tags="getItemTags(item.id)"
          @click="handleEdit"
          @edit="handleEdit"
          @publish="handlePublish"
          @clone="handleClone"
          @delete="handleDelete"
        />
      </div>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-if="!store.loading && store.total > pagination.size"
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="store.total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" :title="$t('functionUnit.create')" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="formRules" label-width="80px">
        <el-form-item :label="$t('functionUnit.icon')">
          <div class="icon-select-wrapper" @click="showIconSelector = true">
            <IconPreview 
              :icon-id="createForm.iconId" 
              :svg-content="selectedIcon?.svgContent"
              size="medium" 
              clickable 
            />
            <span class="icon-select-hint">{{ createForm.iconId ? $t('icon.clickToChange') : $t('icon.clickToSelect') }}</span>
          </div>
        </el-form-item>
        <el-form-item :label="$t('functionUnit.name')" prop="name">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item :label="$t('functionUnit.description')" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="$t('functionUnit.tags')">
          <el-select
            v-model="createForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="$t('functionUnit.selectTags')"
            style="width: 100%;"
          >
            <el-option 
              v-for="tag in availableTags" 
              :key="tag" 
              :label="tag" 
              :value="tag" 
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Icon Selector -->
    <IconSelector 
      v-model="createForm.iconId" 
      :visible="showIconSelector" 
      @update:visible="showIconSelector = $event"
      @select="selectedIcon = $event"
    />
  </div>
</template>


<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { FunctionUnit, FunctionUnitResponse } from '@/api/functionUnit'
import IconPreview from '@/components/icon/IconPreview.vue'
import IconSelector from '@/components/icon/IconSelector.vue'
import FunctionUnitCard from '@/components/function-unit/FunctionUnitCard.vue'
import { getTags, setTags, getAllAvailableTags, matchesTags } from '@/utils/tagStorage'
import { isAuthenticated } from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const store = useFunctionUnitStore()

const searchForm = reactive({ name: '', status: '', tags: [] as string[] })
const pagination = reactive({ page: 1, size: 20 })
const showCreateDialog = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ 
  name: '', 
  description: '', 
  iconId: null as number | null,
  tags: [] as string[]
})
const showIconSelector = ref(false)
const selectedIcon = ref<any>(null)

const formRules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }]
}))

// Get all available tags for filter dropdown
const availableTags = computed(() => getAllAvailableTags())

// Get tags for a specific item
function getItemTags(id: number): string[] {
  return getTags(id)
}

// Filter list based on search criteria
const filteredList = computed(() => {
  return store.list.filter(item => {
    // Filter by name
    if (searchForm.name && !item.name.toLowerCase().includes(searchForm.name.toLowerCase())) {
      return false
    }
    // Filter by status
    if (searchForm.status && item.status !== searchForm.status) {
      return false
    }
    // Filter by tags
    if (searchForm.tags.length > 0) {
      const itemTags = getTags(item.id)
      if (!matchesTags(itemTags, searchForm.tags)) {
        return false
      }
    }
    return true
  })
})

function loadData() {
  store.fetchList({ page: pagination.page - 1, size: pagination.size })
}

function handleSearch() {
  pagination.page = 1
  // Client-side filtering, no need to reload
}

function clearFilters() {
  searchForm.name = ''
  searchForm.status = ''
  searchForm.tags = []
}

function handleEdit(item: FunctionUnitResponse) {
  router.push(`/function-units/${item.id}`)
}

async function handleCreate() {
  await createFormRef.value?.validate()
  const result = await store.create({
    name: createForm.name,
    description: createForm.description,
    iconId: createForm.iconId
  })
  // Save tags for the new function unit
  if (result && createForm.tags.length > 0) {
    setTags(result.id, createForm.tags)
  }
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  createForm.name = ''
  createForm.description = ''
  createForm.iconId = null
  createForm.tags = []
  selectedIcon.value = null
  loadData()
}

async function handlePublish(item: FunctionUnitResponse) {
  const { value } = await ElMessageBox.prompt('请输入变更日志', '发布功能单元', { inputType: 'textarea' })
  await store.publish(item.id, value)
  ElMessage.success('发布成功')
  loadData()
}

async function handleClone(item: FunctionUnitResponse) {
  const { value } = await ElMessageBox.prompt('请输入新名称', '克隆功能单元')
  await store.clone(item.id, value)
  ElMessage.success('克隆成功')
  loadData()
}

async function handleDelete(item: FunctionUnitResponse) {
  await ElMessageBox.confirm('确定要删除该功能单元吗？', '提示', { type: 'warning' })
  await store.remove(item.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  // 检查是否已登录
  if (isAuthenticated()) {
    loadData()
  } else {
    // 未登录，路由守卫应该已经重定向，但以防万一
    router.push('/login')
  }
})
</script>


<style lang="scss" scoped>
.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.result-count {
  font-size: 13px;
  color: #909399;
}

.function-unit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.skeleton-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.empty-state {
  padding: 60px 0;
}

.icon-select-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  
  &:hover .icon-select-hint {
    color: #DB0011;
  }
}

.icon-select-hint {
  font-size: 12px;
  color: #909399;
  transition: color 0.2s;
}

.pagination-wrapper {
  margin-top: auto;
  padding-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

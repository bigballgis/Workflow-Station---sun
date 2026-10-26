<template>
  <div class="processes-page">
    <div class="page-header">
      <div class="page-heading">
        <h1>{{ t('process.title') }}</h1>
        <PortalHelpLink
          path="/up-new"
          v-bind="{ ariaLabel: t('process.guideLinkAria') }"
          test-id="new-request-guide-link"
        />
      </div>
      <el-input
        v-model="searchKeyword"
        class="process-search"
        :placeholder="t('process.searchPlaceholder')"
        :aria-label="t('process.searchPlaceholder')"
        clearable
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <section
      v-if="favoriteProcesses.length > 0"
      class="favorites-section"
      aria-labelledby="favorites-heading"
    >
      <h2 id="favorites-heading">
        {{ t('process.favorites') }}
      </h2>
      <div class="process-grid">
        <ProcessCatalogCard
          v-for="process in favoriteProcesses"
          :key="process.key"
          :process="process"
          :favorite-pending="pendingFavoriteKeys.has(process.key)"
          @toggle-favorite="toggleFavorite"
        />
      </div>
    </section>

    <section :aria-label="t('process.title')">
      <div
        v-if="loading && allProcesses.length === 0"
        class="process-list-loading"
        role="status"
      >
        <el-icon
          class="is-loading"
          :size="28"
        >
          <Loading />
        </el-icon>
        <span>{{ t('common.loading') }}</span>
      </div>
      <div
        v-else-if="allProcesses.length === 0"
        class="empty-state"
      >
        <el-empty :description="searchKeyword ? t('process.noSearchResults') : t('process.noAccessibleProcesses')">
          <template #image>
            <el-icon
              :size="60"
              color="var(--ws-text-muted)"
            >
              <Lock />
            </el-icon>
          </template>
          <el-text
            v-if="!searchKeyword"
            type="info"
            size="small"
          >
            {{ t('process.contactAdminForAccess') }}
          </el-text>
        </el-empty>
      </div>
      <div
        v-else
        ref="mainProcessGrid"
        class="process-grid"
      >
        <ProcessCatalogCard
          v-for="process in allProcesses"
          :key="process.key"
          :process="process"
          :favorite-pending="pendingFavoriteKeys.has(process.key)"
          @toggle-favorite="toggleFavorite"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Lock, Loading } from '@element-plus/icons-vue'
import { processApi, type ProcessDefinition } from '@/api/process'
import PortalHelpLink from '@/components/PortalHelpLink.vue'
import ProcessCatalogCard from '@/components/ProcessCatalogCard.vue'

const { t } = useI18n()
const searchKeyword = ref('')
const loading = ref(true)
const allProcesses = ref<ProcessDefinition[]>([])
const favoriteProcesses = computed(() => allProcesses.value.filter(process => process.isFavorite))

const pendingFavoriteKeys = ref(new Set<string>())
const mainProcessGrid = ref<HTMLElement | null>(null)
let favoriteRevision = 0
const confirmedFavorites = new Map<string, { revision: number; value: boolean }>()

const toggleFavorite = async (key: string, event: MouseEvent) => {
  if (pendingFavoriteKeys.value.has(key)) return
  const trigger = event.currentTarget as HTMLButtonElement
  const hadFocus = document.activeElement === trigger
  pendingFavoriteKeys.value.add(key)
  try {
    const response = await processApi.toggleFavorite(key)
    if (typeof response.data !== 'boolean') {
      throw new Error('Invalid favorite response')
    }
    confirmedFavorites.set(key, { revision: ++favoriteRevision, value: response.data })
    // Resolve the current list item: search can replace the list while this request is pending.
    const process = allProcesses.value.find(item => item.key === key)
    if (process) process.isFavorite = response.data
  } catch {
    ElMessage.error(t('process.favoriteFailed'))
  } finally {
    pendingFavoriteKeys.value.delete(key)
    await nextTick()
    // Restore focus only if the user has not moved to another control while saving.
    if (hadFocus && (document.activeElement === document.body || document.activeElement === trigger)) {
      if (trigger.isConnected) {
        trigger.focus()
      } else if (mainProcessGrid.value) {
        const buttons = mainProcessGrid.value.querySelectorAll<HTMLButtonElement>('.favorite-button')
        Array.from(buttons).find(button => button.dataset.processKey === key)?.focus()
      }
    }
  }
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
  const requestedFavoriteRevision = favoriteRevision
  loading.value = true
  try {
    const response = await processApi.getDefinitions({
      keyword: searchKeyword.value || undefined
    })
    // API 返回的是 { success: true, data: [...] } 格式
    const raw = response.data || response || []
    // 防御性去重：确保每个 key 只显示一条记录
    const definitions = deduplicateByKey(raw)
    // An older list response must not overwrite a favorite saved after the request started.
    for (const process of definitions) {
      const confirmed = confirmedFavorites.get(process.key)
      if (confirmed && confirmed.revision > requestedFavoriteRevision) {
        process.isFavorite = confirmed.value
      }
    }
    allProcesses.value = definitions
  } catch (error) {
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

onMounted(() => {
  loadProcessDefinitions()
})
</script>

<style lang="scss" scoped>
.processes-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
    margin-bottom: 24px;
  }

  .page-heading {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 600;
    color: var(--ws-text);
  }

  h2 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
    color: var(--ws-text);
  }

  .process-search { width: 300px; max-width: 100%; }
  .favorites-section { margin-bottom: 28px; }

  .process-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(240px, 100%), 1fr));
    gap: 20px;
  }

  .process-list-loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 240px;
    color: var(--ws-text-secondary);
    font-size: 14px;
  }

  .empty-state { padding: 40px 0; }

  @media (max-width: 600px) {
    .page-header { align-items: flex-start; flex-direction: column; }
    .process-search { width: 100%; }
    .process-grid { gap: 16px; }
  }
}
</style>

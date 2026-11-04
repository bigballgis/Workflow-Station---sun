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

    <div
      class="catalog-tabs"
      role="tablist"
      :aria-label="t('process.title')"
    >
      <button
        v-for="(tab, index) in catalogTabs"
        :id="`request-tab-${tab.name}`"
        :key="tab.name"
        type="button"
        role="tab"
        class="catalog-tab"
        :aria-selected="activeTab === tab.name"
        :tabindex="activeTab === tab.name ? 0 : -1"
        aria-controls="request-catalog-panel"
        @click="activeTab = tab.name"
        @keydown="handleTabKeydown($event, index)"
      >
        <el-icon
          v-if="tab.name === 'favorites'"
          aria-hidden="true"
        >
          <StarFilled />
        </el-icon>
        <span>{{ tab.label }}</span>
        <span class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <section
      id="request-catalog-panel"
      ref="catalogPanel"
      role="tabpanel"
      :aria-labelledby="`request-tab-${activeTab}`"
    >
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
        v-else-if="displayedProcesses.length === 0"
        class="empty-state"
      >
        <el-empty :description="emptyDescription">
          <template #image>
            <el-icon
              :size="60"
              color="var(--ws-text-muted)"
            >
              <component :is="searchKeyword ? Search : activeTab === 'favorites' ? Star : Lock" />
            </el-icon>
          </template>
          <el-text
            v-if="!searchKeyword && activeTab === 'all'"
            type="info"
            size="small"
          >
            {{ t('process.contactAdminForAccess') }}
          </el-text>
          <template v-if="activeTab === 'favorites'">
            <p
              v-if="!searchKeyword"
              class="empty-hint"
            >
              {{ t('process.favoritesHint') }}
            </p>
            <el-button
              class="view-all-button"
              @click="showAllRequests"
            >
              {{ t('process.viewAllRequests') }}
            </el-button>
          </template>
        </el-empty>
      </div>
      <div
        v-else
        class="process-grid"
      >
        <ProcessCatalogCard
          v-for="process in displayedProcesses"
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
import { Search, Lock, Loading, Star, StarFilled } from '@element-plus/icons-vue'
import { processApi, type ProcessDefinition } from '@/api/process'
import PortalHelpLink from '@/components/PortalHelpLink.vue'
import ProcessCatalogCard from '@/components/ProcessCatalogCard.vue'

const { t } = useI18n()
const searchKeyword = ref('')
const loading = ref(true)
const allProcesses = ref<ProcessDefinition[]>([])
const favoriteProcesses = computed(() => allProcesses.value.filter(process => process.isFavorite))
const activeTab = ref<'all' | 'favorites'>('all')
const displayedProcesses = computed(() => activeTab.value === 'all' ? allProcesses.value : favoriteProcesses.value)
const catalogTabs = computed(() => [
  { name: 'all' as const, label: t('process.allRequests'), count: allProcesses.value.length },
  { name: 'favorites' as const, label: t('process.favorites'), count: favoriteProcesses.value.length },
])
const emptyDescription = computed(() => {
  if (searchKeyword.value) return t('process.noSearchResults')
  return t(activeTab.value === 'favorites' ? 'process.noFavorites' : 'process.noAccessibleProcesses')
})
const handleTabKeydown = (event: KeyboardEvent, index: number): void => {
  const keys = ['ArrowLeft', 'ArrowRight', 'Home', 'End']
  if (!keys.includes(event.key)) return
  event.preventDefault()
  const last = catalogTabs.value.length - 1
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? last
    : (index + (event.key === 'ArrowRight' ? 1 : last)) % catalogTabs.value.length
  activeTab.value = catalogTabs.value[next].name
  const tabs = (event.currentTarget as HTMLButtonElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
  tabs?.[next]?.focus()
}

const pendingFavoriteKeys = ref(new Set<string>())
const catalogPanel = ref<HTMLElement | null>(null)
const showAllRequests = async (): Promise<void> => {
  activeTab.value = 'all'
  await nextTick()
  document.getElementById('request-tab-all')?.focus()
}
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
      } else if (catalogPanel.value) {
        catalogPanel.value.querySelector<HTMLButtonElement>('.favorite-button, .view-all-button')?.focus()
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

  .process-search { width: 300px; max-width: 100%; }

  .catalog-tabs {
    display: flex;
    gap: 28px;
    margin-bottom: 24px;
    border-bottom: 1px solid var(--ws-card-border);
  }

  .catalog-tab {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 48px;
    padding: 0 2px;
    border: 0;
    border-bottom: 3px solid transparent;
    background: transparent;
    color: var(--ws-text-secondary);
    font: inherit;
    cursor: pointer;

    &[aria-selected='true'] {
      border-bottom-color: var(--hsbc-red);
      color: var(--hsbc-red);
      font-weight: 600;
    }

    &:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 3px; }
  }

  .tab-count {
    min-width: 24px;
    padding: 1px 6px;
    border-radius: 4px;
    background: var(--ws-card-bg);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

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
  .empty-hint { margin: 0 0 16px; color: var(--ws-text-secondary); }

  @media (max-width: 600px) {
    .page-header { align-items: flex-start; flex-direction: column; }
    .process-search { width: 100%; }
    .catalog-tabs { gap: 16px; }
    .catalog-tab { gap: 6px; }
    .process-grid { gap: 16px; }
  }
}
</style>

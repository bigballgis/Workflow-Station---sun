<template>
  <div class="page-container">
    <div class="card">
      <!-- Filter Panel -->
      <div class="filter-panel">
        <div class="filter-left">
          <el-input 
            v-model="searchForm.name" 
            :placeholder="t('functionUnit.name')" 
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
            :placeholder="t('functionUnit.status')" 
            clearable
            style="width: 120px;"
            @change="handleSearch"
          >
            <el-option
              :label="t('functionUnit.draft')"
              value="DRAFT"
            />
            <el-option
              :label="t('functionUnit.published')"
              value="PUBLISHED"
            />
            <el-option
              :label="t('functionUnit.archived')"
              value="ARCHIVED"
            />
          </el-select>
          <el-select
            v-model="searchForm.tags"
            multiple
            collapse-tags
            collapse-tags-tooltip
            :placeholder="t('functionUnit.filterByTags')"
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
          <el-button @click="handleSearch">
            {{ t('common.search') }}
          </el-button>
          <span
            v-if="filteredList.length !== store.list.length"
            class="result-count"
          >
            {{ t('functionUnit.showingResults', { count: filteredList.length, total: store.list.length }) }}
          </span>
          <span
            v-if="hiddenByStatusCount > 0"
            class="result-count result-count--hint"
          >
            {{ t('functionUnit.hiddenByStatusFilter', { count: hiddenByStatusCount }) }}
          </span>
        </div>
        <div class="filter-actions">
          <!-- Icon / card view toggle: the whole switch is the click target -->
          <el-switch
            v-model="isCardView"
            class="view-switch"
            inline-prompt
            size="large"
            :active-icon="Postcard"
            :inactive-icon="Grid"
            :title="isCardView ? t('functionUnit.viewIcon') : t('functionUnit.viewCard')"
          />
          <el-button
            v-if="permissions.canCreate()"
            @click="showImportDialog = true"
          >
            <el-icon><Upload /></el-icon>
            {{ t('common.import') }}
          </el-button>
          <el-button
            :disabled="store.list.length === 0"
            :loading="exporting"
            @click="openExportDialog"
          >
            <el-icon><Download /></el-icon>
            {{ t('common.export') }}
          </el-button>
          <el-button
            v-if="permissions.canCreate()"
            type="primary"
            @click="openCreateDialog"
          >
            <el-icon><Plus /></el-icon>
            {{ t('functionUnit.create') }}
          </el-button>
        </div>
      </div>

      <!-- Loading Skeleton -->
      <div
        v-if="store.loading"
        class="function-unit-grid"
      >
        <div
          v-for="i in 6"
          :key="i"
          class="skeleton-card"
        >
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item
                variant="image"
                style="height: 120px;"
              />
              <div style="padding: 16px;">
                <el-skeleton-item
                  variant="h3"
                  style="width: 60%;"
                />
                <el-skeleton-item
                  variant="text"
                  style="margin-top: 8px;"
                />
                <el-skeleton-item
                  variant="text"
                  style="width: 80%; margin-top: 4px;"
                />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <!-- Load Error State (do not masquerade a failed load as "no data") -->
      <div
        v-else-if="store.loadError"
        class="empty-state"
      >
        <el-empty :description="t('functionUnit.loadFailed')">
          <el-button
            type="primary"
            @click="loadData"
          >
            {{ t('common.refresh') }}
          </el-button>
        </el-empty>
      </div>

      <!-- Empty State -->
      <div
        v-else-if="store.list.length === 0"
        class="empty-state"
      >
        <el-empty :description="t('functionUnit.noData')">
          <el-button
            v-if="permissions.canCreate()"
            type="primary"
            @click="openCreateDialog"
          >
            {{ t('functionUnit.create') }}
          </el-button>
        </el-empty>
      </div>

      <!-- No Results State -->
      <div
        v-else-if="filteredList.length === 0"
        class="empty-state"
      >
        <el-empty :description="t('functionUnit.noResults')">
          <el-button @click="clearFilters">
            {{ t('functionUnit.clearFilters') }}
          </el-button>
        </el-empty>
      </div>

      <!-- Launchpad Grid: icon and card views; drop on either side to reorder, drop on the center to merge into a folder -->
      <div
        v-else
        class="launchpad-grid"
        :class="`launchpad-grid--${viewMode}`"
        @dragover.prevent
        @drop.prevent="onDropToEnd"
      >
        <div
          v-for="entry in visibleEntries"
          :key="entryKey(entry)"
          class="launchpad-cell"
          :class="cellClasses(entryKey(entry))"
          draggable="true"
          @dragstart="onDragStart(entry)"
          @dragend="onDragEnd"
          @dragover.prevent.stop="onDragOver(entry, $event)"
          @dragleave="onDragLeave(entry)"
          @drop.prevent.stop="onDrop(entry)"
        >
          <template v-if="viewMode === 'icon'">
            <LaunchpadTile
              v-if="entry.type === 'item' && itemOf(entry.id)"
              :item="itemOf(entry.id)!"
              @open="handleEdit"
              @settings="handleSettings"
              @clone="handleClone"
              @restore="handleRestore"
              @delete="handleDelete"
            />
            <LaunchpadFolderTile
              v-else-if="entry.type === 'folder'"
              :folder="entry"
              :items="folderItems(entry)"
              @open="openFolderId = entry.id"
            />
          </template>
          <template v-else>
            <FunctionUnitCard
              v-if="entry.type === 'item' && itemOf(entry.id)"
              :item="itemOf(entry.id)!"
              :tags="getItemTags(itemOf(entry.id)!)"
              @click="handleEdit"
              @edit="handleEdit"
              @settings="handleSettings"
              @clone="handleClone"
              @restore="handleRestore"
              @delete="handleDelete"
            />
            <LaunchpadFolderCard
              v-else-if="entry.type === 'folder'"
              :folder="entry"
              :items="folderItems(entry)"
              @open="openFolderId = entry.id"
            />
          </template>
        </div>
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

    <!-- Create / Settings Dialog -->
    <el-dialog
      v-model="showFormDialog"
      :title="formDialogTitle"
      width="500px"
      @closed="handleFormDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="basicForm"
        :rules="formRules"
        label-width="auto"
        label-position="left"
      >
        <el-form-item :label="t('functionUnit.icon')">
          <IconUploadField
            v-model="basicForm.iconId"
            size="medium"
          />
        </el-form-item>
        <el-form-item
          :label="t('functionUnit.name')"
          prop="name"
          required
        >
          <el-input
            v-model="basicForm.name"
            :placeholder="t('functionUnit.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item
          :label="t('functionUnit.description')"
          prop="description"
        >
          <el-input
            v-model="basicForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('functionUnit.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('functionUnit.tags')">
          <el-select
            v-model="basicForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('functionUnit.selectTags')"
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
        <el-form-item
          v-if="showTeamSelector"
          :label="t('functionUnit.team')"
          prop="teamGroupIds"
        >
          <el-select
            v-if="teamEditable"
            v-model="basicForm.teamGroupIds"
            multiple
            filterable
            :loading="teamsLoading"
            :placeholder="t('functionUnit.selectTeam')"
            style="width: 100%;"
          >
            <el-option
              v-for="group in teamOptions"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
          <el-input
            v-else
            :model-value="formDialogMode === 'create' ? activeGroupName : currentTeamNames"
            readonly
            :placeholder="t('devGroup.noTeam')"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="formSubmitting"
          @click="handleFormSubmit"
        >
          {{ formDialogMode === 'create' ? t('common.confirm') : t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Expanded folder overlay (iOS-style folder) -->
    <LaunchpadFolderOverlay
      :folder="openedFolder"
      :items="openedFolderItems"
      @close="openFolderId = null"
      @open-item="handleEdit"
      @settings="handleSettings"
      @clone="handleClone"
      @restore="handleRestore"
      @delete="handleDelete"
      @remove="(fid: string, item: FunctionUnitResponse) => removeFromFolder(fid, item.id)"
      @rename="renameFolder"
      @reorder="reorderInFolder"
    />

    <!-- Import Dialog -->
    <FunctionUnitImportDialog
      v-model="showImportDialog"
      @imported="loadData"
    />

    <!-- Export Dialog -->
    <el-dialog
      v-model="showExportDialog"
      :title="t('functionUnit.exportSelectTitle')"
      width="480px"
      @open="initExportSelection"
    >
      <el-form
        label-width="auto"
        label-position="left"
      >
        <el-form-item :label="t('functionUnit.name')">
          <el-select
            v-model="exportTargetId"
            filterable
            :placeholder="t('functionUnit.exportSelectPlaceholder')"
            style="width: 100%;"
          >
            <el-option
              v-for="item in store.list"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExportDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="exporting"
          :disabled="exportTargetId == null"
          @click="handleExport"
        >
          {{ t('common.export') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>


<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Upload, Download, Grid, Postcard } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { type FunctionUnitResponse } from '@/api/functionUnit'
import IconUploadField from '@/components/icon/IconUploadField.vue'
import FunctionUnitCard from '@/components/function-unit/FunctionUnitCard.vue'
import LaunchpadTile from '@/components/function-unit/LaunchpadTile.vue'
import LaunchpadFolderTile from '@/components/function-unit/LaunchpadFolderTile.vue'
import LaunchpadFolderCard from '@/components/function-unit/LaunchpadFolderCard.vue'
import LaunchpadFolderOverlay from '@/components/function-unit/LaunchpadFolderOverlay.vue'
import FunctionUnitImportDialog from '@/components/function-unit/FunctionUnitImportDialog.vue'
import { isAuthenticated } from '@/api/auth'
import { permissions } from '@/utils/permission'
import { redirectToUnifiedLogin } from '@/utils/sso'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { useFunctionUnitFilters } from '@/composables/functionUnitList/useFunctionUnitFilters'
import {
  useLaunchpadLayout,
  keyOf as entryKey,
  type LaunchpadFolderEntry,
} from '@/composables/functionUnitList/useLaunchpadLayout'
import { useFunctionUnitForm } from '@/composables/functionUnitList/useFunctionUnitForm'
import { useFunctionUnitExport } from '@/composables/functionUnitList/useFunctionUnitExport'
import { useRecentFunctionUnits } from '@/composables/useRecentFunctionUnits'

const { t } = useI18n()
const router = useRouter()
const store = useFunctionUnitStore()
const { removeRecent } = useRecentFunctionUnits()

const pagination = reactive({ page: 1, size: 20 })
const showImportDialog = ref(false)

const storeList = computed(() => store.list)
const storeAllTags = computed(() => store.allTags)

function loadData() {
  store.fetchList({
    tags: searchForm.tags.length > 0 ? searchForm.tags : undefined,
    page: pagination.page - 1,
    size: pagination.size,
  })
}

const {
  searchForm,
  availableTags,
  filteredList,
  hiddenByStatusCount,
  getItemTags,
  handleSearch,
  clearFilters,
} = useFunctionUnitFilters({
  list: storeList,
  allTags: storeAllTags,
  resetPage: () => { pagination.page = 1 },
  reload: loadData,
})

const {
  showFormDialog,
  formDialogMode,
  formSubmitting,
  formRef,
  basicForm,
  formDialogTitle,
  formRules,
  teamOptions,
  teamsLoading,
  showTeamSelector,
  teamEditable,
  activeGroupName,
  currentTeamNames,
  openCreateDialog,
  handleFormDialogClosed,
  handleSettings,
  handleFormSubmit,
} = useFunctionUnitForm({ store, reload: loadData })

const {
  showExportDialog,
  exporting,
  exportTargetId,
  openExportDialog,
  initExportSelection,
  handleExport,
} = useFunctionUnitExport({ list: storeList, filteredList })

// ==================== Launchpad (iOS-style icon layout + drag & drop + folders) ====================
const {
  visibleEntries,
  itemById,
  draggingKey,
  dropTarget,
  onDragStart,
  onDragEnd,
  onDragOver,
  onDragLeave,
  onDrop,
  onDropToEnd,
  folderById,
  renameFolder,
  reorderInFolder,
  removeFromFolder,
} = useLaunchpadLayout({
  list: storeList,
  visibleList: filteredList,
  defaultGroupName: () => t('functionUnit.newGroup'),
})

// Icon / card view toggle (icon by default); the choice is persisted
const VIEW_MODE_KEY = 'dw-fu-launchpad-view'
const viewMode = ref<'icon' | 'card'>(
  localStorage.getItem(VIEW_MODE_KEY) === 'card' ? 'card' : 'icon'
)
watch(viewMode, (v) => {
  try {
    localStorage.setItem(VIEW_MODE_KEY, v)
  } catch {
    // Storage unavailable: the choice only applies to this session
  }
})

// el-switch's boolean model: on = card view, off = icon view
const isCardView = computed({
  get: () => viewMode.value === 'card',
  set: (v: boolean) => { viewMode.value = v ? 'card' : 'icon' },
})

const openFolderId = ref<string | null>(null)
// Reconciling the layout can dissolve the open folder (e.g. fewer than 2 members left after a
// deletion); the computed then closes the overlay on its own
const openedFolder = computed<LaunchpadFolderEntry | null>(() =>
  openFolderId.value ? folderById(openFolderId.value) ?? null : null
)
const openedFolderItems = computed<FunctionUnitResponse[]>(() =>
  openedFolder.value
    ? openedFolder.value.itemIds
      .map((id) => itemById.value.get(id))
      .filter((i): i is FunctionUnitResponse => !!i)
    : []
)

function itemOf(id: number): FunctionUnitResponse | undefined {
  return itemById.value.get(id)
}

function folderItems(entry: LaunchpadFolderEntry): FunctionUnitResponse[] {
  return entry.itemIds
    .map((id) => itemById.value.get(id))
    .filter((i): i is FunctionUnitResponse => !!i)
}

function cellClasses(key: string) {
  return {
    'is-dragging': draggingKey.value === key,
    'drop-before': dropTarget.value?.key === key && dropTarget.value.mode === 'before',
    'drop-after': dropTarget.value?.key === key && dropTarget.value.mode === 'after',
    'drop-merge': dropTarget.value?.key === key && dropTarget.value.mode === 'merge',
  }
}

function handleEdit(item: FunctionUnitResponse) {
  router.push(`/function-units/${item.id}`)
}

async function handleClone(item: FunctionUnitResponse) {
  try {
    const { value } = await ElMessageBox.prompt(t('functionUnit.enterNewName'), t('functionUnit.cloneTitle'))
    if (!value?.trim()) {
      ElMessage.warning(t('functionUnit.enterNewName'))
      return
    }
    await store.clone(item.id, value.trim())
    ElMessage.success(t('functionUnit.cloneSuccess'))
    loadData()
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { message?: string })?.message === 'cancel') {
      return
    }
    ElMessage.error(resolveUserFacingHttpMessage(e, t) || t('functionUnit.cloneFailed'))
  }
}

async function handleDelete(item: FunctionUnitResponse) {
  const isArchived = item.status === 'ARCHIVED'
  if (isArchived) {
    await ElMessageBox.confirm(
      t('functionUnit.deletePermanentConfirm', { name: item.name }),
      t('functionUnit.deletePermanentTitle'),
      {
        type: 'error',
        confirmButtonText: t('functionUnit.deletePermanentConfirmButton'),
        cancelButtonText: t('common.cancel'),
        confirmButtonClass: 'el-button--danger',
      }
    )
  } else {
    await ElMessageBox.confirm(
      t('functionUnit.archiveConfirm', { name: item.name }),
      t('functionUnit.archiveTitle'),
      { type: 'warning' }
    )
  }
  await store.remove(item.id)
  // A permanent delete leaves the sidebar's "Recent" entry pointing at a function unit that
  // no longer exists, so drop it. Archiving only changes status (it stays openable) — keep
  // the entry and let the next list load refresh it.
  if (isArchived) removeRecent(item.id)
  ElMessage.success(isArchived ? t('functionUnit.deletePermanentSuccess') : t('functionUnit.archiveSuccess'))
  loadData()
}

async function handleRestore(item: FunctionUnitResponse) {
  try {
    await ElMessageBox.confirm(
      t('functionUnit.restoreConfirm', { name: item.name }),
      t('functionUnit.restoreTitle'),
      { type: 'warning' }
    )
  } catch {
    return // user cancelled
  }
  try {
    await store.restore(item.id)
    ElMessage.success(t('functionUnit.restoreSuccess'))
    loadData()
  } catch (e: unknown) {
    const message = (e as { response?: { data?: { error?: { message?: string }; message?: string } } })
      ?.response?.data?.error?.message
      || (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(message || t('functionUnit.restoreFailed'))
  }
}

onMounted(() => {
  // Check if logged in
  if (isAuthenticated()) {
    loadData()
    store.fetchAllTags()
  } else {
    // Not logged in, route guard should have redirected, but just in case
    redirectToUnifiedLogin('developer-workstation')
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

.filter-actions {
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

// ==================== View toggle switch ====================
// On = card view (red), off = icon view (grey); the icon sits inside the switch
.view-switch {
  // Give the off state a solid color too, so it does not read as disabled
  --el-switch-off-color: #b8bcc4;
}

// ==================== Launchpad (icon / card views + drag to reorder or merge) ====================
.launchpad-grid {
  align-content: start;
  min-height: 320px; // The empty area also accepts a "drop at the end"

  &--icon {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(116px, 1fr));
    gap: 26px 10px;
    padding: 20px 4px;
    justify-items: center;
  }

  &--card {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
  }
}

.launchpad-cell {
  position: relative;
  border-radius: 12px;
  transition: transform 0.15s ease, opacity 0.15s ease;

  &.is-dragging {
    opacity: 0.35;
  }

  // Merge-into-folder candidate: enlarge the target and outline it in the brand red
  &.drop-merge {
    :deep(.function-unit-card),
    :deep(.folder-card),
    :deep(.tile-icon),
    :deep(.folder-icon) {
      box-shadow: 0 0 0 3px var(--primary-color), 0 8px 24px rgba(0, 0, 0, 0.15);
    }
  }

  // Insertion indicator bar
  &.drop-before::before,
  &.drop-after::after {
    content: '';
    position: absolute;
    top: 16px;
    bottom: 16px;
    width: 3px;
    border-radius: 2px;
    background: var(--primary-color);
    z-index: 4;
  }
}

.launchpad-grid--card .launchpad-cell {
  &.drop-merge {
    transform: scale(1.02);
  }

  &.drop-before::before { left: -11px; }
  &.drop-after::after { right: -11px; }
}

.launchpad-grid--icon .launchpad-cell {
  padding: 6px;
  border-radius: 24px;

  &.drop-merge {
    transform: scale(1.08);
  }

  &.drop-before::before,
  &.drop-after::after {
    top: 10px;
    bottom: 28px;
  }

  &.drop-before::before { left: -6px; }
  &.drop-after::after { right: -6px; }
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

.pagination-wrapper {
  margin-top: auto;
  padding-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import MainTableViewDesigner from './MainTableViewDesigner.vue'
import { mainTableViewApi, type MainTableViewDefinition } from '@/api/mainTableView'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'

const props = defineProps<{
  functionUnitId: number
}>()

const { t } = useI18n()

const loading = ref(false)
const views = ref<MainTableViewDefinition[]>([])
const tables = ref<TableDefinition[]>([])
const selectedViewId = ref<number | null>(null)
const hasMainTable = ref(false)
const navCollapsed = ref(false)

const selectedView = ref<MainTableViewDefinition | null>(null)

// Only MAIN + SUB tables get views; RELATION tables are excluded.
const viewableTables = computed(() =>
  tables.value.filter(tbl => tbl.tableType === 'MAIN' || tbl.tableType === 'SUB'),
)

function tableLabel(table: TableDefinition): string {
  return table.tableDisplayName || table.tableName
}

// Views grouped by their owning table, in viewable-table order (MAIN tables surface first naturally).
const viewGroups = computed(() => {
  const byTable = new Map<number, MainTableViewDefinition[]>()
  for (const v of views.value) {
    const list = byTable.get(v.mainTableId) || []
    list.push(v)
    byTable.set(v.mainTableId, list)
  }
  return viewableTables.value
    .map(table => ({
      table,
      views: (byTable.get(table.id) || []).slice().sort((a, b) => {
        if (a.isDefault && !b.isDefault) return -1
        if (!a.isDefault && b.isDefault) return 1
        return a.viewName.localeCompare(b.viewName)
      }),
    }))
    .filter(group => group.views.length > 0)
})

async function loadTables() {
  try {
    const res = await functionUnitApi.getTables(props.functionUnitId)
    tables.value = res.data || []
    hasMainTable.value = tables.value.some(tbl => tbl.tableType === 'MAIN')
  } catch {
    tables.value = []
    hasMainTable.value = false
  }
}

async function loadViews() {
  loading.value = true
  try {
    const res = await mainTableViewApi.list(props.functionUnitId)
    views.value = res.data || []
    if (!selectedViewId.value && views.value.length) {
      selectedViewId.value = views.value[0].id
    }
    await loadSelectedView()
  } catch (e: any) {
    ElMessage.error(e?.message || t('mainTableView.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadSelectedView() {
  if (!selectedViewId.value) {
    selectedView.value = null
    return
  }
  try {
    const res = await mainTableViewApi.get(props.functionUnitId, selectedViewId.value)
    selectedView.value = res.data
  } catch {
    selectedView.value = views.value.find(v => v.id === selectedViewId.value) || null
  }
}

watch(selectedViewId, loadSelectedView)

// Per-table "+": create an empty view directly under that table (no dialog, no field copy).
// The table is already known from the group, so no table picker is needed; the developer renames
// it in the properties panel and adds columns from the catalog.
async function handleAddView(table: TableDefinition) {
  const baseName = t('mainTableView.newViewName')
  const existing = new Set(
    views.value.filter(v => v.mainTableId === table.id).map(v => v.viewName),
  )
  let name = baseName
  let i = 2
  while (existing.has(name)) name = `${baseName} ${i++}`
  try {
    const res = await mainTableViewApi.create(props.functionUnitId, name, table.id)
    await loadViews()
    selectedViewId.value = res.data.id
    ElMessage.success(t('mainTableView.created'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  }
}

const seeding = ref(false)

// Some viewable tables have no view yet (e.g. legacy function units created before per-table views).
const hasTablesWithoutViews = computed(() => {
  const tablesWithView = new Set(views.value.map(v => v.mainTableId))
  return viewableTables.value.some(tbl => !tablesWithView.has(tbl.id))
})

async function handleSeedDefaults() {
  seeding.value = true
  try {
    const res = await mainTableViewApi.seedDefaults(props.functionUnitId)
    views.value = res.data || []
    if (!selectedViewId.value && views.value.length) {
      selectedViewId.value = views.value[0].id
    }
    await loadSelectedView()
    ElMessage.success(t('mainTableView.defaultsGenerated'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  } finally {
    seeding.value = false
  }
}

async function handleDeleteView(view: MainTableViewDefinition) {
  try {
    await ElMessageBox.confirm(
      t('mainTableView.deleteConfirm', { name: view.viewName }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await mainTableViewApi.delete(props.functionUnitId, view.id)
    if (selectedViewId.value === view.id) {
      selectedViewId.value = null
    }
    await loadViews()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('common.deleteFailed'))
    }
  }
}

function onViewSaved(updated: MainTableViewDefinition) {
  selectedView.value = updated
  // Reassign the array so the grouped nav computed re-derives (and the left-nav name updates).
  views.value = views.value.map(v => (v.id === updated.id ? { ...v, ...updated } : v))
}

// Designer-internal FK navigation: open the default view of the referenced table.
function navigateToTableView(refTableId: number) {
  const defaultView = views.value.find(v => v.mainTableId === refTableId && v.isDefault)
    || views.value.find(v => v.mainTableId === refTableId)
  if (defaultView) {
    selectedViewId.value = defaultView.id
  } else {
    ElMessage.warning(t('mainTableView.noViewForRefTable'))
  }
}

onMounted(async () => {
  await loadTables()
  if (hasMainTable.value) {
    await loadViews()
  }
})
</script>

<template>
  <div
    v-loading="loading"
    class="view-design-tab"
  >
    <el-empty
      v-if="!hasMainTable"
      :description="t('mainTableView.needMainTable')"
    />

    <template v-else>
      <div class="view-design-layout">
        <div
          class="view-list-panel"
          :class="{ collapsed: navCollapsed }"
        >
          <div class="panel-header">
            <span v-show="!navCollapsed">{{ t('mainTableView.viewList') }}</span>
            <div class="panel-header-actions">
              <el-button
                v-if="!navCollapsed && hasTablesWithoutViews"
                type="primary"
                size="small"
                link
                :loading="seeding"
                @click="handleSeedDefaults"
              >
                {{ t('mainTableView.generateDefaults') }}
              </el-button>
              <el-button
                text
                size="small"
                :icon="navCollapsed ? DArrowRight : DArrowLeft"
                :title="navCollapsed ? t('mainTableView.expandNav') : t('mainTableView.collapseNav')"
                @click="navCollapsed = !navCollapsed"
              />
            </div>
          </div>

          <div
            v-show="!navCollapsed"
            class="view-groups"
          >
            <!--
              Single shared el-menu across all table groups. Element Plus computes
              each el-menu's active-index state in its own instance scope on mount —
              nesting one el-menu PER table group meant clicking a view in a different
              group never deactivated the previously-selected group's menu (stale
              highlight) and could route @select to a stale/mismatched instance.
              One el-menu = one source of truth for active-item + click routing.
            -->
            <el-menu
              :default-active="selectedViewId ? String(selectedViewId) : ''"
              @select="(idx: string) => { selectedViewId = Number(idx) }"
            >
              <template
                v-for="group in viewGroups"
                :key="group.table.id"
              >
                <div class="view-group-header">
                  <span class="view-group-title">{{ tableLabel(group.table) }}</span>
                  <!-- Marks the category: this table's views open the request detail page
                       in the portal, so they bind no detail form of their own. -->
                  <el-tag
                    v-if="group.table.tableType === 'MAIN'"
                    size="small"
                    type="info"
                  >
                    {{ t('table.mainTable') }}
                  </el-tag>
                  <el-button
                    type="primary"
                    size="small"
                    link
                    :icon="Plus"
                    :title="t('mainTableView.createView')"
                    @click="handleAddView(group.table)"
                  />
                </div>
                <el-menu-item
                  v-for="view in group.views"
                  :key="view.id"
                  :index="String(view.id)"
                >
                  <div class="view-menu-row">
                    <span class="view-name-text">{{ view.viewName }}</span>
                    <el-tag
                      v-if="view.isDefault"
                      size="small"
                      type="info"
                    >
                      {{ t('mainTableView.defaultTag') }}
                    </el-tag>
                    <el-button
                      type="danger"
                      size="small"
                      link
                      :icon="Delete"
                      @click.stop="handleDeleteView(view)"
                    />
                  </div>
                </el-menu-item>
              </template>
            </el-menu>
          </div>
        </div>

        <div class="view-editor-panel">
          <MainTableViewDesigner
            v-if="selectedView"
            :key="selectedView.id"
            :function-unit-id="functionUnitId"
            :view="selectedView"
            @saved="onViewSaved"
            @navigate-to-table-view="navigateToTableView"
          />
          <el-empty
            v-else
            :description="views.length ? t('mainTableView.selectView') : t('mainTableView.noViewsYet')"
          >
            <el-button
              v-if="hasTablesWithoutViews"
              type="primary"
              :loading="seeding"
              @click="handleSeedDefaults"
            >
              {{ t('mainTableView.generateDefaults') }}
            </el-button>
          </el-empty>
        </div>
      </div>
    </template>

  </div>
</template>

<style scoped lang="scss">
.view-design-tab { min-height: 400px; }
.view-design-layout {
  display: flex;
  gap: 12px;
  min-height: 520px;
  height: calc(100vh - 280px);
}
.view-list-panel {
  width: 240px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s ease;

  &.collapsed {
    width: 40px;
  }
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 8px 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.panel-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.view-groups {
  flex: 1;
  overflow-y: auto;
}
.view-group + .view-group {
  border-top: 1px solid var(--el-border-color-lighter);
}
.view-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}
/* Title takes the slack so the tag sits beside the name and the + button stays right-aligned,
   which `justify-content: space-between` could not do once a third child appeared. */
.view-group-header .view-group-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.view-group-header .el-tag {
  flex-shrink: 0;
}
.view-menu-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.view-name-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.view-editor-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
</style>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import MainTableViewDesigner from './MainTableViewDesigner.vue'
import { mainTableViewApi, type MainTableViewDefinition } from '@/api/mainTableView'
import { functionUnitApi, type TableDefinition } from '@/api/functionUnit'

const props = defineProps<{
  functionUnitId: number
}>()

const { t } = useI18n()

const loading = ref(false)
const views = ref<MainTableViewDefinition[]>([])
const selectedViewId = ref<number | null>(null)
const hasMainTable = ref(false)
const createDialogVisible = ref(false)
const newViewName = ref('')

const selectedView = ref<MainTableViewDefinition | null>(null)

async function checkMainTable() {
  try {
    const res = await functionUnitApi.getTables(props.functionUnitId)
    const tables: TableDefinition[] = res.data || []
    hasMainTable.value = tables.some(tbl => tbl.tableType === 'MAIN')
  } catch {
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

async function handleCreateView() {
  const name = newViewName.value.trim()
  if (!name) return
  try {
    const res = await mainTableViewApi.create(props.functionUnitId, name)
    createDialogVisible.value = false
    newViewName.value = ''
    await loadViews()
    selectedViewId.value = res.data.id
    ElMessage.success(t('mainTableView.created'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  }
}

async function handleDeleteView(view: MainTableViewDefinition) {
  if (view.isDefault) {
    ElMessage.warning(t('mainTableView.cannotDeleteDefault'))
    return
  }
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
  const idx = views.value.findIndex(v => v.id === updated.id)
  if (idx >= 0) {
    views.value[idx] = { ...views.value[idx], ...updated }
  }
}

onMounted(async () => {
  await checkMainTable()
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
        <div class="view-list-panel">
          <div class="panel-header">
            <span>{{ t('mainTableView.viewList') }}</span>
            <el-button
              type="primary"
              size="small"
              :icon="Plus"
              @click="createDialogVisible = true"
            />
          </div>
          <el-menu
            :default-active="selectedViewId ? String(selectedViewId) : ''"
            @select="(idx: string) => { selectedViewId = Number(idx) }"
          >
            <el-menu-item
              v-for="view in views"
              :key="view.id"
              :index="String(view.id)"
            >
              <div class="view-menu-row">
                <span>{{ view.viewName }}</span>
                <el-tag
                  v-if="view.isDefault"
                  size="small"
                  type="info"
                >
                  {{ t('mainTableView.defaultTag') }}
                </el-tag>
                <el-button
                  v-if="!view.isDefault"
                  type="danger"
                  size="small"
                  link
                  :icon="Delete"
                  @click.stop="handleDeleteView(view)"
                />
              </div>
            </el-menu-item>
          </el-menu>
        </div>

        <div class="view-editor-panel">
          <MainTableViewDesigner
            v-if="selectedView"
            :function-unit-id="functionUnitId"
            :view="selectedView"
            @saved="onViewSaved"
          />
          <el-empty
            v-else
            :description="t('mainTableView.selectView')"
          />
        </div>
      </div>
    </template>

    <el-dialog
      v-model="createDialogVisible"
      :title="t('mainTableView.createView')"
      width="400px"
    >
      <el-input
        v-model="newViewName"
        :placeholder="t('mainTableView.viewNamePlaceholder')"
      />
      <template #footer>
        <el-button @click="createDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleCreateView"
        >
          {{ t('common.create') }}
        </el-button>
      </template>
    </el-dialog>
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
  width: 220px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.view-menu-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.view-editor-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
</style>

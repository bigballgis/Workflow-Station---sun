<template>
  <div class="form-list-sidebar">
    <div class="designer-toolbar">
      <el-button
        type="primary"
        @click="$emit('create')"
      >
        <el-icon><Plus /></el-icon> {{ $t('form.createForm') }}
      </el-button>
      <el-button
        :loading="loading"
        @click="$emit('refresh')"
      >
        <el-icon><Refresh /></el-icon> {{ $t('common.refresh') }}
      </el-button>
      <el-button
        :disabled="!hasTables"
        @click="$emit('importFromTable')"
      >
        <el-icon><Connection /></el-icon> {{ $t('form.importFields') }}
      </el-button>
    </div>

    <!-- One scene at a time: To Do and My Requests are separate designs of the same
         steps, so interleaving them in one list makes neither readable. -->
    <el-tabs
      v-model="activeScene"
      class="form-scene-tabs"
    >
      <el-tab-pane
        :label="`${t('form.sceneTask')} (${sceneCounts.TASK})`"
        name="TASK"
      />
      <el-tab-pane
        :label="`${t('form.sceneRequest')} (${sceneCounts.REQUEST})`"
        name="REQUEST"
      />
      <!-- Always shown, even at zero: this tab is where a view's detail form is chosen, so
           hiding it until one exists leaves the developer with no way to discover the feature. -->
      <el-tab-pane
        :label="`${t('form.viewsForm')} (${sceneCounts.DETAIL})`"
        name="DETAIL"
      />
    </el-tabs>

    <!-- Views Form groups by table, because a detail form serves the views of one table and
         those views are chosen right here. The step scenes stay a flat list. -->
    <div
      v-if="activeScene === 'DETAIL'"
      class="table-scroll-wrap views-form-groups"
    >
      <div
        v-for="group in viewsFormGroups"
        :key="group.key"
        class="views-form-group"
      >
        <div class="views-form-group-header">
          <span class="views-form-group-title">{{ group.label }}</span>
          <span class="views-form-group-count">{{ group.forms.length }}</span>
        </div>

        <div
          v-if="group.forms.length > 0 || group.views.length > 0"
          class="views-form-cards"
        >
          <div class="views-form-header-row">
            <span class="views-form-col-name">{{ t('form.formName') }}</span>
            <span class="views-form-col-views">{{ t('form.usedByViews') }}</span>
            <span class="views-form-col-actions" />
          </div>

          <div
            v-for="form in group.forms"
            :key="form.id"
            class="views-form-card"
            @click="$emit('selectForm', form)"
          >
            <span
              class="views-form-col-name views-form-name"
              :title="form.formName"
            >{{ form.formName }}</span>

            <span class="views-form-col-views views-form-usedby">
              <el-tag
                v-for="view in viewsUsingForm(form.id)"
                :key="view.id"
                size="small"
                type="info"
                round
              >
                {{ view.viewName }}
              </el-tag>
              <span
                v-if="viewsUsingForm(form.id).length === 0"
                class="text-muted"
              >{{ t('form.notUsedByAnyView') }}</span>
            </span>

            <span
              class="views-form-col-actions"
              @click.stop
            >
              <el-popover
                :width="260"
                trigger="click"
                :title="t('form.bindViewsPopoverTitle')"
              >
                <template #reference>
                  <el-button
                    link
                    type="primary"
                    size="small"
                  >
                    {{ t('form.bindViews') }}
                  </el-button>
                </template>
                <div
                  v-if="group.views.length === 0"
                  class="text-muted"
                >
                  {{ t('form.bindViewsNone') }}
                </div>
                <el-checkbox-group
                  v-else
                  :model-value="viewsUsingForm(form.id).map(v => v.id)"
                  class="bind-views-checkbox-group"
                  @change="(ids: any) => $emit('setFormBoundViews', form, group.views, ids as number[])"
                >
                  <el-checkbox
                    v-for="view in group.views"
                    :key="view.id"
                    :value="view.id"
                    :label="view.id"
                  >
                    {{ view.viewName }}
                  </el-checkbox>
                </el-checkbox-group>
              </el-popover>
              <el-button
                link
                type="danger"
                size="small"
                @click.stop="$emit('deleteForm', form)"
              >
                {{ t('common.delete') }}
              </el-button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div
      v-else
      class="table-scroll-wrap"
    >
      <DesignerListTable
        :loading="loading"
        :storage-key="storageKey"
        :columns="listColumns"
        :rows="visibleForms"
        @row-click="(row: any) => $emit('selectForm', row)"
      >
        <template #cell-formType="{ row }">
          <el-tag :type="formTypeTagType(row.formType)">
            {{ formTypeLabel(row.formType) }}
          </el-tag>
        </template>
        <template #cell-boundTableId="{ row }">
          <template v-if="getPrimaryBinding(row)">
            <el-tag
              type="success"
              size="small"
            >
              {{ getPrimaryBinding(row)!.tableName }}
            </el-tag>
            <el-tag
              v-if="getSubBindingsCount(row) > 0"
              type="info"
              size="small"
              style="margin-left: 4px;"
            >
              +{{ getSubBindingsCount(row) }}
            </el-tag>
          </template>
          <el-tag
            v-else-if="row.boundTableId"
            type="success"
            size="small"
          >
            {{ getTableName(row.boundTableId) }}
          </el-tag>
          <span
            v-else
            class="text-muted"
          >{{ $t('form.notBound') }}</span>
        </template>
        <template #cell-boundNodeId="{ row }">
          <div class="bound-nodes">
            <template v-if="getFormBoundNodes(row.id).length > 0">
              <el-tag
                v-for="node in getFormBoundNodes(row.id)"
                :key="node.nodeId"
                :type="node.readOnly ? 'info' : 'success'"
                size="small"
                class="node-tag"
              >
                {{ node.nodeName }}{{ node.readOnly ? `(${$t('form.readOnly')})` : '' }}
              </el-tag>
            </template>
            <span
              v-else
              class="text-muted"
            >{{ $t('form.notBound') }}</span>
          </div>
        </template>
        <template #actions="{ row }">
          <div class="action-buttons">
            <el-button
              link
              type="primary"
              @click.stop="$emit('selectForm', row)"
            >
              {{ $t('common.edit') }}
            </el-button>
            <el-button
              link
              type="danger"
              @click.stop="$emit('deleteForm', row)"
            >
              {{ $t('common.delete') }}
            </el-button>
            <el-dropdown
              trigger="click"
              @command="(cmd: string) => $emit('moreAction', cmd, row)"
            >
              <el-button
                link
                type="primary"
                @click.stop
              >
                {{ $t('common.more') }}
                <el-icon class="action-more-icon">
                  <ArrowDown />
                </el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    {{ $t('form.renameForm') }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.formType === 'TASK'"
                    command="copy"
                  >
                    {{ $t('form.copyForm') }}
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.formType === 'PROCESS'"
                    command="copy-to-task"
                  >
                    {{ $t('form.copyProcessToTaskForm') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="bindings">
                    {{ $t('form.editBindings') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="bindNode">
                    {{ $t('form.boundNode') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </DesignerListTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Connection, ArrowDown } from '@element-plus/icons-vue'
import DesignerListTable from '@/components/designer-list/DesignerListTable.vue'
import type { DesignerListTableColumn } from '@/composables/useDesignerListGrid'
import { resolveFormTableId } from '@/utils/formDesigner'

const props = withDefaults(
  defineProps<{
    functionUnitId: number
    forms: any[]
    loading: boolean
    hasTables: boolean
    formTypeLabel: (type: string) => string
    getPrimaryBinding: (row: any) => any
    getSubBindingsCount: (row: any) => number
    getTableName: (tableId: number) => string
    getFormBoundNodes: (formId: number) => any[]
    /** Table catalog — drives the Views Form grouping order. */
    tables?: any[]
    /** Main-table views of this function unit, for the Views Form "used by views" column and Bind Views picker. */
    mainTableViews?: any[]
    /**
     * Scene tab to open on mount — e.g. the scene of the form the developer just backed out of,
     * so returning from a My Requests design doesn't drop them back on the To Do tab.
     */
    initialScene?: 'TASK' | 'REQUEST' | 'DETAIL'
  }>(),
  {
    tables: () => [],
    mainTableViews: () => [],
    initialScene: 'TASK',
  },
)

defineEmits<{
  create: []
  refresh: []
  importFromTable: []
  selectForm: [row: any]
  deleteForm: [row: any]
  moreAction: [cmd: string, row: any]
  /**
   * Bind-from-the-form-side: the user toggled which of `candidateViews` should use `form` as
   * their detail page. `checkedViewIds` is the full next-state list from el-checkbox-group (not
   * a delta) — the handler diffs it against each view's current detailFormId to know whether to
   * set or clear that one view's binding.
   */
  setFormBoundViews: [form: any, candidateViews: any[], checkedViewIds: number[]]
}>()

const { t } = useI18n()

/**
 * DETAIL forms are opened from a view row rather than a workflow step, so they
 * belong to neither scene and get a tab of their own.
 */
type SceneTab = 'TASK' | 'REQUEST' | 'DETAIL'

function sceneOf(row: any): SceneTab {
  if (row?.formType === 'DETAIL') return 'DETAIL'
  return (row?.scene ?? 'TASK') === 'REQUEST' ? 'REQUEST' : 'TASK'
}

const activeScene = ref<SceneTab>(props.initialScene)

const sceneCounts = computed(() => {
  const counts: Record<SceneTab, number> = { TASK: 0, REQUEST: 0, DETAIL: 0 }
  for (const f of props.forms) counts[sceneOf(f)]++
  return counts
})

const visibleForms = computed(() => props.forms.filter(f => sceneOf(f) === activeScene.value))

/** Views referencing a given detail form. Reverse of the view-side detailFormId column. */
function viewsUsingForm(formId: number): any[] {
  return props.mainTableViews.filter(v => Number(v?.detailFormId) === Number(formId))
}

/**
 * Views Form content, grouped by table: the detail forms bound to each table alongside that
 * table's views, so a view's detail form is chosen next to the forms that can serve it.
 *
 * <p>Unlike View Design's grouping, empty groups are kept — a table whose views have no detail
 * form yet is exactly where a developer needs to make a selection. Forms with no resolvable
 * table get a trailing bucket instead of disappearing.
 */
const viewsFormGroups = computed(() => {
  const detailForms = props.forms.filter(f => f?.formType === 'DETAIL')
  const formsByTable = new Map<number, any[]>()
  const unbound: any[] = []
  for (const form of detailForms) {
    const tableId = resolveFormTableId(form)
    if (tableId == null) {
      unbound.push(form)
      continue
    }
    const list = formsByTable.get(tableId) || []
    list.push(form)
    formsByTable.set(tableId, list)
  }

  const viewsByTable = new Map<number, any[]>()
  for (const view of props.mainTableViews) {
    const tableId = Number(view?.mainTableId)
    if (!Number.isFinite(tableId)) continue
    const list = viewsByTable.get(tableId) || []
    list.push(view)
    viewsByTable.set(tableId, list)
  }

  const groups = props.tables.map(table => ({
    key: `table-${table.id}`,
    label: table.tableDisplayName || table.tableName,
    forms: formsByTable.get(table.id) || [],
    views: (viewsByTable.get(table.id) || [])
      .slice()
      .sort((a, b) => String(a.viewName || '').localeCompare(String(b.viewName || ''))),
  }))
  // Only tables that can hold a form or a view are worth a heading.
  const meaningful = groups.filter(g => g.forms.length > 0 || g.views.length > 0)

  if (unbound.length > 0) {
    meaningful.push({
      key: 'unbound',
      label: t('form.unboundTableGroup'),
      forms: unbound,
      views: [],
    })
  }
  return meaningful
})

function boundTableText(row: any): string {
  const primary = props.getPrimaryBinding(row)
  if (primary) {
    const extra = props.getSubBindingsCount(row)
    return extra > 0 ? `${primary.tableName} +${extra}` : String(primary.tableName || '')
  }
  if (row.boundTableId) return props.getTableName(row.boundTableId)
  return t('form.notBound')
}

function boundNodesText(row: any): string {
  const nodes = props.getFormBoundNodes(row.id)
  if (!nodes.length) return t('form.notBound')
  return nodes
    .map((n: { nodeName?: string; readOnly?: boolean }) =>
      n.readOnly ? `${n.nodeName}(${t('form.readOnly')})` : String(n.nodeName || ''),
    )
    .join(', ')
}

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

/** Four types now, so PROCESS-or-not is no longer enough to tell them apart. */
function formTypeTagType(formType: string): TagType {
  switch (formType) {
    case 'PROCESS': return 'primary'
    case 'TASK': return 'info'
    case 'ACTION': return 'warning'
    case 'DETAIL': return 'success'
    default: return 'info'
  }
}

const storageKey = computed(() => `${props.functionUnitId}:forms`)

const listColumns = computed<DesignerListTableColumn<any>[]>(() => [
  {
    key: 'formName',
    prop: 'formName',
    label: t('form.formName'),
    defaultWidth: 160,
    showOverflowTooltip: true,
  },
  {
    key: 'formType',
    prop: 'formType',
    label: t('form.formType'),
    defaultWidth: 120,
    getValue: (row) => props.formTypeLabel(row.formType),
  },
  {
    key: 'boundTableId',
    prop: 'boundTableId',
    label: t('form.boundTable'),
    defaultWidth: 180,
    showOverflowTooltip: true,
    getValue: boundTableText,
  },
  {
    key: 'boundNodeId',
    prop: 'boundNodeId',
    label: t('form.boundNode'),
    defaultWidth: 180,
    showOverflowTooltip: true,
    getValue: boundNodesText,
  },
  {
    key: 'description',
    prop: 'description',
    label: t('table.description'),
    defaultWidth: 180,
    showOverflowTooltip: true,
  },
])
</script>

<style lang="scss" scoped>
.designer-toolbar {
  margin-bottom: 16px;
}

/* Tabs sit directly above the table, so drop the default trailing gap. */
.form-scene-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.form-scene-tabs :deep(.el-tabs__content) {
  display: none;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.views-form-groups {
  padding: 4px 2px;
}

.views-form-group {
  margin-bottom: 28px;

  &:last-child {
    margin-bottom: 0;
  }
}

.views-form-group-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 0 2px 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.views-form-group-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.views-form-group-count {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.views-form-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Shared column grid so the form name, the views tag list, and the action buttons
   line up across every card in the group — a header row above them names the columns. */
.views-form-header-row,
.views-form-card {
  display: grid;
  grid-template-columns: 220px 1fr 190px;
  align-items: center;
  gap: 16px;
}

.views-form-header-row {
  padding: 0 14px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-placeholder);
}

.views-form-col-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.views-form-card {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  cursor: pointer;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
}

.views-form-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.views-form-usedby {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.bind-views-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 240px;
  overflow-y: auto;
}

.action-more-icon {
  margin-left: 2px;
  vertical-align: middle;
}
</style>

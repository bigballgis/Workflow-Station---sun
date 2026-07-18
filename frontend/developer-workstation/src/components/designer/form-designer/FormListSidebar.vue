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
    
    <div class="table-scroll-wrap">
      <DesignerListTable
        :loading="loading"
        :storage-key="storageKey"
        :columns="listColumns"
        :rows="toRef(props, 'forms')"
        @row-click="(row: any) => $emit('selectForm', row)"
      >
        <template #cell-formType="{ row }">
          <el-tag :type="row.formType === 'PROCESS' ? 'primary' : 'info'">
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
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Connection, ArrowDown } from '@element-plus/icons-vue'
import DesignerListTable from '@/components/designer-list/DesignerListTable.vue'
import type { DesignerListTableColumn } from '@/composables/useDesignerListGrid'

const props = defineProps<{
  functionUnitId: number
  forms: any[]
  loading: boolean
  hasTables: boolean
  formTypeLabel: (type: string) => string
  getPrimaryBinding: (row: any) => any
  getSubBindingsCount: (row: any) => number
  getTableName: (tableId: number) => string
  getFormBoundNodes: (formId: number) => any[]
}>()

defineEmits<{
  create: []
  refresh: []
  importFromTable: []
  selectForm: [row: any]
  deleteForm: [row: any]
  moreAction: [cmd: string, row: any]
}>()

const { t } = useI18n()

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

.text-muted {
  color: #909399;
  font-size: 12px;
}

.action-more-icon {
  margin-left: 2px;
  vertical-align: middle;
}
</style>

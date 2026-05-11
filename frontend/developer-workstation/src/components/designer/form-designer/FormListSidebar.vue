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
    
    <el-table
      v-loading="loading"
      :data="forms"
      stripe
      @row-click="(row: any) => $emit('selectForm', row)"
    >
      <el-table-column
        prop="formName"
        :label="$t('form.formName')"
      />
      <el-table-column
        prop="formType"
        :label="$t('form.formType')"
        width="120"
      >
        <template #default="{ row }">
          <el-tag :type="row.formType === 'PROCESS' ? 'primary' : 'info'">
            {{ formTypeLabel(row.formType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="boundTableId"
        :label="$t('form.boundTable')"
        width="180"
      >
        <template #default="{ row }">
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
      </el-table-column>
      <el-table-column
        prop="boundNodeId"
        :label="$t('form.boundNode')"
        min-width="180"
      >
        <template #default="{ row }">
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
      </el-table-column>
      <el-table-column
        prop="description"
        :label="$t('table.description')"
        show-overflow-tooltip
      />
      <el-table-column
        :label="$t('common.actions')"
        width="200"
        fixed="right"
        align="left"
      >
        <template #default="{ row }">
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
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { Plus, Refresh, Connection, ArrowDown } from '@element-plus/icons-vue'

defineProps<{
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
</script>

<style lang="scss" scoped>
.designer-toolbar {
  margin-bottom: 16px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.bound-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.node-tag {
  margin: 0;
}

.action-buttons {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.action-more-icon {
  margin-left: 2px;
  vertical-align: middle;
}
</style>

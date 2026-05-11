<template>
  <el-dialog
    :key="bindDialogKey"
    :model-value="modelValue"
    :title="$t('form.bindNodeTitle')"
    width="650px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="bind-dialog-content">
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px;"
      >
        {{ $t('form.bindNodeHint') }}
      </el-alert>
      <div
        v-if="processNodes.length"
        class="node-list"
      >
        <div
          v-for="node in processNodes"
          :key="`${node.id}-${bindDialogKey}`"
          class="node-item"
        >
          <el-checkbox 
            :key="`checkbox-${node.id}-${bindDialogKey}`"
            :model-value="isNodeSelected(node.id)"
            @change="toggleNodeSelection(node.id, node.name, $event as boolean)"
          />
          <div
            class="node-icon"
            :class="node.type"
          />
          <div class="node-info">
            <div class="node-name">
              {{ node.name }}
            </div>
            <div class="node-type">
              {{ nodeTypeLabel(node.type) }}
            </div>
          </div>
          <el-checkbox 
            v-if="isNodeSelected(node.id)"
            :model-value="isNodeReadOnly(node.id)"
            @change="setNodeReadOnly(node.id, $event as boolean)"
          >
            {{ $t('form.readOnly') }}
          </el-checkbox>
        </div>
      </div>
      <el-empty
        v-else
        :description="$t('form.noNodesAvailable')"
      />
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        @click="$emit('confirm')"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  processNodes: any[]
  bindDialogKey: number
  isNodeSelected: (id: string) => boolean
  isNodeReadOnly: (id: string) => boolean
  toggleNodeSelection: (id: string, name: string, selected: boolean) => void
  setNodeReadOnly: (id: string, readOnly: boolean) => void
  nodeTypeLabel: (type: string) => string
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()
</script>

<style lang="scss" scoped>
.bind-dialog-content {
  .node-list {
    max-height: 350px;
    overflow-y: auto;
  }
  
  .node-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border: 1px solid #e6e6e6;
    border-radius: 4px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: all 0.2s;
    
    &:hover {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.02);
    }
    
    &.selected {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.08);
    }
    
    .node-icon {
      width: 32px;
      height: 32px;
      border-radius: 4px;
      
      &.userTask { background-color: #409EFF; }
      &.serviceTask { background-color: #67C23A; }
      &.startEvent { background-color: #00A651; border-radius: 50%; }
      &.endEvent { background-color: #DB0011; border-radius: 50%; }
    }
    
    .node-info {
      flex: 1;
      
      .node-name {
        font-weight: 500;
        margin-bottom: 2px;
      }
      
      .node-type {
        font-size: 12px;
        color: #909399;
      }
    }
    
    .check-icon {
      color: #DB0011;
      font-size: 20px;
    }
  }
}
</style>

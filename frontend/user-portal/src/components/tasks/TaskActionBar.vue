<template>
  <div v-if="!isCompletedTask" class="section action-section">
    <div class="action-buttons">
      <div class="left-actions">
        <el-button @click="$router.back()">{{ $t('task.backToList') }}</el-button>
      </div>
      <div class="right-actions">
        <el-button
          v-if="showImplicitSaveAction"
          type="primary"
          :loading="savingTaskForm"
          @click="$emit('save')"
        >
          {{ $t('common.save') }}
        </el-button>
        <!-- Show custom buttons when custom Actions are configured -->
        <template v-if="actions && actions.length > 0">
          <el-button
            v-for="action in actions"
            :key="action.actionId"
            :type="getButtonType(action.buttonColor)"
            @click="$emit('customAction', action)"
          >
            <el-icon v-if="action.icon"><component :is="getIconComponent(action.icon)" /></el-icon>
            {{ getActionLabel(action) }}
          </el-button>
        </template>
        <!-- Show default approval buttons when no custom Actions are configured -->
        <template v-else-if="actions === undefined || actions === null">
          <el-button type="success" @click="$emit('approve')">
            <el-icon><Check /></el-icon> {{ $t('task.approve') }}
          </el-button>
          <el-button type="danger" @click="$emit('reject')">
            <el-icon><Close /></el-icon> {{ $t('task.reject') }}
          </el-button>
        </template>
        <!-- Transfer, delegate, urge always shown -->
        <el-button @click="$emit('delegate')">
          <el-icon><User /></el-icon> {{ $t('task.delegate') }}
        </el-button>
        <el-button @click="$emit('transfer')">
          <el-icon><Switch /></el-icon> {{ $t('task.transfer') }}
        </el-button>
        <el-button type="warning" @click="$emit('urge')">
          <el-icon><Bell /></el-icon> {{ $t('task.urge') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Check, Close, User, Switch, Bell } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import type { TaskActionInfo } from '@/api/task'

defineProps<{
  isCompletedTask: boolean
  showImplicitSaveAction: boolean
  savingTaskForm: boolean
  actions: TaskActionInfo[] | undefined | null
  getButtonType: (color?: string) => string
  getIconComponent: (iconName?: string) => Component
  getActionLabel: (action: TaskActionInfo) => string
}>()

defineEmits<{
  (e: 'save'): void
  (e: 'customAction', action: TaskActionInfo): void
  (e: 'approve'): void
  (e: 'reject'): void
  (e: 'delegate'): void
  (e: 'transfer'): void
  (e: 'urge'): void
}>()
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);
}

.action-section {
  position: sticky;
  bottom: 0;
  z-index: 10;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e4e7ed);
}

.action-buttons {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .left-actions, .right-actions {
    display: flex;
    gap: 12px;
  }
}
</style>

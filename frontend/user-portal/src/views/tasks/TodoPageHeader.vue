<template>
  <div class="page-header">
    <div class="page-header-row">
      <h1 class="page-title-row">
        <span>{{ t('task.title') }}</span>
        <PortalHelpLink
          path="/up-tasks-to-claim"
          :aria-label="t('task.todoGuideLinkAria')"
          test-id="todo-guide-link"
        />
      </h1>
      <div class="page-header-actions">
        <label class="auto-claim-toggle">
          <span>{{ t('task.autoClaimOnOpen') }}</span>
          <el-switch
            data-test="todo-auto-claim-switch"
            :model-value="autoClaimOnOpen"
            :loading="saving"
            :disabled="saving"
            @change="onChange"
          />
        </label>
        <el-button
          data-test="todo-unclaim-all-btn"
          :loading="busy"
          @click="$emit('unclaim-all')"
        >
          {{ t('task.unclaimAll') }}
        </el-button>
        <el-button
          type="primary"
          data-test="todo-claim-all-btn"
          :loading="busy"
          @click="$emit('claim-all')"
        >
          {{ t('task.claimAll') }}
        </el-button>
      </div>
    </div>
    <p class="page-subtitle">{{ t('task.todoHint') }}</p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PortalHelpLink from '@/components/PortalHelpLink.vue'

defineProps<{
  autoClaimOnOpen: boolean
  saving: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  'claim-all': []
  'unclaim-all': []
  'auto-claim-change': [value: boolean]
}>()

const { t } = useI18n()

function onChange(value: string | number | boolean) {
  emit('auto-claim-change', value === true)
}
</script>

<style lang="scss" scoped>
.page-header {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.page-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
}

.auto-claim-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
}

.page-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 24px;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>

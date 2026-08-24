<template>
  <div class="step-node">
    <div class="step-card">
      <div class="step-card__head">
        <span class="step-card__title">{{ step.displayName }}</span>
        <el-tag
          v-if="kindTag"
          :type="kindTag.type"
          size="small"
          effect="plain"
          disable-transitions
        >
          {{ kindTag.label }}
        </el-tag>
      </div>
      <div
        v-if="metaText"
        class="step-card__meta"
      >
        {{ metaText }}
      </div>
    </div>

    <div
      v-if="step.kind === 'router' && step.branches.length"
      class="step-nested"
    >
      <div
        v-for="(branch, bi) in step.branches"
        :key="bi"
        class="step-branch"
      >
        <div class="step-nested__label">
          {{ branch.label || t('automationFlow.branchLabel', { index: bi + 1 }) }}
        </div>
        <div
          v-if="branch.steps.length === 0"
          class="step-nested__empty"
        >
          {{ t('automationFlow.branchEmpty') }}
        </div>
        <template
          v-for="(child, ci) in branch.steps"
          :key="child.name || ci"
        >
          <div
            v-if="ci > 0"
            class="step-connector"
          />
          <FlowStepNode :step="child" />
        </template>
      </div>
    </div>

    <div
      v-if="step.kind === 'loop' && step.loopSteps.length"
      class="step-nested"
    >
      <div class="step-branch">
        <div class="step-nested__label">
          {{ t('automationFlow.loopBody') }}
        </div>
        <template
          v-for="(child, ci) in step.loopSteps"
          :key="child.name || ci"
        >
          <div
            v-if="ci > 0"
            class="step-connector"
          />
          <FlowStepNode :step="child" />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FlowStepView } from '@/utils/flowStructure'

const props = defineProps<{ step: FlowStepView }>()

const { t } = useI18n()

/** piece 是最常见节点,不打标签;其余类型用标签标出角色 */
const kindTag = computed(() => {
  switch (props.step.kind) {
    case 'trigger':
      return { type: 'primary' as const, label: t('automationFlow.stepKindTrigger') }
    case 'router':
      return { type: 'warning' as const, label: t('automationFlow.stepKindRouter') }
    case 'loop':
      return { type: 'info' as const, label: t('automationFlow.stepKindLoop') }
    case 'code':
      return { type: 'info' as const, label: t('automationFlow.stepKindCode') }
    case 'unknown':
      return { type: 'danger' as const, label: t('automationFlow.stepKindUnknown') }
    default:
      return null
  }
})

const metaText = computed(() =>
  [props.step.pieceName, props.step.detail].filter(Boolean).join(' · '))
</script>

<style scoped>
.step-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--el-fill-color-blank);
}

.step-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step-card__title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-card__meta {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-nested {
  margin: 6px 0 6px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-branch {
  border-left: 2px solid var(--el-border-color-light);
  padding-left: 12px;
}

.step-nested__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}

.step-nested__empty {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.step-connector {
  width: 1px;
  height: 14px;
  margin-left: 20px;
  background: var(--el-border-color);
}
</style>

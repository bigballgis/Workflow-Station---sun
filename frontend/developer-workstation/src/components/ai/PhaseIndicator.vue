<template>
  <div class="phase-rail">
    <div
      v-for="(phase, idx) in phases"
      :key="phase.key"
      class="phase-rail__segment"
      :class="{
        'is-done': isDone(phase.key),
        'is-active': phase.key === currentPhase,
        'is-busy': busy && phase.key === currentPhase
      }"
    >
      <span class="phase-rail__num">
        <svg
          v-if="isDone(phase.key)"
          class="phase-rail__check"
          viewBox="0 0 12 12"
          aria-hidden="true"
        >
          <path
            d="M2.5 6.2 5 8.7l4.5-5.4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <template v-else>{{ String(idx + 1).padStart(2, '0') }}</template>
      </span>
      <span class="phase-rail__label">{{ phase.label }}</span>
      <span class="phase-rail__track">
        <span class="phase-rail__track-fill" />
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AiPhase } from '@/types/aiGeneration'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  currentPhase: AiPhase
  completedPhases?: AiPhase[]
  /** streaming 时激活段轨道显示扫光 */
  busy?: boolean
}>(), {
  completedPhases: () => [],
  busy: false
})

const phases = computed(() => [
  { key: 'REQUIREMENTS' as AiPhase, label: t('ai.phase.requirements') },
  { key: 'DESIGN' as AiPhase, label: t('ai.phase.design') },
  { key: 'GENERATION' as AiPhase, label: t('ai.phase.generation') }
])

function isDone(phaseKey: AiPhase): boolean {
  return props.completedPhases.includes(phaseKey)
}
</script>

<style lang="scss" scoped>
@use '@/styles/ai-tokens.scss' as ai;

.phase-rail {
  display: flex;
  gap: 20px;
  padding: 10px 16px 0;
  background: ai.$ai-paper;
  border-bottom: 1px solid ai.$ai-hairline;
}

.phase-rail__segment {
  flex: 1;
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 7px;
  align-items: baseline;
  padding-bottom: 8px;
  position: relative;
  color: ai.$ai-faint;

  &.is-active {
    color: ai.$ai-ink;
  }

  &.is-done {
    color: ai.$ai-graphite;
  }
}

.phase-rail__num {
  @include ai.ai-mono-num;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;

  .is-active & {
    color: ai.$ai-red;
  }

  .is-done & {
    color: ai.$ai-red;
  }
}

.phase-rail__check {
  width: 10px;
  height: 10px;
  display: inline-block;
  vertical-align: -1px;
}

.phase-rail__label {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

// 基线轨道：每段底部一条 2px 细轨
.phase-rail__track {
  grid-column: 1 / -1;
  margin-top: 6px;
  height: 2px;
  border-radius: 1px;
  background: ai.$ai-hairline;
  overflow: hidden;
}

.phase-rail__track-fill {
  display: block;
  height: 100%;
  width: 0;
  border-radius: 1px;
  background: ai.$ai-red;
  transition: width 0.4s ease;

  .is-done & {
    width: 100%;
  }

  .is-active & {
    width: 100%;
    background: linear-gradient(90deg, ai.$ai-red 0%, ai.$ai-red 55%, rgba(219, 0, 17, 0.35) 100%);
  }

  // streaming：激活段轨道扫光
  .is-busy & {
    background: linear-gradient(
      90deg,
      ai.$ai-red 0%,
      #ff5560 45%,
      ai.$ai-red 90%
    );
    background-size: 200% 100%;
    animation: phase-rail-sheen 1.6s linear infinite;
  }
}

@keyframes phase-rail-sheen {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .phase-rail__track-fill {
    transition: none;
    animation: none !important;
  }
}
</style>

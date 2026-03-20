<template>
  <div class="phase-indicator">
    <el-steps :active="activeIndex" finish-status="success" align-center>
      <el-step
        v-for="phase in phases"
        :key="phase.key"
        :title="phase.label"
        :status="getStepStatus(phase.key)"
      />
    </el-steps>
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
}>(), {
  completedPhases: () => []
})

const phases = computed(() => [
  { key: 'REQUIREMENTS' as AiPhase, label: t('ai.phase.requirements') },
  { key: 'DESIGN' as AiPhase, label: t('ai.phase.design') },
  { key: 'GENERATION' as AiPhase, label: t('ai.phase.generation') }
])

const activeIndex = computed(() => {
  const idx = phases.value.findIndex(p => p.key === props.currentPhase)
  return idx >= 0 ? idx : 0
})

function getStepStatus(phaseKey: AiPhase): '' | 'process' | 'finish' | 'success' {
  if (props.completedPhases.includes(phaseKey)) return 'success'
  if (phaseKey === props.currentPhase) return 'process'
  return ''
}
</script>

<style lang="scss" scoped>
.phase-indicator {
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
</style>

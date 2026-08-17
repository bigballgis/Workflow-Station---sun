<template>
  <div
    v-if="enabled"
    class="pk-generation-editor"
  >
    <el-select
      v-model="local.strategy"
      size="small"
      class="pk-strategy-select"
      :placeholder="t('form.pkGenerationStrategy')"
      @change="onStrategyChange"
    >
      <el-option
        v-for="s in PK_GENERATION_STRATEGIES"
        :key="s"
        :label="t(`form.pkGen_${s}`)"
        :value="s"
      />
    </el-select>
    <el-popover
      v-if="showExtra"
      :width="300"
      trigger="click"
      placement="bottom-end"
    >
      <template #reference>
        <el-button
          size="small"
          class="pk-config-btn"
          :type="hasExtraValues ? 'primary' : 'default'"
          plain
        >
          <el-icon><Setting /></el-icon>
        </el-button>
      </template>
      <div class="pk-popover-body">
        <div class="pk-popover-title">
          {{ t('form.pkGenerationSettings') }}
        </div>
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('form.pkGenerationStartValue')">
            <el-input-number
              v-model="local.startValue"
              :min="0"
              controls-position="right"
              style="width: 100%;"
              @change="emitChange"
            />
          </el-form-item>
          <template v-if="showPrefix">
            <el-form-item :label="t('form.pkGenerationPrefix')">
              <el-input
                v-model="local.prefix"
                :placeholder="t('form.pkGenerationPrefixPlaceholder')"
                @input="emitChange"
              />
            </el-form-item>
          </template>
          <template v-if="showPadWidth">
            <el-form-item :label="t('form.pkGenerationPadWidth')">
              <el-input-number
                v-model="local.padWidth"
                :min="1"
                :max="20"
                controls-position="right"
                style="width: 100%;"
                @change="emitChange"
              />
            </el-form-item>
          </template>
        </el-form>
        <div
          v-if="showPreview"
          class="pk-preview"
        >
          {{ t('form.pkGenerationPreview') }}: <code>{{ previewLabel }}</code>
        </div>
      </div>
    </el-popover>
  </div>
  <span
    v-else
    class="text-muted"
  >—</span>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting } from '@element-plus/icons-vue'
import {
  DAILY_DATE_SEQUENCE_DEFAULT_PAD,
  PK_GENERATION_STRATEGIES,
  formatCalendarDateSequencePreview,
  isCalendarDateSequence,
  parsePkGeneration,
  pkGenerationNeedsExtraConfig,
  serializePkGeneration,
  type PkGenerationConfig,
} from '@/utils/pkGenerationConfig'

const props = defineProps<{
  modelValue?: Record<string, unknown> | PkGenerationConfig | null
  enabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown> | undefined]
}>()

const { t } = useI18n()

const enabled = computed(() => props.enabled === true)
const local = reactive(parsePkGeneration(props.modelValue))

const showExtra = computed(() => pkGenerationNeedsExtraConfig(local.strategy))
const showPrefix = computed(() => local.strategy === 'prefixedSequence')
const showPadWidth = computed(() =>
  local.strategy === 'prefixedSequence' || isCalendarDateSequence(local.strategy))
const showPreview = computed(() => showPadWidth.value && !!previewLabel.value)

const hasExtraValues = computed(() => {
  if (local.strategy === 'autoIncrement') {
    return local.startValue != null && local.startValue !== 1
  }
  if (local.strategy === 'prefixedSequence') {
    return !!(local.prefix?.trim()) || local.startValue !== 1 || local.padWidth !== 6
  }
  if (isCalendarDateSequence(local.strategy)) {
    return local.startValue !== 1 || local.padWidth !== DAILY_DATE_SEQUENCE_DEFAULT_PAD
  }
  return false
})

const previewLabel = computed(() => {
  if (isCalendarDateSequence(local.strategy)) {
    return formatCalendarDateSequencePreview(
      local.strategy === 'monthlyDateSequence' ? 'month' : 'day',
      local.padWidth,
      local.startValue,
    )
  }
  if (local.strategy !== 'prefixedSequence') return ''
  const prefix = local.prefix ?? ''
  const pad = local.padWidth ?? 6
  const start = local.startValue ?? 1
  return `${prefix}${String(start).padStart(pad, '0')}`
})

watch(
  () => props.modelValue,
  (val) => {
    Object.assign(local, parsePkGeneration(val))
  },
)

watch(enabled, (on) => {
  if (!on) {
    emit('update:modelValue', undefined)
    return
  }
  if (!props.modelValue) {
    Object.assign(local, parsePkGeneration({ strategy: 'uuid' }))
    emitChange()
  }
})

function onStrategyChange() {
  if (isCalendarDateSequence(local.strategy)) {
    local.padWidth = DAILY_DATE_SEQUENCE_DEFAULT_PAD
    local.prefix = ''
  }
  emitChange()
}

function emitChange() {
  if (!enabled.value) {
    emit('update:modelValue', undefined)
    return
  }
  emit('update:modelValue', serializePkGeneration(local, true))
}
</script>

<style scoped>
.pk-generation-editor {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 168px;
}
.pk-strategy-select {
  flex: 1;
  min-width: 132px;
}
.pk-strategy-select :deep(.el-select__wrapper) {
  min-width: 132px;
}
.pk-strategy-select :deep(.el-select__selected-item) {
  max-width: none;
}
.pk-config-btn {
  flex-shrink: 0;
  padding: 5px 8px;
}
.pk-popover-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.pk-preview {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.pk-preview code {
  color: var(--el-color-primary);
}
.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>

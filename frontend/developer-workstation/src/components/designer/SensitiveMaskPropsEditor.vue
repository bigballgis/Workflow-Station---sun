<template>
  <div class="sensitive-mask-props-editor">
    <div class="sm-row">
      <el-switch
        :model-value="local.enabled"
        @update:model-value="onEnabledChange"
      />
      <span class="sm-label">{{ t('form.sensitiveMask.enabled') }}</span>
    </div>

    <template v-if="local.enabled">
      <div class="sm-field">
        <label class="sm-label">{{ t('form.sensitiveMask.preset') }}</label>
        <el-select
          :model-value="local.preset"
          style="width: 100%"
          @update:model-value="onPresetChange"
        >
          <el-option
            v-for="opt in presetOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>

      <template v-if="local.preset === 'custom'">
        <div class="sm-field sm-inline">
          <label class="sm-label">{{ t('form.sensitiveMask.keepPrefix') }}</label>
          <el-input-number
            :model-value="local.keepPrefix ?? 0"
            :min="0"
            :max="99"
            controls-position="right"
            @update:model-value="(v: number | undefined) => patch({ keepPrefix: v ?? 0 })"
          />
        </div>
        <div class="sm-field sm-inline">
          <label class="sm-label">{{ t('form.sensitiveMask.keepSuffix') }}</label>
          <el-input-number
            :model-value="local.keepSuffix ?? 4"
            :min="0"
            :max="99"
            controls-position="right"
            @update:model-value="(v: number | undefined) => patch({ keepSuffix: v ?? 0 })"
          />
        </div>
        <div class="sm-field">
          <label class="sm-label">{{ t('form.sensitiveMask.maskChar') }}</label>
          <el-input
            :model-value="local.maskChar || '*'"
            maxlength="1"
            @update:model-value="onMaskCharChange"
          />
        </div>
      </template>

      <div class="sm-preview">
        <span class="sm-label">{{ t('form.sensitiveMask.preview') }}</span>
        <code>{{ previewSample }}</code>
      </div>

      <div class="sm-row sm-reveal">
        <el-switch
          :model-value="local.revealPlainOnFocus === true"
          @update:model-value="(v: boolean) => patch({ revealPlainOnFocus: v })"
        />
        <span class="sm-label">{{ t('form.sensitiveMask.revealPlainOnFocus') }}</span>
      </div>
      <div class="sm-hint">
        {{ t('form.sensitiveMask.revealPlainOnFocusHint') }}
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  applySensitiveMask,
  DEFAULT_SENSITIVE_MASK_CONFIG,
  normalizeSensitiveMaskConfig,
  type SensitiveMaskConfig,
  type SensitiveMaskPreset,
} from '@/utils/sensitiveMask'

const SAMPLE = '6222021234567890'

const props = defineProps<{ modelValue?: SensitiveMaskConfig | Record<string, unknown> | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: SensitiveMaskConfig] }>()

const { t } = useI18n()

const local = computed((): SensitiveMaskConfig => {
  return normalizeSensitiveMaskConfig(props.modelValue) ?? { ...DEFAULT_SENSITIVE_MASK_CONFIG }
})

const presetOptions = computed(() => [
  { value: 'last4' as const, label: t('form.sensitiveMask.presets.last4') },
  { value: 'first4Last4' as const, label: t('form.sensitiveMask.presets.first4Last4') },
  { value: 'first3Last4' as const, label: t('form.sensitiveMask.presets.first3Last4') },
  { value: 'all' as const, label: t('form.sensitiveMask.presets.all') },
  { value: 'custom' as const, label: t('form.sensitiveMask.presets.custom') },
])

const previewSample = computed(() => applySensitiveMask(SAMPLE, { ...local.value, enabled: true }))

function emitConfig(next: SensitiveMaskConfig) {
  emit('update:modelValue', next)
}

function patch(partial: Partial<SensitiveMaskConfig>) {
  emitConfig({ ...local.value, ...partial })
}

function onEnabledChange(enabled: boolean) {
  patch({ enabled })
}

function onPresetChange(preset: SensitiveMaskPreset) {
  patch({ preset })
}

function onMaskCharChange(v: string) {
  const ch = (v && v.length > 0) ? v.charAt(0) : '*'
  patch({ maskChar: ch })
}
</script>

<style scoped>
.sensitive-mask-props-editor {
  width: 100%;
  min-width: 180px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sm-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sm-reveal {
  margin-top: 4px;
}

.sm-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sm-inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.sm-label {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}

.sm-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.sm-preview code {
  font-size: 12px;
  word-break: break-all;
  color: #303133;
}

.sm-hint {
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}
</style>

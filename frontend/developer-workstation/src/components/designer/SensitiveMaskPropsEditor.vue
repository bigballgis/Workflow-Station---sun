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

      <template v-if="local.preset === 'ends'">
        <div class="sm-field sm-inline">
          <label class="sm-label">{{ t('form.sensitiveMask.maskPrefix') }}</label>
          <el-input-number
            :model-value="local.maskPrefix ?? 3"
            :min="0"
            :max="99"
            controls-position="right"
            @update:model-value="(v: number | undefined) => patch({ maskPrefix: v ?? 0 })"
          />
        </div>
        <div class="sm-field sm-inline">
          <label class="sm-label">{{ t('form.sensitiveMask.maskSuffix') }}</label>
          <el-input-number
            :model-value="local.maskSuffix ?? 4"
            :min="0"
            :max="99"
            controls-position="right"
            @update:model-value="(v: number | undefined) => patch({ maskSuffix: v ?? 0 })"
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

      <template v-if="local.preset === 'ranges'">
        <div class="sm-hint">
          {{ t('form.sensitiveMask.rangesHint') }}
        </div>
        <div
          v-for="(row, idx) in rangeRows"
          :key="idx"
          class="sm-range-row"
        >
          <el-select
            :model-value="row.side"
            class="sm-range-side"
            @update:model-value="(v: MaskRangeSide) => updateRangeRow(idx, { side: v })"
          >
            <el-option
              :label="t('form.sensitiveMask.rangeFromLeft')"
              value="left"
            />
            <el-option
              :label="t('form.sensitiveMask.rangeFromRight')"
              value="right"
            />
          </el-select>
          <span class="sm-range-mini-label">{{ t('form.sensitiveMask.rangeOffset') }}</span>
          <el-input-number
            :model-value="row.offset"
            :min="0"
            :max="99"
            controls-position="right"
            class="sm-range-num"
            @update:model-value="(v: number | undefined) => updateRangeRow(idx, { offset: v ?? 0 })"
          />
          <span class="sm-range-mini-label">{{ t('form.sensitiveMask.rangeLength') }}</span>
          <el-input-number
            :model-value="row.length"
            :min="0"
            :max="99"
            controls-position="right"
            class="sm-range-num"
            @update:model-value="(v: number | undefined) => updateRangeRow(idx, { length: v ?? 0 })"
          />
          <el-button
            link
            type="danger"
            @click="removeRangeRow(idx)"
          >
            {{ t('form.sensitiveMask.removeRange') }}
          </el-button>
        </div>
        <div class="sm-range-actions">
          <el-button
            size="small"
            @click="addRangeRow"
          >
            {{ t('form.sensitiveMask.addRange') }}
          </el-button>
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
  maskRangeToUiRow,
  normalizeSensitiveMaskConfig,
  uiRowToMaskRange,
  type MaskRangeSide,
  type SensitiveMaskConfig,
  type SensitiveMaskPreset,
  type SensitiveMaskRangeUi,
} from '@/utils/sensitiveMask'

const SAMPLE = '6222021234567890'
const DEFAULT_RANGES = [
  { start: 0, end: 3 },
  { start: -4, end: null },
]

const props = defineProps<{ modelValue?: SensitiveMaskConfig | Record<string, unknown> | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: SensitiveMaskConfig] }>()

const { t } = useI18n()

const local = computed((): SensitiveMaskConfig => {
  return normalizeSensitiveMaskConfig(props.modelValue) ?? { ...DEFAULT_SENSITIVE_MASK_CONFIG }
})

const rangeRows = computed((): SensitiveMaskRangeUi[] => {
  const ranges = local.value.maskRanges ?? []
  if (ranges.length === 0) return []
  return ranges.map(maskRangeToUiRow)
})

/**
 * Designer options stay minimal: "all" for one-click full mask; "ranges" covers
 * every other pattern (keep ends, mask ends, middle-only, etc.).
 * Legacy presets remain runtime-compatible and appear only when already selected.
 */
const DESIGNER_PRESETS: SensitiveMaskPreset[] = ['all', 'ranges']
const LEGACY_PRESETS: SensitiveMaskPreset[] = [
  'last4',
  'first4Last4',
  'first3Last4',
  'ends',
  'custom',
]

const presetOptions = computed(() => {
  const options = DESIGNER_PRESETS.map((value) => ({
    value,
    label: t(`form.sensitiveMask.presets.${value}`),
  }))
  const current = local.value.preset
  if (LEGACY_PRESETS.includes(current)) {
    return [
      { value: current, label: t(`form.sensitiveMask.presets.${current}`) },
      ...options,
    ]
  }
  return options
})

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
  if (preset === 'ends') {
    patch({
      preset,
      maskPrefix: local.value.maskPrefix ?? 3,
      maskSuffix: local.value.maskSuffix ?? 4,
    })
    return
  }
  if (preset === 'ranges') {
    const existing = local.value.maskRanges
    patch({
      preset,
      maskRanges: existing && existing.length > 0 ? existing : DEFAULT_RANGES.map((r) => ({ ...r })),
    })
    return
  }
  patch({ preset })
}

function commitRangeRows(rows: SensitiveMaskRangeUi[]) {
  patch({
    preset: 'ranges',
    maskRanges: rows.map(uiRowToMaskRange),
  })
}

function updateRangeRow(idx: number, partial: Partial<SensitiveMaskRangeUi>) {
  const next = rangeRows.value.map((row, i) => (i === idx ? { ...row, ...partial } : row))
  commitRangeRows(next)
}

function addRangeRow() {
  commitRangeRows([
    ...rangeRows.value,
    { side: 'left', offset: 0, length: 1 },
  ])
}

function removeRangeRow(idx: number) {
  commitRangeRows(rangeRows.value.filter((_, i) => i !== idx))
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

.sm-range-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.sm-range-side {
  width: 110px;
}

.sm-range-mini-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.sm-range-num {
  width: 96px;
}

.sm-range-actions {
  display: flex;
  justify-content: flex-start;
}
</style>

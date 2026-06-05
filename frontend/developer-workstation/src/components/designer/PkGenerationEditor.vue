<template>
  <div
    v-if="enabled"
    class="pk-generation-editor"
    :class="editorClasses"
  >
    <el-popover
      v-if="variant === 'popover'"
      v-model:visible="popoverVisible"
      :width="300"
      trigger="click"
      placement="bottom-end"
      popper-class="pk-generation-popover"
    >
      <template #reference>
        <el-button
          size="small"
          class="constraint-config-btn"
          :type="hasExtraValues ? 'primary' : 'info'"
          plain
        >
          {{ strategyShortLabel }}
        </el-button>
      </template>
      <div
        class="pk-popover-body"
        @click.stop
      >
        <div class="pk-popover-title">
          {{ t('table.pkGeneration') }}
        </div>
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('table.pkGenerationStrategy')">
            <el-select
              v-model="local.strategy"
              style="width: 100%;"
              :teleported="false"
              :placeholder="t('table.pkGenerationStrategy')"
              @change="emitChange"
            >
              <el-option
                v-for="s in PK_GENERATION_STRATEGIES"
                :key="s"
                :label="t(`table.pkGen_${s}`)"
                :value="s"
              />
            </el-select>
          </el-form-item>
          <template v-if="showExtra">
            <el-form-item :label="t('table.pkGenerationStartValue')">
              <el-input-number
                v-model="local.startValue"
                :min="0"
                controls-position="right"
                style="width: 100%;"
                @change="emitChange"
              />
            </el-form-item>
            <template v-if="local.strategy === 'prefixedSequence'">
              <el-form-item :label="t('table.pkGenerationPrefix')">
                <el-input
                  v-model="local.prefix"
                  :placeholder="t('table.pkGenerationPrefixPlaceholder')"
                  @input="emitChange"
                />
              </el-form-item>
              <el-form-item :label="t('table.pkGenerationPadWidth')">
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
          </template>
        </el-form>
        <div
          v-if="local.strategy === 'prefixedSequence' && previewLabel"
          class="pk-preview"
        >
          {{ t('table.pkGenerationPreview') }}: <code>{{ previewLabel }}</code>
        </div>
        <div class="pk-popover-footer">
          <el-button
            size="small"
            type="primary"
            @click="popoverVisible = false"
          >
            {{ t('table.close') }}
          </el-button>
        </div>
      </div>
    </el-popover>

    <template v-else>
      <el-select
        v-model="local.strategy"
        size="small"
        class="pk-strategy-select"
        :placeholder="t('table.pkGenerationStrategy')"
        @change="emitChange"
      >
        <el-option
          v-for="s in PK_GENERATION_STRATEGIES"
          :key="s"
          :label="t(`table.pkGen_${s}`)"
          :value="s"
        />
      </el-select>
      <el-popover
        v-if="showExtra"
        :width="300"
        trigger="click"
        placement="bottom-end"
        popper-class="pk-generation-popover"
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
            {{ t('table.pkGenerationSettings') }}
          </div>
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item :label="t('table.pkGenerationStartValue')">
              <el-input-number
                v-model="local.startValue"
                :min="0"
                controls-position="right"
                style="width: 100%;"
                @change="emitChange"
              />
            </el-form-item>
            <template v-if="local.strategy === 'prefixedSequence'">
              <el-form-item :label="t('table.pkGenerationPrefix')">
                <el-input
                  v-model="local.prefix"
                  :placeholder="t('table.pkGenerationPrefixPlaceholder')"
                  @input="emitChange"
                />
              </el-form-item>
              <el-form-item :label="t('table.pkGenerationPadWidth')">
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
            v-if="local.strategy === 'prefixedSequence' && previewLabel"
            class="pk-preview"
          >
            {{ t('table.pkGenerationPreview') }}: <code>{{ previewLabel }}</code>
          </div>
        </div>
      </el-popover>
    </template>
  </div>
  <span
    v-else
    class="text-muted"
  >—</span>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting } from '@element-plus/icons-vue'
import {
  PK_GENERATION_STRATEGIES,
  parsePkGeneration,
  pkGenerationNeedsExtraConfig,
  serializePkGeneration,
  type PkGenerationConfig,
} from '@/utils/pkGenerationConfig'

const props = defineProps<{
  modelValue?: Record<string, unknown> | PkGenerationConfig | null
  enabled?: boolean
  compact?: boolean
  variant?: 'inline' | 'popover'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown> | undefined]
}>()

const { t } = useI18n()

const popoverVisible = ref(false)
const enabled = computed(() => props.enabled === true)
const compact = computed(() => props.compact === true)
const variant = computed(() => props.variant ?? (compact.value ? 'inline' : 'inline'))
const local = reactive(parsePkGeneration(props.modelValue))

const editorClasses = computed(() => ({
  'pk-generation-editor--compact': compact.value && variant.value !== 'popover',
  'pk-generation-editor--popover': variant.value === 'popover',
}))

const showExtra = computed(() => pkGenerationNeedsExtraConfig(local.strategy))

const strategyShortLabel = computed(() => {
  const map: Record<string, string> = {
    manual: 'Manual',
    uuid: 'UUID',
    autoIncrement: 'Auto',
    prefixedSequence: 'Prefix',
  }
  return map[local.strategy] ?? local.strategy
})

const hasExtraValues = computed(() => {
  if (local.strategy === 'autoIncrement') {
    return local.startValue != null && local.startValue !== 1
  }
  if (local.strategy === 'prefixedSequence') {
    return !!(local.prefix?.trim()) || local.startValue !== 1 || local.padWidth !== 6
  }
  return false
})

const previewLabel = computed(() => {
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
}
.pk-generation-editor--compact {
  min-width: 176px;
}
.pk-generation-editor--compact .pk-strategy-select {
  flex: 1;
  min-width: 156px;
}
.pk-generation-editor--compact .pk-strategy-select :deep(.el-select__wrapper) {
  min-width: 156px;
}
.pk-generation-editor--popover {
  width: auto;
  flex-shrink: 0;
}
.constraint-config-btn {
  padding: 2px 8px;
  height: 24px;
  font-size: 12px;
}
.pk-strategy-select {
  flex: 1;
  min-width: 120px;
}
.pk-config-btn {
  flex-shrink: 0;
  padding: 5px 8px;
}
.pk-popover-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pk-popover-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}
.pk-popover-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.pk-preview {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding-top: 4px;
}
.pk-preview code {
  font-family: ui-monospace, monospace;
  color: var(--el-color-primary);
}
.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>

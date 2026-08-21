<!--
  Mirror: frontend/admin-center/src/components/relation-table/PkGenerationSettingsForm.vue
  Keep Sequence reset + custom format fields in sync; only i18n keys differ (table.* vs form.*).
-->
<template>
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
        @change="emit('change')"
      />
    </el-form-item>
    <el-form-item
      v-if="showPrefix"
      :label="t('table.pkGenerationPrefix')"
    >
      <el-input
        v-model="local.prefix"
        :placeholder="t('table.pkGenerationPrefixPlaceholder')"
        @input="emit('change')"
      />
    </el-form-item>
    <el-form-item
      v-if="showReset"
      :label="t('table.pkGenerationResetPeriod')"
    >
      <el-select
        v-model="local.resetPeriod"
        style="width: 100%;"
        :teleported="false"
        @change="emit('change')"
      >
        <el-option
          :label="t('table.pkReset_none')"
          value="none"
        />
        <el-option
          :label="t('table.pkReset_day')"
          value="day"
          :disabled="!canDailyReset"
        />
        <el-option
          :label="t('table.pkReset_month')"
          value="month"
          :disabled="!canMonthlyReset"
        />
      </el-select>
    </el-form-item>
    <el-form-item
      v-if="showPadWidth"
      :label="t('table.pkGenerationPadWidth')"
    >
      <el-input-number
        v-model="local.padWidth"
        :min="1"
        :max="20"
        controls-position="right"
        style="width: 100%;"
        @change="emit('change')"
      />
    </el-form-item>
    <el-form-item
      v-if="showFormat"
      :label="t('table.pkGenerationFormat')"
    >
      <el-input
        v-model="local.format"
        @input="onFormatInput"
      />
      <el-dropdown
        class="pk-append"
        trigger="click"
        @command="appendSnippet"
      >
        <el-button size="small">
          {{ t('table.pkGenerationAppend') }}
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="seqnum">
              {{ t('table.pkAppend_seqnum') }}
            </el-dropdown-item>
            <el-dropdown-item command="datetime">
              {{ t('table.pkAppend_datetime') }}
            </el-dropdown-item>
            <el-dropdown-item command="randstring">
              {{ t('table.pkAppend_randstring') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-form-item>
  </el-form>
  <div
    v-if="previewLabel"
    class="pk-preview"
  >
    {{ t('table.pkGenerationPreview') }}: <code>{{ previewLabel }}</code>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CUSTOM_FORMAT_SNIPPETS,
  coerceCustomResetPeriod,
  customFormatAllowsDailyReset,
  customFormatAllowsMonthlyReset,
  formatPkGenerationPreview,
  isCalendarDateSequence,
  isCustomFormat,
  type PkGenerationConfig,
} from '@/utils/pkGenerationConfig'

const props = defineProps<{
  local: PkGenerationConfig
}>()

const emit = defineEmits<{
  change: []
}>()

const { t } = useI18n()

const local = computed(() => props.local)
const showPrefix = computed(() => local.value.strategy === 'prefixedSequence')
const showReset = computed(() => isCustomFormat(local.value.strategy))
const showPadWidth = computed(() =>
  local.value.strategy === 'prefixedSequence'
  || isCalendarDateSequence(local.value.strategy))
const showFormat = computed(() => isCustomFormat(local.value.strategy))
const canDailyReset = computed(() => customFormatAllowsDailyReset(local.value.format))
const canMonthlyReset = computed(() => customFormatAllowsMonthlyReset(local.value.format))
const previewLabel = computed(() => formatPkGenerationPreview(local.value))

function onFormatInput() {
  // Designer-only: user edited the template. Parse/serialize keep stored reset otherwise.
  local.value.resetPeriod = coerceCustomResetPeriod(local.value.format, local.value.resetPeriod)
  emit('change')
}

function appendSnippet(command: 'seqnum' | 'datetime' | 'randstring') {
  const snippet = CUSTOM_FORMAT_SNIPPETS[command]
  const current = local.value.format ?? ''
  local.value.format = current + snippet
  onFormatInput()
}
</script>

<style scoped>
.pk-append {
  margin-top: 6px;
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
</style>

<template>
  <span v-if="row.type === 'DIRECT'" class="wizard-hint">—</span>
  <el-input
    v-else-if="row.type === 'LABEL'"
    v-model="row.label"
    size="small"
    placeholder="Case No: "
  />
  <el-input
    v-else-if="row.type === 'CONST'"
    v-model="row.value"
    size="small"
    placeholder="EMAIL"
  />
  <el-input
    v-else-if="row.type === 'HEADER'"
    v-model="row.header"
    size="small"
    placeholder="From"
  />
  <el-popover
    v-else-if="usesRulePopover(row.type)"
    :width="320"
    trigger="click"
    placement="top-start"
    teleported
    :z-index="popoverZIndex"
    :show-arrow="false"
    @show="onPopoverShow"
  >
    <template #reference>
      <div class="rule-summary-trigger" @pointerdown="onPopoverShow">
        <el-input
          :model-value="summary"
          readonly
          size="small"
          class="rule-summary-input"
        />
      </div>
    </template>
    <el-form label-position="top" size="small" class="rule-popover-form" @click.stop>
      <template v-if="row.type === 'BETWEEN'">
        <el-form-item :label="t('emailMonitor.wizard.before')">
          <el-input v-model="row.before" />
        </el-form-item>
        <el-form-item :label="t('emailMonitor.wizard.after')">
          <el-input v-model="row.after" />
        </el-form-item>
      </template>
      <el-form-item v-else :label="t('emailMonitor.wizard.pattern')">
        <el-input
          v-model="row.pattern"
          type="textarea"
          :rows="3"
          placeholder="(\\d+)"
        />
      </el-form-item>
    </el-form>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useZIndex } from 'element-plus'
import type { ExtractionFieldRule } from '@/api/emailMonitor'
import { summarizeMappingRule, usesRulePopover } from '@/composables/email/emailExtractionFieldMapping'

const props = defineProps<{
  row: ExtractionFieldRule
}>()

const { t } = useI18n()
const { nextZIndex } = useZIndex()
const popoverZIndex = ref(4000)

function onPopoverShow(): void {
  popoverZIndex.value = nextZIndex()
}

const summary = computed(() =>
  summarizeMappingRule(props.row, t('emailMonitor.wizard.ruleEmpty')),
)
</script>

<style scoped lang="scss">
.wizard-hint {
  font-size: 12px;
  color: #909399;
}

.rule-summary-trigger {
  width: 100%;
}

.rule-summary-input {
  cursor: pointer;

  :deep(.el-input__inner) {
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.rule-popover-form {
  .el-form-item {
    margin-bottom: 10px;
  }
  .el-form-item:last-child {
    margin-bottom: 0;
  }
}
</style>

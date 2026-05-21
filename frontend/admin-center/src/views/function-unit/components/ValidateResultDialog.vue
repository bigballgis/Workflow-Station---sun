<template>
  <el-dialog
    v-model="visible"
    :title="t('functionUnit.validateResultTitle')"
    width="560px"
    destroy-on-close
  >
    <el-result
      :icon="result?.valid ? 'success' : 'error'"
      :title="result?.valid ? t('functionUnit.validateSuccess') : t('functionUnit.validateFailed')"
    >
      <template #sub-title>
        <span v-if="result?.valid">{{ t('functionUnit.validateSuccessHint') }}</span>
        <span v-else>{{ t('functionUnit.validateFailedHint') }}</span>
      </template>
    </el-result>

    <div v-if="result?.warnings?.length" style="margin-bottom: 16px;">
      <div style="font-weight: 600; margin-bottom: 8px;">{{ t('functionUnit.validateWarnings') }}</div>
      <ul style="margin: 0; padding-left: 20px; color: #e6a23c;">
        <li v-for="(w, i) in result.warnings" :key="i">{{ w }}</li>
      </ul>
    </div>

    <div v-if="result?.errors?.length">
      <div style="font-weight: 600; margin-bottom: 8px;">{{ t('functionUnit.validateErrors') }}</div>
      <el-table :data="result.errors" size="small" border>
        <el-table-column prop="type" :label="t('functionUnit.validateErrorType')" width="140" />
        <el-table-column prop="field" :label="t('functionUnit.validateErrorField')" width="140" />
        <el-table-column prop="message" :label="t('functionUnit.validateErrorMessage')" />
      </el-table>
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FunctionUnitValidationResult } from '@/api/functionUnit'

const props = defineProps<{
  modelValue: boolean
  result: FunctionUnitValidationResult | null
}>()

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})
</script>

<template>
  <el-dialog v-model="visible" :title="t('functionUnit.deployFunctionUnit')" width="500px">
    <el-form label-width="160px" label-position="left">
      <el-form-item :label="t('functionUnit.targetEnvironment')">
        <el-select v-model="deployForm.environment" style="width: 100%">
          <el-option :label="t('functionUnit.envDev')" value="DEV" />
          <el-option :label="t('functionUnit.envTest')" value="TEST" />
          <el-option :label="t('functionUnit.envStaging')" value="STAGING" />
          <el-option :label="t('functionUnit.envProd')" value="PROD" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('functionUnit.deployStrategy')">
        <el-select v-model="deployForm.strategy" style="width: 100%">
          <el-option :label="t('functionUnit.strategyFull')" value="FULL" />
          <el-option :label="t('functionUnit.strategyIncremental')" value="INCREMENTAL" />
          <el-option :label="t('functionUnit.strategyCanary')" value="CANARY" />
          <el-option :label="t('functionUnit.strategyBlueGreen')" value="BLUE_GREEN" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="emit('deploy')">{{ t('functionUnit.confirmDeploy') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  deployForm: { environment: string; strategy: string }
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'deploy': []
}>()

const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })
</script>

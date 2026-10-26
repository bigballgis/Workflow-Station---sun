<template>
  <el-button
    link
    :type="enabled ? 'warning' : 'success'"
    size="small"
    :loading="loading"
    :disabled="!available && !enabled"
    :title="!available && !enabled ? t('bi.dataViewAssignment.viewUnavailable') : undefined"
    @click="change"
  >
    {{ t(enabled ? 'common.disable' : 'common.enable') }}
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { biManagementApi } from '@/api/biManagement'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
const props = withDefaults(defineProps<{ id: string; dashboardTitle: string; enabled: boolean; scope: 'assignment' | 'dataViewAssignment'; available?: boolean }>(), { available: true })
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n()
const loading = ref(false)
async function change(): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    await notifyConfirm(
      t(props.enabled ? 'bi.assignment.confirmDisable' : 'bi.assignment.confirmEnable', { title: props.dashboardTitle }),
      t(props.enabled ? 'common.disable' : 'common.enable'),
      { type: 'warning' },
    )
    await biManagementApi[props.scope].setEnabled(props.id, !props.enabled)
    notifySuccess(t('bi.assignment.statusUpdated'))
    emit('changed')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(t('bi.assignment.statusFailed'))
  } finally {
    loading.value = false
  }
}
</script>

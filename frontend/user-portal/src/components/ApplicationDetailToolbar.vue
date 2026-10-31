<template>
  <div class="application-actions">
    <AssignmentReopenAction :process-id="processId" :status="status" />
    <el-button :icon="Refresh" :loading="loading" @click="$emit('refresh')">{{ t('applicationDetail.refresh') }}</el-button>
  </div>
</template>
<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import AssignmentReopenAction from './AssignmentReopenAction.vue'
defineProps<{ processId: string; status?: string; loading: boolean }>()
defineEmits<{ refresh: [] }>()
const { t } = useI18n()
</script>
<style scoped>
.application-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
</style>

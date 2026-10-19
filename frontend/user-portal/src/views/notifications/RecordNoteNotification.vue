<template>
  <section v-loading="loading" class="record-note-notification">
    <template v-if="destination">
      <el-page-header @back="router.push('/notifications')">
        <template #content>{{ destination.requestTitle || destination.processInstanceId }}</template>
        <template #extra><el-button @click="openRequest">{{ t('recordNoteNotification.openRequest') }}</el-button></template>
      </el-page-header>
      <p>{{ destination.targetType === 'TABLE' ? t('recordNoteNotification.caseNotes') : t('recordNoteNotification.transactionNotes') }}
        <el-tag v-if="destination.targetType === 'RECORD'" class="transaction-reference">{{ destination.targetId }}</el-tag>
      </p>
      <RecordNoteField :table-kind="destination.tableKind" :table-id="destination.tableId"
        :record-id="destination.targetId" :process-instance-id="destination.processInstanceId"
        :focus-submission-id="destination.submissionId" :readonly="true"
        :config="{ scope: destination.targetType, pageSize: 20 }" />
    </template>
    <el-result v-else-if="failed" icon="warning" :title="t('recordNoteNotification.unavailable')" />
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import request from '@/api/request'
import RecordNoteField from '@/components/RecordNoteField.vue'
interface Destination {
  processInstanceId: string; requestTitle: string; targetType: 'TABLE' | 'RECORD'
  targetId: string; tableId: string; tableKind: 'DW' | 'RT'; submissionId: string
}
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const destination = ref<Destination>()
const loading = ref(true)
const failed = ref(false)
function openRequest(): void {
  if (destination.value) void router.push(`/applications/${encodeURIComponent(destination.value.processInstanceId)}`)
}
onMounted(async () => {
  try {
    const result = await request.get<unknown, { data: Destination }>(
      `/record-notes/notifications/${encodeURIComponent(String(route.params.id))}/target`)
    destination.value = result.data
  } catch { failed.value = true } finally { loading.value = false }
})
</script>
<style scoped>
.record-note-notification { padding: 24px; }
.transaction-reference { margin-inline-start: 8px; }
</style>

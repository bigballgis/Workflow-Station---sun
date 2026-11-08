<template>
  <el-dialog
    :model-value="visible"
    :title="t('automation.flowIdentity')"
    width="520px"
    @close="emit('close')"
  >
    <el-form
      v-loading="loading"
      label-width="auto"
    >
      <el-form-item :label="t('automation.publishIdentity')">
        <el-select
          v-model="serviceAccountUserId"
          style="width: 100%"
        >
          <el-option
            :label="t('automation.publishIdentityDefault')"
            :value="WORKSPACE_DEFAULT"
          />
          <el-option
            v-for="account in accounts"
            :key="account.userId"
            :label="account.displayName"
            :value="account.userId"
          />
        </el-select>
        <div class="tip">{{ t('automation.publishIdentityHint') }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!scopeReady"
        @click="save"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  getAutomationScope,
  listAutomationServiceAccounts,
  updateFlowRuntimeIdentity,
  type AutomationServiceAccount,
} from '@/api/automation'

const WORKSPACE_DEFAULT = '__workspace_default__'

const props = defineProps<{ visible: boolean; flowId: string }>()
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

const accounts = ref<AutomationServiceAccount[]>([])
const serviceAccountUserId = ref(WORKSPACE_DEFAULT)
const loading = ref(false)
const saving = ref(false)
const scopeReady = ref(false)

watch(() => props.visible, (open) => {
  if (open) void load()
})

async function load(): Promise<void> {
  loading.value = true
  scopeReady.value = false
  serviceAccountUserId.value = WORKSPACE_DEFAULT
  try {
    const [scope, listed] = await Promise.all([
      getAutomationScope(props.flowId),
      listAutomationServiceAccounts(),
    ])
    accounts.value = listed
    serviceAccountUserId.value = scope.serviceAccountUserId || WORKSPACE_DEFAULT
    scopeReady.value = true
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    accounts.value = []
    ElMessage.error(status === 404 ? t('automation.flowIdentityNeedsScope') : t('automation.runtimeLoadFailed'))
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!scopeReady.value) return
  saving.value = true
  try {
    await updateFlowRuntimeIdentity(
      props.flowId,
      serviceAccountUserId.value === WORKSPACE_DEFAULT ? null : serviceAccountUserId.value,
    )
    ElMessage.success(t('automation.flowIdentitySaved'))
    emit('close')
  } catch (error) {
    const message = (error as { response?: { data?: { error?: { message?: string } } } })
      ?.response?.data?.error?.message
    ElMessage.error(message || t('automation.runtimeSaveFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
</style>

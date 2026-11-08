<template>
  <el-dialog
    :model-value="visible"
    :title="t('automation.publishScopeTitle')"
    width="560px"
    @close="emit('close')"
  >
    <p class="hint">
      {{ t('automation.publishScopeHint', { name: flowName }) }}
      <DesignerHelpLink
        path="/automation-connection"
        :aria-label="t('automation.guideLinkAria')"
        test-id="automation-publish-guide-link"
      />
    </p>
    <el-form label-width="auto">
      <el-form-item :label="t('automation.functionUnit')">
        <el-select
          v-model="functionUnitId"
          filterable
          :loading="loadingUnits"
          style="width: 100%"
        >
          <el-option
            v-for="unit in units"
            :key="unit.id"
            :label="unit.name"
            :value="unit.id"
          />
        </el-select>
        <div class="tip">{{ t('automation.functionUnitHint') }}</div>
      </el-form-item>
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
      <el-form-item :label="t('automation.capabilities')">
        <el-checkbox-group v-model="capabilities">
          <el-checkbox label="read">{{ t('automation.capRead') }}</el-checkbox>
          <el-checkbox label="query">{{ t('automation.capQuery') }}</el-checkbox>
          <el-checkbox label="write">{{ t('automation.capWrite') }}</el-checkbox>
          <el-checkbox label="subtable">{{ t('automation.capSubtable') }}</el-checkbox>
          <el-checkbox label="start">{{ t('automation.capStart') }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :loading="publishing"
        :disabled="functionUnitId == null || capabilities.length === 0"
        @click="confirm"
      >
        {{ t('automation.publish') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import { functionUnitApi, type FunctionUnitResponse } from '@/api/functionUnit'
import {
  getAutomationScope,
  listAutomationServiceAccounts,
  publishAutomationScope,
  type AutomationServiceAccount,
} from '@/api/automation'

const props = defineProps<{ visible: boolean; flowId: string; flowName: string }>()
const emit = defineEmits<{ close: []; published: [] }>()
const { t } = useI18n()

const units = ref<FunctionUnitResponse[]>([])
const accounts = ref<AutomationServiceAccount[]>([])
const WORKSPACE_DEFAULT = '__workspace_default__'
const functionUnitId = ref<number | undefined>()
const serviceAccountUserId = ref(WORKSPACE_DEFAULT)
const capabilities = ref<string[]>(['read', 'query', 'write'])
const loadingUnits = ref(false)
const publishing = ref(false)

watch(() => props.visible, (open) => {
  if (open) void load()
})

async function load(): Promise<void> {
  await Promise.all([loadUnits(), loadAccounts(), loadScope()])
}

async function loadUnits(): Promise<void> {
  loadingUnits.value = true
  try {
    const res = await functionUnitApi.list({ page: 0, size: 100 })
    units.value = res?.data?.content ?? []
  } catch (error) {
    units.value = []
    console.error('[AutomationPublishDialog] function unit list failed', error)
    ElMessage.error(t('automation.publishFailed'))
  } finally {
    loadingUnits.value = false
  }
}

async function loadAccounts(): Promise<void> {
  try {
    accounts.value = await listAutomationServiceAccounts()
  } catch (error) {
    accounts.value = []
    console.error('[AutomationPublishDialog] account list failed', error)
    ElMessage.error(t('automation.publishFailed'))
  }
}

async function loadScope(): Promise<void> {
  serviceAccountUserId.value = WORKSPACE_DEFAULT
  functionUnitId.value = undefined
  capabilities.value = ['read', 'query', 'write']
  try {
    const scope = await getAutomationScope(props.flowId)
    serviceAccountUserId.value = scope.serviceAccountUserId || WORKSPACE_DEFAULT
    if (scope.functionUnitId != null) {
      functionUnitId.value = scope.functionUnitId
    }
    const saved = scope.capabilities?.filter((item) => item.trim() !== '') ?? []
    if (saved.length > 0) {
      capabilities.value = saved
    }
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status !== 404) {
      console.error('[AutomationPublishDialog] scope load failed', error)
    }
  }
}

async function confirm(): Promise<void> {
  if (functionUnitId.value == null) return
  publishing.value = true
  try {
    await publishAutomationScope(
      props.flowId,
      functionUnitId.value,
      capabilities.value,
      serviceAccountUserId.value === WORKSPACE_DEFAULT ? null : serviceAccountUserId.value,
    )
    emit('published')
  } catch (error) {
    const message = (error as { response?: { data?: { error?: { message?: string }; message?: string } } })
      ?.response?.data?.error?.message
      || (error as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(message || t('automation.publishFailed'))
    console.error('[AutomationPublishDialog] scope publish failed', error)
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.hint, .tip { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.hint { margin: 0 0 12px; }
</style>

<template>
  <div class="page-container">
    <div class="panel">
      <div class="title-row">
        <h1>{{ t('automation.runtimeTitle') }}</h1>
        <DesignerHelpLink
          path="/automation-connection"
          :aria-label="t('automation.guideLinkAria')"
          test-id="automation-runtime-guide-link"
        />
      </div>
      <p class="hint">{{ t('automation.runtimeHint') }}</p>
      <el-alert
        v-if="usingDefault"
        type="info"
        :closable="false"
        show-icon
        :title="t('automation.runtimeUsingDefault', { name: displayName })"
      />
      <el-alert
        v-if="readOnly"
        type="warning"
        :closable="false"
        show-icon
        :title="t('automation.runtimeReadOnly')"
      />
      <el-form
        v-loading="loading"
        label-width="auto"
      >
        <el-form-item :label="t('automation.runtimeAccount')">
          <div class="account-row">
            <el-select
              v-if="canChoose"
              v-model="selectedUserId"
              class="account-row__field"
            >
              <el-option
                v-for="account in accounts"
                :key="account.userId"
                :label="account.displayName"
                :value="account.userId"
              />
            </el-select>
            <el-input
              v-else
              class="account-row__field"
              :model-value="displayName"
              disabled
            />
            <el-button
              v-if="canBind"
              @click="openRegister"
            >
              {{ t('automation.registerAccount') }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <el-dialog
        v-model="registerOpen"
        :title="t('automation.registerTitle')"
        width="520px"
      >
        <el-form label-width="auto">
          <el-form-item :label="t('automation.registerUser')">
            <el-select
              v-model="pickedUserId"
              filterable
              remote
              :remote-method="searchUsers"
              :loading="userLoading"
              style="width: 100%"
              @change="onUserPicked"
            >
              <el-option
                v-for="user in userOptions"
                :key="user.id"
                :label="userLabel(user)"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('automation.registerDisplayName')">
            <el-input v-model="registerDisplayName" />
          </el-form-item>
        </el-form>
        <p class="hint">{{ t('automation.registerHint') }}</p>
        <template #footer>
          <el-button @click="registerOpen = false">{{ t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            :loading="registering"
            @click="submitRegister"
          >
            {{ t('automation.registerAccount') }}
          </el-button>
        </template>
      </el-dialog>
      <div class="actions">
        <el-button @click="router.push('/automation')">{{ t('automation.backToList') }}</el-button>
        <el-button
          v-if="canBind"
          :disabled="usingDefault"
          @click="clearBinding"
        >
          {{ t('automation.runtimeUnbind') }}
        </el-button>
        <el-button
          v-if="canBind && canChoose"
          type="primary"
          :loading="saving"
          @click="save"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { debounce } from 'lodash-es'
import { ElMessage } from 'element-plus'
import DesignerHelpLink from '@/components/designer/DesignerHelpLink.vue'
import {
  bindAutomationRuntime,
  fetchServiceTaskSession,
  getAutomationRuntime,
  listAutomationServiceAccounts,
  registerAutomationServiceAccount,
  unbindAutomationRuntime,
  type AutomationServiceAccount,
} from '@/api/automation'
import { userApi, type PlatformUserOption } from '@/api/user'
import { getAutomationWorkspaceId } from '@/utils/automationWorkspace'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const displayName = ref('')
const usingDefault = ref(false)
const selectedUserId = ref('')
const accounts = ref<AutomationServiceAccount[]>([])
const readOnly = ref(false)
const canWrite = ref(false)
const registerOpen = ref(false)
const registering = ref(false)
const userLoading = ref(false)
const pickedUserId = ref('')
const registerDisplayName = ref('')
const userOptions = ref<PlatformUserOption[]>([])

const canBind = computed(() => canWrite.value && !readOnly.value)
const canChoose = computed(() => canBind.value && accounts.value.length > 0)

const searchUsers = debounce((keyword: string) => {
  void loadUsers(keyword)
}, 300)

onMounted(load)
onBeforeUnmount(() => searchUsers.cancel())

function openRegister(): void {
  pickedUserId.value = ''
  registerDisplayName.value = ''
  userOptions.value = []
  registerOpen.value = true
  searchUsers('')
}

async function loadUsers(keyword: string): Promise<void> {
  userLoading.value = true
  try {
    userOptions.value = await userApi.searchUsers(keyword)
  } catch (error) {
    ElMessage.error(messageOf(error) || t('automation.registerFailed'))
    console.error('[AutomationRuntimePage] user search failed', error)
  } finally {
    userLoading.value = false
  }
}

function onUserPicked(userId: string): void {
  const user = userOptions.value.find(item => item.id === userId)
  registerDisplayName.value = user?.displayName || user?.username || ''
}

function userLabel(user: PlatformUserOption): string {
  const name = user.displayName || user.username || user.id
  return user.username ? `${name} (${user.username})` : name
}

async function submitRegister(): Promise<void> {
  const displayNameText = registerDisplayName.value.trim()
  if (!pickedUserId.value || !displayNameText) {
    ElMessage.warning(t('automation.registerRequired'))
    return
  }
  registering.value = true
  try {
    const saved = await registerAutomationServiceAccount(pickedUserId.value, displayNameText)
    accounts.value = [...accounts.value.filter(item => item.userId !== saved.userId), saved]
    selectedUserId.value = saved.userId
    registerOpen.value = false
    ElMessage.success(t('automation.registerSaved'))
  } catch (error) {
    ElMessage.error(messageOf(error) || t('automation.registerFailed'))
  } finally {
    registering.value = false
  }
}

async function load(): Promise<void> {
  const workspaceId = getAutomationWorkspaceId()
  if (!workspaceId) {
    readOnly.value = true
    return
  }
  loading.value = true
  try {
    const session = await fetchServiceTaskSession()
    canWrite.value = session.workspace?.canWrite === true
    const current = await getAutomationRuntime(workspaceId)
    displayName.value = current.displayName
    usingDefault.value = current.usingPlatformDefault === true
    selectedUserId.value = current.userId
    if (canWrite.value) {
      accounts.value = await listAutomationServiceAccounts()
    }
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status
    readOnly.value = status === 403
    if (status !== 403) {
      ElMessage.error(t('automation.runtimeLoadFailed'))
    }
    console.error('[AutomationRuntimePage] load failed', error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const workspaceId = getAutomationWorkspaceId()
  if (!workspaceId || !selectedUserId.value) return
  saving.value = true
  try {
    const saved = await bindAutomationRuntime(workspaceId, selectedUserId.value)
    displayName.value = saved.displayName
    usingDefault.value = false
    ElMessage.success(t('automation.runtimeSaved'))
  } catch (error) {
    ElMessage.error(messageOf(error) || t('automation.runtimeSaveFailed'))
  } finally {
    saving.value = false
  }
}

async function clearBinding(): Promise<void> {
  const workspaceId = getAutomationWorkspaceId()
  if (!workspaceId) return
  saving.value = true
  try {
    const saved = await unbindAutomationRuntime(workspaceId)
    displayName.value = saved.displayName
    usingDefault.value = true
    selectedUserId.value = saved.userId
    ElMessage.success(t('automation.runtimeUnbound'))
  } catch (error) {
    ElMessage.error(messageOf(error) || t('automation.runtimeSaveFailed'))
  } finally {
    saving.value = false
  }
}

function messageOf(error: unknown): string {
  const data = (error as { response?: { data?: { error?: { message?: string }; message?: string } } })?.response?.data
  return data?.error?.message || data?.message || ''
}
</script>

<style scoped>
.panel { background: #fff; border-radius: 14px; padding: 16px 20px 20px; max-width: 640px; }
.title-row { display: flex; align-items: center; gap: 8px; }
.title-row h1 { margin: 0; font-size: 18px; }
.hint { color: var(--el-text-color-secondary); font-size: 13px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
.account-row { display: flex; gap: 8px; width: 100%; }
.account-row__field { flex: 1; }
</style>

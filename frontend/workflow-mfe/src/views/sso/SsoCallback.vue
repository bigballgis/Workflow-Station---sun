<template>
  <div class="sso-callback">
    <p v-if="!workspaceDialogVisible">
      {{ t('common.loading') }}…
    </p>
    <el-dialog
      v-model="workspaceDialogVisible"
      :title="t('login.selectWorkspaceTitle')"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
    >
      <p class="workspace-hint">
        {{ t('login.selectWorkspaceHint') }}
      </p>
      <el-radio-group
        v-model="selectedWorkspaceIndex"
        class="workspace-radio-group"
      >
        <el-radio
          v-for="(c, idx) in workspaceOptions"
          :key="idx"
          :label="idx"
          class="workspace-radio"
        >
          {{ (c.businessUnitName || c.businessUnitId) + ' · ' + (c.roleName || c.roleCode || c.roleId) }}
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button
          type="primary"
          :loading="confirmLoading"
          @click="confirmWorkspace"
        >
          {{ t('login.confirmWorkspace') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  exchangeSsoCode,
  saveTokens,
  saveUser,
  USER_ID_KEY,
  type LoginResponse,
  type WorkspaceContextOption
} from '@/api/auth'
import { consumeSsoReturnPath, redirectToUnifiedLogin } from '@/utils/sso'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const workspaceDialogVisible = ref(false)
const workspaceOptions = ref<WorkspaceContextOption[]>([])
const selectedWorkspaceIndex = ref(0)
const confirmLoading = ref(false)
const authCode = ref('')

function completeLogin(response: LoginResponse) {
  if (!response.accessToken || !response.refreshToken || !response.user) return
  saveTokens(response.accessToken, response.refreshToken)
  saveUser(response.user)
  localStorage.setItem(USER_ID_KEY, response.user.userId)
  const dest =
    response.user?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY' ? '/permissions' : '/dashboard'
  router.replace(consumeSsoReturnPath(dest))
}

async function runExchange(bu?: string, role?: string) {
  const res = await exchangeSsoCode({
    code: authCode.value,
    state: typeof route.query.state === 'string' ? route.query.state : undefined,
    workspaceBusinessUnitId: bu,
    workspaceRoleId: role
  })
  completeLogin(res)
}

async function confirmWorkspace() {
  const opts = workspaceOptions.value
  const idx = selectedWorkspaceIndex.value
  if (!opts.length || idx < 0 || idx >= opts.length) return
  confirmLoading.value = true
  try {
    const c = opts[idx]!
    await runExchange(c.businessUnitId, c.roleId)
    workspaceDialogVisible.value = false
  } catch (e: unknown) {
    ElMessage.error(t('api.requestFailed'))
  } finally {
    confirmLoading.value = false
  }
}

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!code) {
    redirectToUnifiedLogin('portal')
    return
  }
  authCode.value = code
  try {
    await runExchange()
  } catch (e: unknown) {
    const ax = e as { response?: { status?: number; data?: LoginResponse } }
    const status = ax.response?.status
    const data = ax.response?.data
    if (status === 400) {
      if (data?.workspaceContexts?.length) {
        workspaceOptions.value = data.workspaceContexts
        selectedWorkspaceIndex.value = 0
        workspaceDialogVisible.value = true
        return
      }
      // Avoid login redirect loops when backend returns a recoverable 400.
      ElMessage.error(data?.message || t('login.loginFailed'))
      return
    }
    ElMessage.error(t('login.loginFailed'))
    redirectToUnifiedLogin('portal')
  }
})
</script>

<style scoped>
.sso-callback {
  padding: 2rem;
  text-align: center;
}
.workspace-hint {
  margin: 0 0 16px;
  color: #606266;
  font-size: 14px;
}
.workspace-radio-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.workspace-radio {
  margin-right: 0;
  white-space: normal;
  height: auto;
  align-items: flex-start;
}
</style>

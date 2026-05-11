<template>
  <div
    v-if="showBar"
    class="workspace-context-bar"
  >
    <span
      class="ctx-text"
      :title="fullTitle"
    >{{ label }}</span>
    <el-dropdown
      v-if="user?.workspaceSwitcherVisible"
      trigger="click"
      @command="onSwitch"
    >
      <el-button
        type="primary"
        link
        class="ctx-switch"
      >
        {{ t('workspace.switch') }}
        <el-icon class="el-icon--right">
          <ArrowDown />
        </el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="c in contexts"
            :key="`${c.businessUnitId}-${c.roleId}`"
            :command="c"
            :disabled="c.businessUnitId === user?.activeBusinessUnitId && c.roleId === user?.activeRoleId"
          >
            {{ displayOption(c) }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  getUser,
  listWorkspaceContexts,
  switchWorkspace,
  saveTokens,
  saveUser,
  USER_ID_KEY,
  type UserInfo,
  type WorkspaceContextOption
} from '@/api/auth'

const { t } = useI18n()

const user = ref<UserInfo | null>(getUser())
const contexts = ref<WorkspaceContextOption[]>([])

const showBar = computed(() => {
  const u = user.value
  return !!(u?.activeBusinessUnitId && u?.activeRoleId)
})

const label = computed(() => {
  const u = user.value
  if (!u?.activeBusinessUnitId) return ''
  const bu = u.activeBusinessUnitName || u.activeBusinessUnitId
  const role = u.activeRoleName || u.activeRoleId || ''
  return `${bu} · ${role}`
})

const fullTitle = computed(() => label.value)

function displayOption(c: WorkspaceContextOption) {
  const bu = c.businessUnitName || c.businessUnitId
  const r = c.roleName || c.roleCode || c.roleId
  return `${bu} · ${r}`
}

async function loadContexts() {
  if (!user.value?.workspaceSwitcherVisible) return
  try {
    contexts.value = await listWorkspaceContexts()
  } catch {
    contexts.value = []
  }
}

async function onSwitch(c: WorkspaceContextOption) {
  try {
    const resp = await switchWorkspace(c.businessUnitId, c.roleId)
    if (resp.accessToken && resp.refreshToken && resp.user) {
      saveTokens(resp.accessToken, resp.refreshToken)
      saveUser(resp.user)
      user.value = resp.user
      localStorage.setItem(USER_ID_KEY, resp.user.userId)
      ElMessage.success(t('workspace.switched'))
      window.location.reload()
    }
  } catch {
    ElMessage.error(t('workspace.switchFailed'))
  }
}

function syncUser() {
  user.value = getUser()
}

onMounted(() => {
  syncUser()
  loadContexts()
})
</script>

<script lang="ts">
export default {
  name: 'WorkspaceContextBar'
}
</script>

<style scoped lang="scss">
.workspace-context-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 12px;
  color: #fff;
  font-size: 13px;
  max-width: 320px;
}
.ctx-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctx-switch {
  color: #fff !important;
  flex-shrink: 0;
}
</style>

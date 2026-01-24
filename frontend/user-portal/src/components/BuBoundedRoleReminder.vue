<template>
  <el-dialog
    v-model="visible"
    :title="t('reminder.title')"
    width="500px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
  >
    <div class="reminder-content">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>
          <span>{{ t('reminder.message') }}</span>
        </template>
      </el-alert>

      <div class="role-list">
        <div v-for="role in unactivatedRoles" :key="role.id" class="role-item">
          <el-tag type="warning">{{ role.name }}</el-tag>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleRemindLater">{{ t('reminder.remindLater') }}</el-button>
        <el-button @click="handleDontRemind">{{ t('reminder.dontRemind') }}</el-button>
        <el-button type="primary" @click="handleApplyNow">{{ t('reminder.applyNow') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { permissionApi, type RoleInfo } from '@/api/permission'

const { t } = useI18n()
const router = useRouter()

const visible = ref(false)
const unactivatedRoles = ref<RoleInfo[]>([])

const SESSION_KEY = 'bu_reminder_dismissed'

const checkAndShowReminder = async () => {
  // Check if already dismissed in this session
  if (sessionStorage.getItem(SESSION_KEY)) {
    return
  }

  try {
    const res = await permissionApi.shouldShowReminder()
    const data = (res as any).data || res
    if (data && data.shouldShow && data.roles && Array.isArray(data.roles) && data.roles.length > 0) {
      unactivatedRoles.value = data.roles
      visible.value = true
    }
  } catch (e) {
    console.error('Failed to check reminder:', e)
    // Fallback: try to get unactivated roles directly
    try {
      const rolesRes = await permissionApi.getUnactivatedRoles()
      const roles = (rolesRes as any).data || []
      if (Array.isArray(roles) && roles.length > 0) {
        unactivatedRoles.value = roles
        visible.value = true
      }
    } catch (e2) {
      console.error('Fallback also failed:', e2)
    }
  }
}

const handleRemindLater = () => {
  sessionStorage.setItem(SESSION_KEY, 'true')
  visible.value = false
}

const handleDontRemind = async () => {
  try {
    await permissionApi.setDontRemind()
  } catch (e) {
    console.error('Failed to set dont remind preference:', e)
  }
  visible.value = false
}

const handleApplyNow = () => {
  sessionStorage.setItem(SESSION_KEY, 'true')
  visible.value = false
  router.push('/permissions')
}

// Expose method for parent to trigger check
defineExpose({ checkAndShowReminder })

onMounted(() => {
  // Auto check on mount
  checkAndShowReminder()
})
</script>

<style lang="scss" scoped>
.reminder-content {
  .role-list {
    margin-top: 16px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .role-item {
    display: inline-block;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

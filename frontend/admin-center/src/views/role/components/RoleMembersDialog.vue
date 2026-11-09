<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('role.members') + ' - ' + (role?.name || '')"
    width="900px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!-- 角色信息卡片 -->
    <el-card
      shadow="never"
      class="info-card"
    >
      <template #header>
        <div class="card-header">
          <span>{{ t('role.roleInfo') }}</span>
        </div>
      </template>
      <el-descriptions
        :column="3"
        border
        size="small"
      >
        <el-descriptions-item :label="t('role.roleName')">
          {{ role?.name }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('role.roleCode')">
          {{ role?.code }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('role.roleType')">
          <el-tag
            :type="typeTagType(role?.type)"
            size="small"
          >
            {{ typeText(role?.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          :label="t('role.holdersSource')"
          :span="3"
        >
          <span class="text-muted">{{ sourceText }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 持有人列表：来源按角色类型（SYSTEM 组 / 团队名单 / BU 成员资格） -->
    <div class="section-title">
      <span>{{ t('role.memberList') }}</span>
      <span class="member-count">{{ holders.length }} {{ t('role.people') }}</span>
    </div>

    <el-table
      v-loading="loading"
      :data="holders"
      max-height="350"
      stripe
      size="small"
    >
      <el-table-column
        type="index"
        width="50"
        align="center"
      />
      <el-table-column
        prop="employeeId"
        :label="t('user.employeeId')"
        width="100"
      />
      <el-table-column
        prop="username"
        :label="t('user.username')"
        width="120"
      />
      <el-table-column
        prop="fullName"
        :label="t('user.fullName')"
        width="120"
      />
      <el-table-column
        prop="email"
        :label="t('user.email')"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="scopeName"
        :label="scopeLabel"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="status"
        :label="t('common.status')"
        width="100"
      />
    </el-table>

    <el-empty
      v-if="holders.length === 0 && !loading"
      :description="t('common.noData')"
    />

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { roleApi, type Role, type RoleHolder } from '@/api/role'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; role: Role | null }>()
defineEmits(['update:modelValue'])

const loading = ref(false)
const holders = ref<RoleHolder[]>([])

const typeText = (type?: string) => ({
  BU_BOUNDED: t('role.buBounded'),
  ADMIN: t('role.adminRole'),
  AUDITOR: t('role.auditorRole'),
  DEVELOPER: t('role.developerRole')
}[type || ''] || type || '-')

const typeTagType = (type?: string) => ({
  BU_BOUNDED: 'warning',
  ADMIN: 'danger',
  AUDITOR: 'info',
  DEVELOPER: 'primary'
}[type || ''] || 'info') as any

/** Where holders come from, by role type: the backend reads the same source. */
const sourceText = computed(() => {
  switch (props.role?.type) {
    case 'ADMIN':
    case 'AUDITOR':
      return t('role.holdersFromSystemGroup')
    case 'DEVELOPER':
      return t('role.holdersFromTeamLists')
    default:
      return t('role.holdersFromBusinessUnits')
  }
})

const scopeLabel = computed(() => {
  switch (props.role?.type) {
    case 'ADMIN':
    case 'AUDITOR':
      return t('role.holderGroup')
    case 'DEVELOPER':
      return t('role.holderTeam')
    default:
      return t('user.businessUnit')
  }
})

const loadHolders = async () => {
  if (!props.modelValue || !props.role) return
  loading.value = true
  holders.value = []
  try {
    holders.value = await roleApi.getHolders(props.role.id)
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (val) => {
  if (val) loadHolders()
})

watch(() => props.role, () => {
  if (props.modelValue && props.role) loadHolders()
})

const onVisibilityRefresh = () => {
  if (document.visibilityState === 'visible' && props.modelValue && props.role) {
    loadHolders()
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityRefresh)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityRefresh)
})
</script>

<style scoped>
.info-card {
  margin-bottom: 16px;
}

.card-header {
  font-weight: 500;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}

.member-count {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.text-muted {
  color: #909399;
}
</style>

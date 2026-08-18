<template>
  <el-dialog
    v-model="visible"
    :title="t('virtualGroup.boundRole') + ' - ' + (group?.name || '')"
    width="600px"
    @open="fetchRoles"
  >
    <!-- System group warning -->
    <el-alert
      v-if="isSystemGroup"
      :title="t('virtualGroup.systemGroupRoleWarning')"
      type="warning"
      :closable="false"
      style="margin-bottom: 16px"
    />

    <div
      v-if="!readOnly"
      class="roles-header"
    >
      <el-select 
        v-model="selectedRoleId" 
        :placeholder="t('virtualGroup.selectRolePlaceholder')" 
        filterable 
        style="width: 300px"
        :disabled="isSystemGroup"
      >
        <el-option
          v-for="role in availableRoles"
          :key="role.id"
          :label="`${role.name} (${getRoleTypeLabel(role.type)})`"
          :value="role.id"
        />
      </el-select>
      <el-button
        type="primary"
        :disabled="!selectedRoleId || isSystemGroup"
        @click="bindRole"
      >
        {{ boundRole ? t('virtualGroup.replaceRole') : t('virtualGroup.bindRole') }}
      </el-button>
    </div>

    <el-alert
      v-if="boundRole"
      :title="t('virtualGroup.currentBoundRole')"
      type="info"
      :closable="false"
      style="margin-top: 16px"
    >
      <template #default>
        <div class="bound-role-info">
          <span class="role-name">{{ boundRole.roleName }}</span>
          <el-tag
            size="small"
            :type="boundRole.roleType === 'BU_BOUNDED' ? 'warning' : 'success'"
          >
            {{ getRoleTypeLabel(boundRole.roleType) }}
          </el-tag>
          <el-button 
            v-if="!isSystemGroup && !readOnly"
            link 
            type="danger" 
            style="margin-left: 16px" 
            @click="unbindRole"
          >
            {{ t('virtualGroup.unbindRole') }}
          </el-button>
        </div>
      </template>
    </el-alert>

    <el-empty
      v-else
      :description="t('virtualGroup.noRoleBound')"
      style="margin-top: 16px"
    />

    <div
      class="role-type-hint"
      style="margin-top: 16px; color: #909399; font-size: 13px;"
    >
      <p>{{ t('virtualGroup.roleTypeHint') }}</p>
      <ul style="margin: 8px 0 0 20px; padding: 0;">
        <li><strong>{{ t('role.buBounded') }}</strong>: {{ t('virtualGroup.buBoundedHint') }}</li>
        <li><strong>{{ t('role.buUnbounded') }}</strong>: {{ t('virtualGroup.buUnboundedHint') }}</li>
      </ul>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useVirtualGroupRoles } from '@/composables/modules/useVirtualGroupRoles'
import type { VirtualGroup } from '@/api/virtualGroup'

const props = defineProps<{ group: VirtualGroup | null; readOnly?: boolean }>()
const visible = defineModel<boolean>({ default: false })
const { t } = useI18n()

const { boundRole, selectedRoleId, isSystemGroup, availableRoles,
  getRoleTypeLabel, fetchRoles, bindRole, unbindRole }
  = useVirtualGroupRoles(toRef(props, 'group'))

watch(() => props.group, () => { if (visible.value) fetchRoles() })
</script>

<style scoped>
.roles-header {
  display: flex;
  gap: 12px;
  align-items: center;
}
.bound-role-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.role-name {
  font-weight: 500;
}
</style>

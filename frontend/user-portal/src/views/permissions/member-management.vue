<template>
  <div class="member-management-page">
    <div class="page-header">
      <h1>{{ t('memberManagement.title') }}</h1>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane
        :label="t('memberManagement.virtualGroupMembers')"
        name="virtualGroup"
      >
        <div class="portal-card">
          <div class="filter-row">
            <el-select
              v-model="selectedVirtualGroup"
              :placeholder="t('memberManagement.selectVirtualGroup')"
              style="width: 300px"
              filterable
              @change="loadVirtualGroupMembers"
            >
              <el-option
                v-for="group in managedVirtualGroups"
                :key="group.id"
                :label="group.name"
                :value="group.id"
              />
            </el-select>
          </div>

          <el-empty
            v-if="!selectedVirtualGroup"
            :description="t('memberManagement.selectVirtualGroup')"
          />
          <MemberSharedList
            v-else
            :columns="vgColumns"
            :rows="virtualGroupMembers"
            :loading="loadingVG"
            storage-key="portal-list-layout:member-vg"
            :extra-width="VG_ACTIONS_WIDTH"
            :empty-text="t('memberManagement.noMembers')"
            :get-value="vgValue"
          >
            <template #cell="{ column, row }">
              <template v-if="column.field === 'joinedAt'">
                {{ formatDate(row.joinedAt) }}
              </template>
              <template v-else>
                {{ vgValue(row, column.field) || '-' }}
              </template>
            </template>
            <template #action-column>
              <el-table-column
                :label="t('common.actions')"
                :width="VG_ACTIONS_WIDTH"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="removeVGMember(row)"
                  >
                    {{ t('memberManagement.remove') }}
                  </el-button>
                </template>
              </el-table-column>
            </template>
          </MemberSharedList>
        </div>
      </el-tab-pane>

      <el-tab-pane
        :label="t('memberManagement.businessUnitMembers')"
        name="businessUnit"
      >
        <div class="portal-card">
          <div class="filter-row">
            <el-select
              v-model="selectedBusinessUnit"
              :placeholder="t('memberManagement.selectBusinessUnit')"
              style="width: 300px"
              filterable
              @change="loadBusinessUnitMembers"
            >
              <el-option
                v-for="bu in managedBusinessUnits"
                :key="bu.id"
                :label="bu.name"
                :value="bu.id"
              />
            </el-select>
          </div>

          <el-empty
            v-if="!selectedBusinessUnit"
            :description="t('memberManagement.selectBusinessUnit')"
          />
          <MemberSharedList
            v-else
            :columns="buColumns"
            :rows="businessUnitMembers"
            :loading="loadingBU"
            storage-key="portal-list-layout:member-bu"
            :empty-text="t('memberManagement.noMembers')"
            :get-value="buValue"
          >
            <template #cell="{ column, row }">
              <template v-if="column.field === 'roles'">
                <el-tag
                  v-for="role in row.roles"
                  :key="role.id"
                  size="small"
                  style="margin-right: 4px"
                >
                  {{ role.name }}
                  <el-icon
                    class="remove-role-icon"
                    @click.stop="removeBURole(row, role)"
                  >
                    <Close />
                  </el-icon>
                </el-tag>
              </template>
              <template v-else-if="column.field === 'joinedAt'">
                {{ formatDate(row.joinedAt) }}
              </template>
              <template v-else>
                {{ buValue(row, column.field) || '-' }}
              </template>
            </template>
          </MemberSharedList>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'
import { operatorsFor } from '@platform-shared/list/columnMeta'
import MemberSharedList from '@/components/permissions/MemberSharedList.vue'
import { permissionApi, type MemberInfo, type VirtualGroupInfo, type BusinessUnit, type RoleInfo } from '@/api/permission'
import { formatDate } from '@/utils/dateFormat'

const VG_ACTIONS_WIDTH = 100
const { t } = useI18n()

const activeTab = ref('virtualGroup')
const loadingVG = ref(false)
const loadingBU = ref(false)

const managedVirtualGroups = ref<VirtualGroupInfo[]>([])
const managedBusinessUnits = ref<BusinessUnit[]>([])
const selectedVirtualGroup = ref('')
const selectedBusinessUnit = ref('')
const virtualGroupMembers = ref<MemberInfo[]>([])
const businessUnitMembers = ref<MemberInfo[]>([])

const vgColumns: ListColumnMeta[] = [
  { field: 'fullName', label: 'memberManagement.memberName', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'username', label: 'memberManagement.username', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'joinedAt', label: 'memberManagement.joinTime', kind: 'DATETIME', filterable: true, sortable: true, operators: operatorsFor('DATETIME') },
]

const buColumns: ListColumnMeta[] = [
  { field: 'fullName', label: 'memberManagement.memberName', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'username', label: 'memberManagement.username', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'roles', label: 'memberManagement.roles', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'joinedAt', label: 'memberManagement.joinTime', kind: 'DATETIME', filterable: true, sortable: true, operators: operatorsFor('DATETIME') },
]

function vgValue(row: MemberInfo, field: string): unknown {
  if (field === 'fullName') return row.fullName || row.username
  if (field === 'username') return row.username
  if (field === 'joinedAt') return row.joinedAt
  return ''
}

function buValue(row: MemberInfo, field: string): unknown {
  if (field === 'roles') return (row.roles ?? []).map((role) => role.name).join(', ')
  return vgValue(row, field)
}

function unwrapList<T>(res: unknown): T[] {
  const body = res as { data?: { data?: T[] } | T[] } | T[]
  if (Array.isArray(body)) return body
  if (Array.isArray(body.data)) return body.data
  if (body.data && Array.isArray((body.data as { data?: T[] }).data)) {
    return (body.data as { data: T[] }).data
  }
  throw new Error('member list did not return an array')
}

const loadManagedGroups = async () => {
  const res = await permissionApi.getAvailableVirtualGroups()
  managedVirtualGroups.value = unwrapList<VirtualGroupInfo>(res)
}

const loadManagedBusinessUnits = async () => {
  const res = await permissionApi.getBusinessUnits()
  managedBusinessUnits.value = unwrapList<BusinessUnit>(res)
}

const loadVirtualGroupMembers = async () => {
  if (!selectedVirtualGroup.value) return
  loadingVG.value = true
  try {
    const res = await permissionApi.getVirtualGroupMembers(selectedVirtualGroup.value)
    virtualGroupMembers.value = unwrapList<MemberInfo>(res)
  } catch (e: unknown) {
    virtualGroupMembers.value = []
    if (!(e as { response?: unknown })?.response) {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.loadFailed'))
    }
  } finally {
    loadingVG.value = false
  }
}

const loadBusinessUnitMembers = async () => {
  if (!selectedBusinessUnit.value) return
  loadingBU.value = true
  try {
    const res = await permissionApi.getBusinessUnitMembers(selectedBusinessUnit.value)
    businessUnitMembers.value = unwrapList<MemberInfo>(res)
  } catch (e: unknown) {
    businessUnitMembers.value = []
    if (!(e as { response?: unknown })?.response) {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.loadFailed'))
    }
  } finally {
    loadingBU.value = false
  }
}

const removeVGMember = async (member: MemberInfo) => {
  try {
    await ElMessageBox.confirm(t('memberManagement.removeConfirm'), t('common.confirm'))
    await permissionApi.removeVirtualGroupMember(selectedVirtualGroup.value, member.userId)
    ElMessage.success(t('memberManagement.removeSuccess'))
    await loadVirtualGroupMembers()
  } catch (e: unknown) {
    if (e !== 'cancel') {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.removeFailed'))
    }
  }
}

const removeBURole = async (member: MemberInfo, role: RoleInfo) => {
  try {
    await ElMessageBox.confirm(t('memberManagement.removeConfirm'), t('common.confirm'))
    await permissionApi.removeBusinessUnitRole(selectedBusinessUnit.value, member.userId, role.id)
    ElMessage.success(t('memberManagement.removeSuccess'))
    await loadBusinessUnitMembers()
  } catch (e: unknown) {
    if (e !== 'cancel') {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.removeFailed'))
    }
  }
}

onMounted(() => {
  void loadManagedGroups().catch((e: unknown) => {
    if (!(e as { response?: unknown })?.response) {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.loadFailed'))
    }
  })
  void loadManagedBusinessUnits().catch((e: unknown) => {
    if (!(e as { response?: unknown })?.response) {
      ElMessage.error(e instanceof Error ? e.message : t('memberManagement.loadFailed'))
    }
  })
})
</script>

<style lang="scss" scoped>
.member-management-page {
  .page-header {
    margin-bottom: 20px;
    h1 { font-size: 24px; font-weight: 500; margin: 0; }
  }
  .filter-row {
    margin-bottom: 20px;
  }
  .remove-role-icon {
    margin-left: 4px;
    cursor: pointer;
    &:hover { color: var(--el-color-danger); }
  }
}
</style>

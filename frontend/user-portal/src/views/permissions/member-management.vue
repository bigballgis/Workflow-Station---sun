<template>
  <div class="member-management-page">
    <div class="page-header">
      <h1>{{ t('memberManagement.title') }}</h1>
    </div>

    <!-- Virtual groups are not available in the portal (SYS_ADMIN maintains them in Admin Center). -->
    <el-tabs model-value="businessUnit">
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
                <!-- The tag's own close button keeps the remove action on the role's line. -->
                <el-tag
                  v-for="role in row.roles"
                  :key="role.id"
                  size="small"
                  closable
                  class="member-role-tag"
                  @close="removeBURole(row, role)"
                >
                  {{ role.name }}
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
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'
import { operatorsFor } from '@platform-shared/list/columnMeta'
import MemberSharedList from '@/components/permissions/MemberSharedList.vue'
import { permissionApi, type MemberInfo, type BusinessUnit, type RoleInfo } from '@/api/permission'
import { formatDate } from '@/utils/dateFormat'

const { t } = useI18n()

const loadingBU = ref(false)

const managedBusinessUnits = ref<BusinessUnit[]>([])
const selectedBusinessUnit = ref('')
const businessUnitMembers = ref<MemberInfo[]>([])

const buColumns: ListColumnMeta[] = [
  { field: 'fullName', label: 'memberManagement.memberName', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'username', label: 'memberManagement.username', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'roles', label: 'memberManagement.roles', kind: 'TEXT', filterable: true, sortable: true, operators: operatorsFor('TEXT') },
  { field: 'joinedAt', label: 'memberManagement.joinTime', kind: 'DATETIME', filterable: true, sortable: true, operators: operatorsFor('DATETIME') },
]

function buValue(row: MemberInfo, field: string): unknown {
  if (field === 'roles') return (row.roles ?? []).map((role) => role.name).join(', ')
  if (field === 'fullName') return row.fullName || row.username
  if (field === 'username') return row.username
  if (field === 'joinedAt') return row.joinedAt
  return ''
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

const loadManagedBusinessUnits = async () => {
  const res = await permissionApi.getBusinessUnits()
  managedBusinessUnits.value = unwrapList<BusinessUnit>(res)
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
  .member-role-tag {
    margin-right: 4px;
  }
}
</style>

<template>
  <div
    v-if="showBar"
    class="dev-group-bar"
  >
    <span
      class="ctx-text"
      :title="currentName"
    >{{ t('devGroup.current') }}: {{ currentName }}</span>
    <el-dropdown
      v-if="switchOptions.length > 1"
      trigger="click"
      @command="onSwitch"
    >
      <el-button
        type="primary"
        link
        class="ctx-switch"
      >
        {{ t('devGroup.switch') }}
        <el-icon class="el-icon--right">
          <ArrowDown />
        </el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="opt in switchOptions"
            :key="opt.id"
            :command="opt.id"
            :disabled="isOptionDisabled(opt)"
          >
            {{ formatOptionLabel(opt) }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>

  <!-- Entry team selection (mirrors User Portal workspace selection) -->
  <el-dialog
    v-model="selectDialogVisible"
    :title="t('devGroup.selectTitle')"
    width="480px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    append-to-body
  >
    <p class="dev-group-hint">
      {{ t('devGroup.selectHint') }}
    </p>
    <el-radio-group
      v-model="pendingGroupId"
      class="dev-group-radio-group"
    >
      <el-radio
        v-for="g in selectableGroupsForDialog"
        :key="g.id"
        :label="g.id"
        :disabled="!isGroupActive(g)"
        class="dev-group-radio"
      >
        {{ formatOptionLabel(g) }}
      </el-radio>
    </el-radio-group>
    <template #footer>
      <el-button
        type="primary"
        :disabled="!pendingGroupId || !isPendingSelectable"
        @click="confirmSelection"
      >
        {{ t('devGroup.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import { functionUnitApi, type DevGroupOption } from '@/api/functionUnit'
import { ALL_GROUPS, getActiveGroupRaw, setActiveGroup } from '@/utils/devGroupContext'

const { t } = useI18n()
const emit = defineEmits<{ ready: [] }>()

const groups = ref<DevGroupOption[]>([])
const canSeeAll = ref(false)
const publicGroupId = ref<string | null>(null)
const currentId = ref<string | null>(null)
const selectDialogVisible = ref(false)
const pendingGroupId = ref<string>('')

function isGroupActive(opt: DevGroupOption): boolean {
  return !opt.status || opt.status === 'ACTIVE'
}

function formatOptionLabel(opt: DevGroupOption): string {
  if (isGroupActive(opt)) return opt.name
  return `${opt.name} (${t('devGroup.inactive')})`
}

function isOptionDisabled(opt: DevGroupOption): boolean {
  if (opt.id === currentId.value) return true
  if (opt.id === ALL_GROUPS || opt.id === publicGroupId.value) return false
  return !isGroupActive(opt)
}

// Options shown in the header switcher: "All groups" (admin only), Public, and the user's teams.
const switchOptions = computed<DevGroupOption[]>(() => {
  const opts: DevGroupOption[] = []
  if (canSeeAll.value) {
    opts.push({ id: ALL_GROUPS, name: t('devGroup.allGroups'), status: 'ACTIVE' })
  }
  if (publicGroupId.value) {
    opts.push({ id: publicGroupId.value, name: t('devGroup.publicGroup'), status: 'ACTIVE' })
  }
  opts.push(...groups.value)
  return opts
})

const selectableGroupsForDialog = computed(() => groups.value)

const activeGroups = computed(() => groups.value.filter(isGroupActive))

const isPendingSelectable = computed(() => {
  const g = groups.value.find((x) => x.id === pendingGroupId.value)
  return Boolean(g && isGroupActive(g))
})

const showBar = computed(() => canSeeAll.value || Boolean(publicGroupId.value) || groups.value.length > 0)

const currentName = computed(() => {
  if (currentId.value === ALL_GROUPS) return t('devGroup.allGroups')
  if (currentId.value === publicGroupId.value) return t('devGroup.publicGroup')
  const match = groups.value.find(g => g.id === currentId.value)
  if (match) return formatOptionLabel(match)
  return t('devGroup.noTeam')
})

function applyAndReload(groupId: string) {
  setActiveGroup(groupId)
  window.location.reload()
}

function onSwitch(groupId: string) {
  if (groupId === currentId.value) return
  const opt = switchOptions.value.find((o) => o.id === groupId)
  if (opt && isOptionDisabled(opt)) return
  applyAndReload(groupId)
}

function confirmSelection() {
  if (!pendingGroupId.value || !isPendingSelectable.value) return
  selectDialogVisible.value = false
  applyAndReload(pendingGroupId.value)
}

async function resolveContext() {
  try {
    const res = await functionUnitApi.getMyDevGroups()
    groups.value = res?.data?.groups ?? []
    canSeeAll.value = res?.data?.canSeeAllGroups === true
    publicGroupId.value = res?.data?.publicGroupId || null
  } catch {
    groups.value = []
    canSeeAll.value = false
    publicGroupId.value = null
    emit('ready')
    return
  }

  const stored = getActiveGroupRaw()
  // Only ACTIVE teams (plus All/Public) are valid as the current workspace.
  const validIds = new Set<string>(activeGroups.value.map(g => g.id))
  if (canSeeAll.value) validIds.add(ALL_GROUPS)
  if (publicGroupId.value) validIds.add(publicGroupId.value)

  if (stored && validIds.has(stored)) {
    currentId.value = stored
    emit('ready')
    return
  }

  // No valid stored selection — decide default or prompt.
  if (canSeeAll.value) {
    currentId.value = ALL_GROUPS
    setActiveGroup(ALL_GROUPS)
    emit('ready')
  } else if (activeGroups.value.length === 1) {
    currentId.value = activeGroups.value[0]!.id
    setActiveGroup(currentId.value)
    emit('ready')
  } else if (activeGroups.value.length === 0) {
    currentId.value = publicGroupId.value
    if (currentId.value) setActiveGroup(currentId.value)
    emit('ready')
  } else {
    // Multiple active teams and no prior choice → force a selection.
    pendingGroupId.value = activeGroups.value[0]!.id
    selectDialogVisible.value = true
  }
}

onMounted(resolveContext)
</script>

<script lang="ts">
export default {
  name: 'DevGroupContextBar'
}
</script>

<style scoped lang="scss">
.dev-group-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 13px;
  max-width: 320px;
}
.ctx-text {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctx-switch {
  color: #fff !important;
  flex-shrink: 0;
}
.dev-group-hint {
  margin: 0 0 16px;
  color: #606266;
  font-size: 14px;
}
.dev-group-radio-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.dev-group-radio {
  margin-right: 0;
  white-space: normal;
  height: auto;
  align-items: flex-start;
}
</style>

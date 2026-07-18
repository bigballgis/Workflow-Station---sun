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
            :disabled="opt.id === currentId"
          >
            {{ opt.name }}
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
        v-for="g in groups"
        :key="g.id"
        :label="g.id"
        class="dev-group-radio"
      >
        {{ g.name }}
      </el-radio>
    </el-radio-group>
    <template #footer>
      <el-button
        type="primary"
        :disabled="!pendingGroupId"
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

const groups = ref<DevGroupOption[]>([])
const canSeeAll = ref(false)
const currentId = ref<string | null>(null)
const selectDialogVisible = ref(false)
const pendingGroupId = ref<string>('')

// Options shown in the header switcher: "All groups" (admin only) + the user's teams.
const switchOptions = computed<DevGroupOption[]>(() => {
  const opts: DevGroupOption[] = []
  if (canSeeAll.value) {
    opts.push({ id: ALL_GROUPS, name: t('devGroup.allGroups') })
  }
  opts.push(...groups.value)
  return opts
})

const showBar = computed(() => canSeeAll.value || groups.value.length > 0)

const currentName = computed(() => {
  if (currentId.value === ALL_GROUPS) return t('devGroup.allGroups')
  const match = groups.value.find(g => g.id === currentId.value)
  if (match) return match.name
  return t('devGroup.noTeam')
})

function applyAndReload(groupId: string) {
  setActiveGroup(groupId)
  window.location.reload()
}

function onSwitch(groupId: string) {
  if (groupId === currentId.value) return
  applyAndReload(groupId)
}

function confirmSelection() {
  if (!pendingGroupId.value) return
  selectDialogVisible.value = false
  applyAndReload(pendingGroupId.value)
}

async function resolveContext() {
  try {
    const res = await functionUnitApi.getMyDevGroups()
    groups.value = res?.data?.groups ?? []
    canSeeAll.value = res?.data?.canSeeAllGroups === true
  } catch {
    groups.value = []
    canSeeAll.value = false
    return
  }

  const stored = getActiveGroupRaw()
  const validIds = new Set<string>(groups.value.map(g => g.id))
  if (canSeeAll.value) validIds.add(ALL_GROUPS)

  if (stored && validIds.has(stored)) {
    currentId.value = stored
    return
  }

  // No valid stored selection — decide default or prompt.
  if (canSeeAll.value) {
    currentId.value = ALL_GROUPS
    setActiveGroup(ALL_GROUPS)
  } else if (groups.value.length === 1) {
    currentId.value = groups.value[0]!.id
    setActiveGroup(currentId.value)
  } else if (groups.value.length === 0) {
    currentId.value = null
  } else {
    // Multiple teams and no prior choice → force a selection.
    pendingGroupId.value = groups.value[0]!.id
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

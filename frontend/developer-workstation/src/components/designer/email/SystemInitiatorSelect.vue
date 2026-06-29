<template>
  <el-select
    :model-value="modelValue || undefined"
    filterable
    remote
    clearable
    reserve-keyword
    :placeholder="t('emailMonitor.systemInitiatorPlaceholder')"
    :remote-method="searchUsers"
    :loading="loading"
    style="width: 100%;"
    @update:model-value="emit('update:modelValue', ($event as string) || '')"
    @focus="loadDefaultUsers"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="user in options"
      :key="user.id"
      :label="optionLabel(user)"
      :value="user.id"
    >
      <div class="initiator-option">
        <span class="initiator-option__name">{{ user.displayName }}</span>
        <span v-if="user.email" class="initiator-option__email">{{ user.email }}</span>
        <span v-else class="initiator-option__email">{{ user.username }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { userApi, type PlatformUserOption } from '@/api/user'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()
const loading = ref(false)
const options = ref<PlatformUserOption[]>([])

function optionLabel(user: PlatformUserOption): string {
  const name = user.displayName || user.username || user.id
  const secondary = user.email || user.username
  return secondary ? `${name} (${secondary})` : name
}

function mergeOption(user: PlatformUserOption) {
  if (!options.value.some(o => o.id === user.id)) {
    options.value = [user, ...options.value]
  }
}

async function loadDefaultUsers() {
  if (options.value.length > 0) return
  await searchUsers('')
}

async function searchUsers(keyword: string) {
  loading.value = true
  try {
    options.value = await userApi.searchUsers(keyword)
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

async function ensureSelectedUserLoaded(userId?: string) {
  const id = userId?.trim()
  if (!id) return
  if (options.value.some(u => u.id === id)) return
  const user = await userApi.getUserById(id)
  if (user) mergeOption(user)
}

function onVisibleChange(visible: boolean) {
  if (visible) {
    void ensureSelectedUserLoaded(props.modelValue)
  }
}

watch(
  () => props.modelValue,
  id => { void ensureSelectedUserLoaded(id) },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.initiator-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  padding: 2px 0;

  &__name {
    font-size: 13px;
    color: var(--el-text-color-primary);
  }

  &__email {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="500px"
    class="task-action-dialog"
    @opened="$emit('opened')"
  >
    <el-form
      :model="formData"
      label-width="120px"
      label-position="left"
      class="task-action-form"
    >
      <el-form-item
        v-show="currentAction !== 'urge'"
        :label="$t('task.targetUser')"
      >
        <el-select
          v-model="formData.targetUserId"
          :placeholder="$t('task.selectUser')"
          :teleported="false"
          style="width: 100%;"
        >
          <el-option
            v-for="user in userOptions"
            :key="user.id"
            :label="$t('task.userOptionFormat', { name: user.name, username: user.username })"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="currentAction === 'urge' ? $t('task.urgeMessage') : $t('task.reasonDescription')"
        class="task-action-reason-item"
      >
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="5"
          :placeholder="currentAction === 'urge' ? $t('task.urgeMessagePlaceholder') : $t('task.reasonPlaceholder')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        {{ $t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="$emit('confirm')"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface UserOption {
  id: string | number
  name: string
  username: string
}

const props = defineProps<{
  modelValue: boolean
  title: string
  currentAction: string
  formData: { targetUserId: string; reason: string }
  userOptions: UserOption[]
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'opened'): void
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})
</script>

<style lang="scss" scoped>
/* Styles previously in global unscoped block — now scoped to this component */
:deep(.task-action-form) {
  .el-form-item__label {
    white-space: nowrap;
    text-align: left;
  }

  .task-action-reason-item .el-textarea__inner {
    min-height: 100px;
    font-size: 14px;
  }
}
</style>

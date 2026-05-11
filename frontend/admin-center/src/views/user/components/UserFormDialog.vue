<template>
  <el-dialog 
    :model-value="modelValue" 
    :title="isEdit ? t('user.editUser') : t('user.createUser')" 
    width="560px" 
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="auto"
      label-position="left"
    >
      <el-form-item
        :label="t('user.username')"
        prop="username"
      >
        <el-input
          v-model="form.username"
          :disabled="isEdit"
          :placeholder="t('user.usernamePlaceholder')"
        />
      </el-form-item>
      <el-form-item
        :label="t('user.fullName')"
        prop="fullName"
      >
        <el-input
          v-model="form.fullName"
          :placeholder="t('user.fullNamePlaceholder')"
        />
      </el-form-item>
      <el-form-item
        :label="t('user.email')"
        prop="email"
      >
        <el-input
          v-model="form.email"
          :placeholder="t('user.emailPlaceholder')"
        />
      </el-form-item>
      <el-form-item
        :label="t('user.employeeId')"
        prop="employeeId"
      >
        <el-input
          v-model="form.employeeId"
          :placeholder="t('user.employeeIdPlaceholder')"
        />
      </el-form-item>
      <el-form-item
        :label="t('user.position')"
        prop="position"
      >
        <el-input
          v-model="form.position"
          :placeholder="t('user.positionPlaceholder')"
        />
      </el-form-item>
      <el-form-item
        :label="t('user.entityManager')"
        prop="entityManagerId"
      >
        <el-select 
          v-model="form.entityManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          :placeholder="t('user.entityManagerPlaceholder')"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('user.functionManager')"
        prop="functionManagerId"
      >
        <el-select 
          v-model="form.functionManagerId" 
          filterable 
          remote 
          :remote-method="searchUsers"
          clearable 
          :placeholder="t('user.functionManagerPlaceholder')"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option 
            v-for="user in userOptions" 
            :key="user.id" 
            :label="`${user.fullName} (${user.username})`" 
            :value="user.id" 
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="!isEdit"
        :label="t('user.initialPassword')"
        prop="initialPassword"
      >
        <el-input
          v-model="form.initialPassword"
          type="password"
          show-password
          :placeholder="t('user.initialPasswordPlaceholder')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="handleSubmit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useUserForm } from '@/composables/modules/useUserForm'

const props = defineProps<{ modelValue: boolean; user: any | null }>()
const emit = defineEmits(['update:modelValue', 'success'])
const { t } = useI18n()
const formRef = ref<FormInstance>()

const { form, rules, loading, userSearchLoading, userOptions, isEdit, initForm, searchUsers, submit }
  = useUserForm({ user: toRef(props, 'user'), onSuccess: () => { emit('update:modelValue', false); emit('success') } })

watch(() => props.modelValue, async (val) => { if (val) await initForm() })

const handleSubmit = async () => {
  if (!await formRef.value?.validate()) return
  await submit()
}
</script>

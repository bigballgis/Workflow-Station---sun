<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? t('virtualGroup.edit') : t('virtualGroup.create')"
    width="560px"
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
        :label="t('common.name')"
        prop="name"
      >
        <el-input
          v-model="form.name"
          :disabled="nameLocked"
        />
      </el-form-item>
      <!-- A team code is made from the name (platform access model 5.4): no input when creating a team -->
      <el-form-item
        v-if="isEdit || form.type === 'CUSTOM'"
        :label="t('common.code')"
        prop="code"
      >
        <el-input
          v-model="form.code"
          :disabled="isEdit"
        />
      </el-form-item>
      <el-form-item
        :label="t('common.type')"
        prop="type"
      >
        <el-select
          v-model="form.type"
          :disabled="isEdit"
        >
          <el-option
            :label="t('virtualGroup.typeCustom')"
            value="CUSTOM"
          />
          <el-option
            :label="t('virtualGroup.typeDeveloper')"
            value="DEVELOPER"
          />
          <el-option
            v-if="isSystemGroup"
            :label="t('virtualGroup.typeSystem')"
            value="SYSTEM"
          />
        </el-select>
      </el-form-item>
      <el-alert
        v-if="isTeam && !isEdit"
        :title="t('virtualGroup.teamCodeHint')"
        type="info"
        :closable="false"
        show-icon
        class="form-hint"
      />
      <el-alert
        v-if="isPublicTeam"
        :title="t('virtualGroup.publicNoAdGroup')"
        type="info"
        :closable="false"
        show-icon
        class="form-hint"
      />
      <template v-else>
        <el-form-item :label="isTeam ? t('virtualGroup.developerAdGroup') : t('virtualGroup.adGroup')">
          <el-input
            v-model="form.adGroup"
            :placeholder="t('virtualGroup.adGroupPlaceholder')"
          />
        </el-form-item>
        <el-form-item
          v-if="isTeam"
          :label="t('virtualGroup.techLeadAdGroup')"
        >
          <el-input
            v-model="form.techLeadAdGroup"
            :placeholder="t('virtualGroup.adGroupPlaceholder')"
          />
        </el-form-item>
      </template>
      <el-form-item :label="t('common.description')">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          :disabled="isSystemGroup"
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
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, FormInstance } from 'element-plus'
import { virtualGroupApi, type VirtualGroup } from '@/api/virtualGroup'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; group: VirtualGroup | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [createdType?: 'CUSTOM' | 'DEVELOPER' | 'SYSTEM']
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isEdit = computed(() => !!props.group)
const isSystemGroup = computed(() => props.group?.type === 'SYSTEM')
const isPublicTeam = computed(() => !!props.group?.publicTeam)

const form = reactive({
  name: '',
  code: '',
  type: 'CUSTOM' as 'CUSTOM' | 'DEVELOPER' | 'SYSTEM',
  adGroup: '',
  techLeadAdGroup: '',
  description: '',
})

const isTeam = computed(() => form.type === 'DEVELOPER')
// Type never changes; SYSTEM groups and teams keep their name (5.2, 5.4).
const nameLocked = computed(() => isEdit.value && form.type !== 'CUSTOM')

const rules = computed(() => ({
  name: [{ required: true, message: t('common.inputPlaceholder'), trigger: 'blur' }],
  code: [{ required: !isEdit.value && form.type === 'CUSTOM', message: t('common.inputPlaceholder'), trigger: 'blur' }],
  type: [{ required: true, message: t('common.selectPlaceholder'), trigger: 'change' }]
}))

watch(() => props.modelValue, (val) => {
  if (val && props.group) {
    Object.assign(form, {
      name: props.group.name,
      code: props.group.code,
      type: props.group.type,
      adGroup: props.group.adGroup || '',
      techLeadAdGroup: props.group.techLeadAdGroup || '',
      description: props.group.description || '',
    })
  } else if (val) {
    Object.assign(form, { name: '', code: '', type: 'CUSTOM', adGroup: '', techLeadAdGroup: '', description: '' })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    const data = {
      name: form.name,
      type: form.type,
      description: form.description,
      adGroup: isPublicTeam.value ? undefined : form.adGroup || undefined,
      techLeadAdGroup: isTeam.value && !isPublicTeam.value ? form.techLeadAdGroup || undefined : undefined,
    }
    if (isEdit.value) {
      await virtualGroupApi.update(props.group!.id, data)
    } else {
      await virtualGroupApi.create(form.type === 'CUSTOM' ? { ...data, code: form.code } : data)
    }
    ElMessage.success(t('common.success'))
    emit('update:modelValue', false)
    emit('success', form.type)
  } catch (e) {
    console.error('Failed to save virtual group:', e)
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-hint {
  margin-bottom: 18px;
}
</style>

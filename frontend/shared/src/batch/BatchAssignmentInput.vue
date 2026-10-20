<template>
  <el-select v-if="rule.field === config.assigneeField" v-bind="rule.props" v-model="uniform.assigneeId" @update:model-value="value => emit('change', value)" :style="rule.style"
    :disabled="disabled || rule.props?.disabled || rule.props?.readonly" filterable remote :remote-method="query => emit('search', query)" :loading="userLoading">
    <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
  </el-select>
  <el-cascader v-else-if="rule.field === config.buField" v-bind="rule.props" :style="rule.style" v-model="uniform.businessUnitId"
    :disabled="disabled || rule.props?.disabled || rule.props?.readonly" :options="directory.businessUnits.value"
    :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }"
    @change="value => emit('business-unit', String(value ?? ''))" />
  <el-select v-else-if="rule.field === config.roleField" v-bind="rule.props" :style="rule.style" v-model="uniform.roleCode" @update:model-value="value => emit('change', value)"
    :disabled="disabled || rule.props?.disabled || rule.props?.readonly || (!!config.buField && !uniform.businessUnitId)" :loading="roleLoading">
    <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
  </el-select>
</template>
<script setup lang="ts">
import type { BatchAssignmentConfig } from './workspaceTypes'
import type { BatchUniformAssignmentState } from './assignmentDraft'
import type { BatchAssignmentDirectory } from './assignmentDirectoryTypes'
defineProps<{
  rule: Record<string, any>; config: BatchAssignmentConfig; uniform: BatchUniformAssignmentState; disabled?: boolean
  directory: BatchAssignmentDirectory; userOptions: Array<{ id: string; name: string }>; userLoading: boolean
  roleOptions: Array<{ label: string; value: string }>; roleLoading: boolean
}>()
const emit = defineEmits<{ (event: 'change', value: unknown): void; (event: 'search', query: string): void; (event: 'business-unit', value: string): void }>()
</script>

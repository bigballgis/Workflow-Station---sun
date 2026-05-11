<template>
  <div class="page-header">
    <span class="page-title">{{ title }}</span>
    <div
      v-if="$slots.actions"
      class="header-actions"
    >
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 全局页面标题组件
 * 
 * 替代所有页面中重复的 page-header 模板。
 * 
 * @example
 * <PageHeader :title="t('menu.userList')">
 *   <template #actions>
 *     <el-button type="primary" @click="showCreateDialog">
 *       <el-icon><Plus /></el-icon>{{ t('user.createUser') }}
 *     </el-button>
 *   </template>
 * </PageHeader>
 */
defineProps<{
  title: string
}>()
</script>

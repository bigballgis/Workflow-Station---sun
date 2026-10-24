<template>
  <span class="designer-unsaved-notice" role="status">
    <el-icon><WarningFilled /></el-icon>
    <span>{{ t('common.unsavedChanges') }}</span>
  </span>
</template>

<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped lang="scss">
.designer-unsaved-notice {
  display: inline-flex;
  flex: 0 1 auto;
  align-items: center;
  gap: 7px;
  width: max-content;
  max-width: 100%;
  min-width: 0;
  padding: 5px 9px;
  border: 1px solid #fedf89;
  border-radius: 5px;
  background: #fffaeb;
  color: #b54708;
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
  text-align: left;

  &.dialog-unsaved-notice {
    margin-left: auto;
  }

  .el-icon {
    display: inline-flex;
    flex: 0 0 20px;
    width: 20px;
    height: 20px;
    font-size: 20px;
  }
}
</style>

<template>
  <div class="page-container">
    <PageHeader :title="t('menu.environmentVariables')">
      <template #actions>
        <DesignerHelpLink
          path="/environment-variables"
          :aria-label="t('config.envGuideLinkAria')"
          test-id="env-guide-link"
        />
      </template>
    </PageHeader>
    <EnvironmentVariablesPanel />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import EnvironmentVariablesPanel from '@/views/config/EnvironmentVariablesPanel.vue'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'

const { t } = useI18n()
</script>

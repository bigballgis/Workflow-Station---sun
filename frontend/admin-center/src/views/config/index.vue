<template>
  <div class="page-container">
    <PageHeader :title="t('menu.config')" />
    
    <el-tabs v-model="activeTab">
      <el-tab-pane
        :label="t('config.systemParams')"
        name="system"
      >
        <el-form
          :model="systemConfig"
          label-width="150px"
        >
          <el-form-item :label="t('config.sessionTimeout')">
            <el-input-number
              v-model="systemConfig.sessionTimeout"
              :min="5"
              :max="120"
            />
            <span class="form-tip">{{ t('config.minutes') }}</span>
          </el-form-item>
          <el-form-item :label="t('config.fileUploadLimit')">
            <el-input-number
              v-model="systemConfig.maxFileSize"
              :min="1"
              :max="100"
            />
            <span class="form-tip">MB</span>
          </el-form-item>
          <el-form-item :label="t('config.mailServer')">
            <el-input
              v-model="systemConfig.smtpServer"
              style="width: 300px"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="saveConfig('system')"
            >
              {{ t('common.save') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <el-tab-pane
        :label="t('config.businessParams')"
        name="business"
      >
        <el-form
          :model="businessConfig"
          label-width="150px"
        >
          <el-form-item :label="t('config.processTimeout')">
            <el-input-number
              v-model="businessConfig.processTimeout"
              :min="1"
              :max="30"
            />
            <span class="form-tip">{{ t('config.days') }}</span>
          </el-form-item>
          <el-form-item :label="t('config.taskAssignRule')">
            <el-select v-model="businessConfig.taskAssignRule">
              <el-option
                :label="t('config.roundRobin')"
                value="ROUND_ROBIN"
              />
              <el-option
                :label="t('config.loadBalance')"
                value="LOAD_BALANCE"
              />
              <el-option
                :label="t('config.random')"
                value="RANDOM"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="saveConfig('business')"
            >
              {{ t('common.save') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { useConfig } from '@/composables/modules/useConfig'

const { t } = useI18n()

const {
  activeTab, systemConfig, businessConfig, saveConfig, loadConfigs,
} = useConfig()

onActivated(() => { loadConfigs() })
</script>

<style scoped>
.form-tip { margin-left: 10px; color: #909399; }
</style>

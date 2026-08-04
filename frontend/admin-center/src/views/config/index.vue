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
          <el-divider content-position="left">{{ t('config.smtpSection') }}</el-divider>
          <el-form-item :label="t('config.smtpHost')" required>
            <el-input
              v-model="systemConfig.smtpHost"
              style="width: 300px"
              placeholder="smtp.example.com"
            />
          </el-form-item>
          <el-form-item :label="t('config.smtpPort')" required>
            <el-input-number
              v-model="systemConfig.smtpPort"
              :min="1"
              :max="65535"
            />
          </el-form-item>
          <el-form-item :label="t('config.smtpUseTls')" required>
            <el-radio-group v-model="systemConfig.smtpUseTls">
              <el-radio :value="true">{{ t('common.yes') }}</el-radio>
              <el-radio :value="false">{{ t('common.no') }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-divider content-position="left">{{ t('config.imapSection') }}</el-divider>
          <el-form-item :label="t('config.imapHost')" required>
            <el-input
              v-model="systemConfig.imapHost"
              style="width: 300px"
              placeholder="imap.example.com"
            />
          </el-form-item>
          <el-form-item :label="t('config.imapPort')" required>
            <el-input-number
              v-model="systemConfig.imapPort"
              :min="1"
              :max="65535"
            />
          </el-form-item>
          <el-form-item :label="t('config.imapUseSsl')" required>
            <el-radio-group v-model="systemConfig.imapUseSsl">
              <el-radio :value="true">{{ t('common.yes') }}</el-radio>
              <el-radio :value="false">{{ t('common.no') }}</el-radio>
            </el-radio-group>
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
import { onActivated, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { useConfig } from '@/composables/modules/useConfig'

const { t } = useI18n()

const {
  activeTab, systemConfig, businessConfig, saveConfig, loadConfigs,
} = useConfig()

onMounted(() => { loadConfigs() })
onActivated(() => { loadConfigs() })
</script>

<style scoped>
.form-tip { margin-left: 10px; color: #909399; }
</style>

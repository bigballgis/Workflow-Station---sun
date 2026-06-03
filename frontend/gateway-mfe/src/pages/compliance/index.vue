<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('gateway.complianceDashboard') }}</span>
    </div>
    <el-card class="search-card">
      <div class="search-form">
        <el-input v-model="releaseIdInput" :placeholder="t('gateway.enterReleaseId')" style="width:220px" @keyup.enter="handleCheck" />
        <el-button type="primary" @click="handleCheck" :loading="checking">{{ t('gateway.runCheck') }}</el-button>
      </div>
    </el-card>
    <el-card v-if="result" class="table-card">
      <template #header><span>{{ t('gateway.complianceResult') }} — {{ t('gateway.release') }} #{{ releaseIdInput }}</span></template>
      <el-alert v-if="result.passed" :title="t('gateway.compliancePassed')" type="success" show-icon :closable="false" />
      <el-alert v-else :title="t('gateway.complianceFailed')" type="error" show-icon :closable="false" style="margin-bottom:16px" />
      <div v-if="result.violations?.length">
        <h4 style="color:#f56c6c">{{ t('gateway.violations') }} ({{ result.violations.length }})</h4>
        <el-table :data="result.violations" size="small">
          <el-table-column prop="ruleCode" :label="t('gateway.ruleCode')" width="200" />
          <el-table-column prop="ruleName" :label="t('gateway.ruleName')" />
          <el-table-column prop="severity" :label="t('gateway.severity')" width="80"><template #default="{ row }"><el-tag type="danger">{{ row.severity }}</el-tag></template></el-table-column>
          <el-table-column prop="message" :label="t('gateway.message')" />
        </el-table>
      </div>
      <div v-if="result.warnings?.length" style="margin-top:16px">
        <h4 style="color:#e6a23c">{{ t('gateway.warnings') }} ({{ result.warnings.length }})</h4>
        <el-table :data="result.warnings" size="small">
          <el-table-column prop="ruleCode" :label="t('gateway.ruleCode')" width="200" />
          <el-table-column prop="ruleName" :label="t('gateway.ruleName')" />
          <el-table-column prop="severity" :label="t('gateway.severity')" width="80"><template #default="{ row }"><el-tag type="warning">{{ row.severity }}</el-tag></template></el-table-column>
          <el-table-column prop="message" :label="t('gateway.message')" />
        </el-table>
      </div>
      <div v-if="!result.violations?.length && !result.warnings?.length" style="color:#67c23a">{{ t('gateway.noIssues') }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { complianceCheck, getComplianceCheck } from '@/api/gateway'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const releaseIdInput = ref(''), checking = ref(false), result = ref<any>(null)

const handleCheck = async () => {
  if (!releaseIdInput.value) return
  checking.value = true
  try {
    const res: any = await complianceCheck(Number(releaseIdInput.value))
    result.value = res
  } catch (e: any) {
    // Try GET latest check
    try { const res: any = await getComplianceCheck(Number(releaseIdInput.value)); result.value = res } catch {
      ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
    }
  }
  finally { checking.value = false }
}
</script>

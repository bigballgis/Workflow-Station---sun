<template>
  <div
    class="cf-guide"
    data-testid="computed-field-guide-page"
  >
    <el-card class="cf-guide-card">
      <h1 class="cf-guide-title">
        {{ t('computedFieldGuide.pageTitle') }}
      </h1>
      <p class="cf-guide-intro">
        {{ t('computedFieldGuide.intro') }}
      </p>

      <section
        v-for="section in sections"
        :key="section.titleKey"
        class="cf-guide-section"
      >
        <h2>{{ t(section.titleKey) }}</h2>
        <p>{{ t(section.bodyKey) }}</p>
        <ul v-if="section.samples?.length">
          <li
            v-for="sample in section.samples"
            :key="sample.code"
          >
            <code>{{ sample.code }}</code>
            <span>{{ t(sample.hintKey) }}</span>
          </li>
        </ul>
      </section>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const sections = [
  {
    titleKey: 'computedFieldGuide.basicsTitle',
    bodyKey: 'computedFieldGuide.basicsBody',
  },
  {
    titleKey: 'computedFieldGuide.thisRowTitle',
    bodyKey: 'computedFieldGuide.thisRowBody',
    samples: [
      { code: 'price * quantity', hintKey: 'computedFieldGuide.thisRowSample' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.relatedRowsTitle',
    bodyKey: 'computedFieldGuide.relatedRowsBody',
    samples: [
      { code: 'SUM(date_info.day)', hintKey: 'computedFieldGuide.sampleSum' },
      { code: 'AVG(date_info.day)', hintKey: 'computedFieldGuide.sampleAvg' },
      { code: 'MIN(date_info.day) / MAX(date_info.day)', hintKey: 'computedFieldGuide.sampleMinMax' },
      { code: 'COUNT(date_info)', hintKey: 'computedFieldGuide.sampleCountRows' },
      { code: 'COUNT(date_info.day)', hintKey: 'computedFieldGuide.sampleCountCells' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.parentTitle',
    bodyKey: 'computedFieldGuide.parentBody',
    samples: [
      { code: 'leave_request.name', hintKey: 'computedFieldGuide.parentSample' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.datesTitle',
    bodyKey: 'computedFieldGuide.datesBody',
    samples: [
      { code: 'enddate - startdate', hintKey: 'computedFieldGuide.dateMinus' },
      { code: 'DATEDIFF(startdate, enddate)', hintKey: 'computedFieldGuide.dateDatediff' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.logicTitle',
    bodyKey: 'computedFieldGuide.logicBody',
    samples: [
      { code: 'IF(condition, then, else?)', hintKey: 'computedFieldGuide.fnIf' },
      { code: 'AND(...) / OR(...)', hintKey: 'computedFieldGuide.fnAndOr' },
      { code: 'SWITCH(value, match1, result1, …, default?)', hintKey: 'computedFieldGuide.fnSwitch' },
      { code: 'COALESCE(...)', hintKey: 'computedFieldGuide.fnCoalesce' },
    ],
  },
  {
    titleKey: 'computedFieldGuide.mathTitle',
    bodyKey: 'computedFieldGuide.mathBody',
  },
  {
    titleKey: 'computedFieldGuide.textTitle',
    bodyKey: 'computedFieldGuide.textBody',
  },
  {
    titleKey: 'computedFieldGuide.errorsTitle',
    bodyKey: 'computedFieldGuide.errorsBody',
  },
  {
    titleKey: 'computedFieldGuide.relationTitle',
    bodyKey: 'computedFieldGuide.relationBody',
  },
]
</script>

<style scoped>
.cf-guide {
  padding: 8px 4px 24px;
}

.cf-guide-card {
  max-width: 880px;
  margin: 0 auto;
}

.cf-guide-title {
  margin: 0 0 12px;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.cf-guide-intro {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
}

.cf-guide-section {
  margin-top: 22px;
}

.cf-guide-section h2 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.cf-guide-section p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.cf-guide-section ul {
  margin: 10px 0 0;
  padding-left: 18px;
}

.cf-guide-section li {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.cf-guide-section code {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>

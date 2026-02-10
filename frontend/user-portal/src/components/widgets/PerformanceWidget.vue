<template>
  <div class="performance-widget">
    <div class="score-section">
      <div class="main-score">
        <div class="score-ring">
          <svg viewBox="0 0 100 100">
            <circle
              cx="50"
              cy="50"
              r="45"
              fill="none"
              stroke="#f0f0f0"
              stroke-width="8"
            />
            <circle
              cx="50"
              cy="50"
              r="45"
              fill="none"
              stroke="#00A651"
              stroke-width="8"
              stroke-linecap="round"
              :stroke-dasharray="`${overallScore * 2.83} 283`"
              transform="rotate(-90 50 50)"
            />
          </svg>
          <div class="score-value">{{ overallScore }}</div>
        </div>
        <div class="score-label">{{ t('widget.overallScore') }}</div>
      </div>
    </div>

    <div class="metrics-grid">
      <div class="metric-item">
        <div class="metric-header">
          <span class="metric-name">{{ $t('dashboard.efficiencyScore') }}</span>
          <span class="metric-value">{{ metrics.efficiency }}</span>
        </div>
        <el-progress
          :percentage="metrics.efficiency"
          :stroke-width="6"
          :show-text="false"
          color="#1890ff"
        />
      </div>
      <div class="metric-item">
        <div class="metric-header">
          <span class="metric-name">{{ $t('dashboard.qualityScore') }}</span>
          <span class="metric-value">{{ metrics.quality }}</span>
        </div>
        <el-progress
          :percentage="metrics.quality"
          :stroke-width="6"
          :show-text="false"
          color="#00A651"
        />
      </div>
      <div class="metric-item">
        <div class="metric-header">
          <span class="metric-name">{{ $t('dashboard.collaborationScore') }}</span>
          <span class="metric-value">{{ metrics.collaboration }}</span>
        </div>
        <el-progress
          :percentage="metrics.collaboration"
          :stroke-width="6"
          :show-text="false"
          color="#FF6600"
        />
      </div>
    </div>

    <div class="rank-info">
      <el-icon><Trophy /></el-icon>
      <span>{{ $t('dashboard.monthlyRank') }}: </span>
      <span class="rank-value">{{ t('widget.rankDisplay', { rank }) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Trophy } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const { t } = useI18n()
const dashboardStore = useDashboardStore()

const metrics = ref({
  efficiency: 85,
  quality: 92,
  collaboration: 88
})

const rank = ref(12)

const overallScore = computed(() => {
  return Math.round(
    (metrics.value.efficiency + metrics.value.quality + metrics.value.collaboration) / 3
  )
})

onMounted(async () => {
  await dashboardStore.fetchOverview()
  if (dashboardStore.overview) {
    metrics.value = {
      efficiency: dashboardStore.overview.efficiencyScore || 85,
      quality: dashboardStore.overview.qualityScore || 92,
      collaboration: dashboardStore.overview.collaborationScore || 88
    }
    rank.value = dashboardStore.overview.monthlyRank || 12
  }
})
</script>

<style scoped lang="scss">
.performance-widget {
  height: 100%;
  display: flex;
  flex-direction: column;

  .score-section {
    display: flex;
    justify-content: center;
    margin-bottom: 16px;

    .main-score {
      text-align: center;

      .score-ring {
        position: relative;
        width: 80px;
        height: 80px;
        margin: 0 auto;

        svg {
          width: 100%;
          height: 100%;
        }

        .score-value {
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          font-size: 24px;
          font-weight: 700;
          color: #00A651;
        }
      }

      .score-label {
        margin-top: 8px;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .metrics-grid {
    flex: 1;

    .metric-item {
      margin-bottom: 12px;

      .metric-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 4px;

        .metric-name {
          font-size: 12px;
          color: #606266;
        }

        .metric-value {
          font-size: 12px;
          font-weight: 600;
          color: #303133;
        }
      }
    }
  }

  .rank-info {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    padding: 10px;
    background: #fff7e6;
    border-radius: 4px;
    font-size: 13px;
    color: #FF6600;

    .rank-value {
      font-weight: 600;
    }
  }
}
</style>

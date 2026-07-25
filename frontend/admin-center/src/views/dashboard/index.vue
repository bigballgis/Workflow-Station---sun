<template>
  <div class="dashboard">
    <el-row
      v-loading="statsLoading"
      :gutter="20"
    >
      <el-col
        v-for="stat in statsCards"
        :key="stat.titleKey"
        :span="6"
      >
        <div class="stat-card">
          <div class="stat-head">
            <span class="stat-icon">
              <el-icon :size="18">
                <component :is="stat.icon" />
              </el-icon>
            </span>
            <span class="stat-title">{{ t(stat.titleKey) }}</span>
          </div>
          <div class="stat-value">
            {{ stat.value }}
          </div>
          <div class="stat-rule">
            <span class="stat-rule-fill" />
          </div>
        </div>
      </el-col>
    </el-row>
    
    <el-row
      :gutter="20"
      class="lower-row"
    >
      <el-col
        :lg="12"
        :xs="24"
      >
        <section class="ledger-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.userTrends') }}</span>
            <div class="chart-legend">
              <span class="legend-item">
                <span class="legend-dot is-line" />{{ t('dashboard.activeUsers') }}
              </span>
              <span class="legend-item">
                <span class="legend-dot is-bar" />{{ t('dashboard.newUsers') }}
              </span>
            </div>
          </header>
          <div
            ref="systemChartRef"
            v-loading="trendsLoading"
            class="chart-container"
          />
        </section>
      </el-col>
      <el-col
        :lg="12"
        :xs="24"
      >
        <section class="ledger-card">
          <header class="card-head">
            <span class="eyebrow">{{ t('dashboard.recentActivities') }}</span>
          </header>
          <el-skeleton
            v-if="activitiesLoading && activities.length === 0"
            :rows="5"
            animated
          />
          <ul
            v-else-if="activities.length > 0"
            class="activity-ledger"
          >
            <li
              v-for="activity in activities"
              :key="activity.id"
              class="activity-row"
            >
              <span class="activity-text">{{ activity.description || `${activity.username} ${activity.action} ${activity.resourceName || activity.resourceType}` }}</span>
              <span class="activity-time">{{ formatDateTime(activity.createdAt) }}</span>
            </li>
          </ul>
          <p
            v-else
            class="activity-empty"
          >
            {{ t('dashboard.noActivities') }}
          </p>
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onActivated } from 'vue'
import { useDashboard } from '@/composables/modules/useDashboard'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()

const {
  systemChartRef, statsLoading, activitiesLoading, trendsLoading,
  statsCards, activities, loadStats, loadActivities,
} = useDashboard()

onActivated(() => { loadStats(); loadActivities() })

</script>

<style scoped lang="scss">
// 参考稿统计卡：红色浅底图标砖 + 标签，大号数字，底部红色饰线
.stat-card {
  background: var(--ws-card-bg);
  border: 1px solid var(--ws-card-border);
  border-radius: var(--ws-radius-card);
  padding: 20px 22px;
  box-shadow: 0 1px 2px rgba(20, 20, 20, 0.04);
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 12px;

  .stat-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: var(--primary-soft);
    color: var(--primary-color);
    flex-shrink: 0;
  }

  .stat-title {
    color: var(--ws-text-secondary);
    font-size: 14px;
    font-weight: 500;
  }
}

.stat-value {
  font-size: 38px;
  font-weight: 700;
  color: var(--ws-text);
  line-height: 1.2;
  margin: 14px 0 16px;
  font-variant-numeric: tabular-nums;
}

.stat-rule {
  height: 4px;
  border-radius: 2px;
  background: var(--primary-soft);
  overflow: hidden;

  .stat-rule-fill {
    display: block;
    height: 100%;
    width: 62%;
    border-radius: 2px;
    background: var(--primary-color);
  }
}

// ==================== 下半区：账册卡（与 user-portal home 同款） ====================
.lower-row {
  margin-top: 20px;
}

.ledger-card {
  background: var(--ws-card-bg);
  border: 1px solid var(--ws-card-border);
  border-radius: var(--ws-radius-card);
  padding: 18px 22px 20px;
  box-shadow: 0 1px 2px rgba(20, 20, 20, 0.04);
}

// xs 下两卡叠放时拉开距离
@media (max-width: 1199px) {
  .lower-row .el-col + .el-col .ledger-card {
    margin-top: 20px;
  }
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  .eyebrow {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #8f8f8a;
  }
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--ws-text-secondary);
  }

  .legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;

    &.is-line { background: var(--primary-color); }
    &.is-bar { background: #d8d8d4; }
  }
}

.chart-container {
  height: 300px;
}

.activity-ledger {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 300px;
  overflow-y: auto;
}

.activity-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 11px 0;
  border-bottom: 1px solid var(--ws-line);

  &:last-child {
    border-bottom: none;
  }

  .activity-text {
    min-width: 0;
    font-size: 14px;
    color: var(--ws-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .activity-time {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--ws-text-muted);
    font-variant-numeric: tabular-nums;
  }
}

.activity-empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 14px;
  color: var(--ws-text-muted);
}
</style>

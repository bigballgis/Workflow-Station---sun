<template>
  <div class="calendar-widget">
    <el-calendar v-model="currentDate">
      <template #date-cell="{ data }">
        <div
          class="calendar-cell"
          :class="{ 'has-tasks': hasTasksOnDate(data.day) }"
        >
          <span class="date-number">{{ data.day.split('-')[2] }}</span>
          <div
            v-if="hasTasksOnDate(data.day)"
            class="task-dots"
          >
            <span
              v-for="(task, index) in getTasksOnDate(data.day).slice(0, 3)"
              :key="index"
              class="task-dot"
              :class="task.priority"
            />
          </div>
        </div>
      </template>
    </el-calendar>

    <div
      v-if="selectedDateTasks.length > 0"
      class="selected-date-tasks"
    >
      <div class="tasks-header">
        <span>{{ t('dashboard.calendarTasksForDate', { date: formatSelectedDate }) }}</span>
        <span class="task-count">{{ t('dashboard.calendarTaskCount', { count: selectedDateTasks.length }) }}</span>
      </div>
      <div class="tasks-list">
        <div
          v-for="task in selectedDateTasks"
          :key="task.id"
          class="task-item"
          @click="goToTask(task.id)"
        >
          <span
            class="task-priority"
            :class="task.priority"
          />
          <span class="task-name">{{ task.name }}</span>
          <span class="task-time">{{ task.dueTime }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useTaskStore } from '@/stores/task'

interface CalendarTask {
  id: string
  name: string
  dueDate: string
  dueTime?: string
  priority: 'urgent' | 'high' | 'normal' | 'low'
}

const router = useRouter()
const { t, locale } = useI18n()
const taskStore = useTaskStore()

const currentDate = ref(new Date())
const tasks = ref<CalendarTask[]>([])

// 按日期预分组一次，日历每格用 O(1) 查表替代对全量任务逐格 filter
// （date-cell 模板每格会调 hasTasksOnDate + getTasksOnDate 两次，约 35 格）。
const tasksByDate = computed(() => {
  const map = new Map<string, CalendarTask[]>()
  for (const task of tasks.value) {
    if (!task.dueDate) continue
    const list = map.get(task.dueDate)
    if (list) {
      list.push(task)
    } else {
      map.set(task.dueDate, [task])
    }
  }
  return map
})

// 获取指定日期的任务
const getTasksOnDate = (dateStr: string) => {
  return tasksByDate.value.get(dateStr) ?? []
}

// 检查日期是否有任务
const hasTasksOnDate = (dateStr: string) => {
  return tasksByDate.value.has(dateStr)
}

// 选中日期的任务
const selectedDateTasks = computed(() => {
  const dateStr = formatDate(currentDate.value)
  return getTasksOnDate(dateStr)
})

// 格式化选中日期
const formatSelectedDate = computed(() => {
  return currentDate.value.toLocaleDateString(locale.value, {
    month: 'long',
    day: 'numeric'
  })
})

// 格式化日期为 YYYY-MM-DD
const formatDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 跳转到任务详情
const goToTask = (id: string) => {
  router.push(`/tasks/${id}`)
}

// 加载任务数据
const loadTasks = async () => {
  await taskStore.fetchTasks({ page: 0, size: 100 })
  tasks.value = taskStore.tasks.map(task => ({
    id: task.id,
    name: task.name,
    dueDate: task.dueDate?.split('T')[0] || '',
    dueTime: task.dueDate?.split('T')[1]?.substring(0, 5),
    priority: task.priority as any
  }))
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped lang="scss">
.calendar-widget {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-calendar) {
    --el-calendar-border: 1px solid #f0f0f0;

    .el-calendar__header {
      padding: 8px 12px;
      border-bottom: 1px solid #f0f0f0;
    }

    .el-calendar__body {
      padding: 8px;
    }

    .el-calendar-table {
      th {
        padding: 6px 0;
        font-size: 12px;
      }

      td {
        border: none;

        &.is-selected {
          background: transparent;

          .calendar-cell {
            background: #fff5f5;
            border-radius: 4px;
          }
        }

        &.is-today {
          .calendar-cell {
            .date-number {
              background: #DB0011;
              color: white;
              border-radius: 50%;
              width: 24px;
              height: 24px;
              display: flex;
              align-items: center;
              justify-content: center;
            }
          }
        }
      }

      .el-calendar-day {
        height: auto;
        min-height: 40px;
        padding: 2px;
      }
    }
  }

  .calendar-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 4px;
    cursor: pointer;

    &.has-tasks {
      background: #f6ffed;
      border-radius: 4px;
    }

    .date-number {
      font-size: 13px;
      color: #303133;
    }

    .task-dots {
      display: flex;
      gap: 2px;
      margin-top: 2px;

      .task-dot {
        width: 4px;
        height: 4px;
        border-radius: 50%;

        &.urgent {
          background: #DB0011;
        }

        &.high {
          background: #FF6600;
        }

        &.normal {
          background: #1890ff;
        }

        &.low {
          background: #00A651;
        }
      }
    }
  }

  .selected-date-tasks {
    flex: 1;
    border-top: 1px solid #f0f0f0;
    padding: 12px;
    overflow: auto;

    .tasks-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
      font-size: 13px;
      font-weight: 600;
      color: #303133;

      .task-count {
        font-weight: normal;
        color: #909399;
      }
    }

    .tasks-list {
      .task-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px;
        border-radius: 4px;
        cursor: pointer;
        margin-bottom: 4px;

        &:hover {
          background: #f5f7fa;
        }

        .task-priority {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          flex-shrink: 0;

          &.urgent {
            background: #DB0011;
          }

          &.high {
            background: #FF6600;
          }

          &.normal {
            background: #1890ff;
          }

          &.low {
            background: #00A651;
          }
        }

        .task-name {
          flex: 1;
          font-size: 13px;
          color: #303133;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .task-time {
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }
}
</style>

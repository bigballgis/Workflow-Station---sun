<template>
  <div class="calendar-widget">
    <el-calendar v-model="currentDate">
      <template #date-cell="{ data }">
        <div class="calendar-cell" :class="{ 'has-tasks': hasTasksOnDate(data.day) }">
          <span class="date-number">{{ data.day.split('-')[2] }}</span>
          <div class="task-dots" v-if="hasTasksOnDate(data.day)">
            <span
              v-for="(task, index) in getTasksOnDate(data.day).slice(0, 3)"
              :key="index"
              class="task-dot"
              :class="task.priority"
            ></span>
          </div>
        </div>
      </template>
    </el-calendar>

    <div class="selected-date-tasks" v-if="selectedDateTasks.length > 0">
      <div class="tasks-header">
        <span>{{ formatSelectedDate }} 的任务</span>
        <span class="task-count">{{ selectedDateTasks.length }} 项</span>
      </div>
      <div class="tasks-list">
        <div
          v-for="task in selectedDateTasks"
          :key="task.id"
          class="task-item"
          @click="goToTask(task.id)"
        >
          <span class="task-priority" :class="task.priority"></span>
          <span class="task-name">{{ task.name }}</span>
          <span class="task-time">{{ task.dueTime }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTaskStore } from '@/stores/task'

interface CalendarTask {
  id: string
  name: string
  dueDate: string
  dueTime?: string
  priority: 'urgent' | 'high' | 'normal' | 'low'
}

const router = useRouter()
const taskStore = useTaskStore()

const currentDate = ref(new Date())
const tasks = ref<CalendarTask[]>([])

// 获取指定日期的任务
const getTasksOnDate = (dateStr: string) => {
  return tasks.value.filter(task => task.dueDate === dateStr)
}

// 检查日期是否有任务
const hasTasksOnDate = (dateStr: string) => {
  return getTasksOnDate(dateStr).length > 0
}

// 选中日期的任务
const selectedDateTasks = computed(() => {
  const dateStr = formatDate(currentDate.value)
  return getTasksOnDate(dateStr)
})

// 格式化选中日期
const formatSelectedDate = computed(() => {
  return currentDate.value.toLocaleDateString('zh-CN', {
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
    id: task.taskId,
    name: task.taskName,
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

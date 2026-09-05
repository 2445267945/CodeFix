<!-- src/views/TaskList.vue -->

<template>
  <div class="task-list-page">
    <div class="page-header">
      <div>
        <h2>代码审计任务</h2>
        <p class="page-description">查看和管理你的 Agent 审计任务</p>
      </div>

      <el-button type="primary" @click="createNewTask"> 新建任务 </el-button>
    </div>

    <el-card class="task-card" shadow="never">
      <el-table
        v-loading="taskStore.loading"
        :data="tasks"
        style="width: 100%"
        empty-text="暂无任务"
      >
        <el-table-column label="任务" min-width="320">
          <template #default="{ row }">
            <div class="task-title">
              {{ getTaskTitle(row) }}
            </div>

            <div class="task-id">
              {{ row.taskId }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <TaskStatus :status-value="row.statusValue" />
          </template>
        </el-table-column>

        <el-table-column label="Run" width="110">
          <template #default="{ row }">
            <span class="run-text">
              {{ shortRunId(row.runId) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTask(row.taskId)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import TaskStatus from "../components/TaskStatus.vue";
import { useTaskStore } from "../stores/task";

const router = useRouter();
const taskStore = useTaskStore();

/**
 * 第一版暂时使用本地列表。
 *
 * 目前你的后端已经有：
 * GET /tasks/{taskId}
 *
 * 但还没有真正的：
 * GET /tasks
 *
 * 因此这里暂时不伪造一个不存在的后端 API。
 *
 * 后续增加任务列表接口后，
 * 只需要把这里改成调用 API 即可。
 */
const tasks = computed(() => taskStore.tasks);

onMounted(async () => {
  try {
    await taskStore.fetchTasks();
  } catch (error) {
    ElMessage.error(taskStore.error || "获取任务列表失败");
  }
});

/**
 * 获取任务标题
 *
 * 第一版没有 name 字段，
 * 所以优先显示 question。
 */
function getTaskTitle(task) {
  if (!task) {
    return "未命名任务";
  }

  const question = task.question || "";

  if (!question) {
    return "Java 代码审计任务";
  }

  const normalized = question.replace(/\s+/g, " ").trim();

  if (normalized.length <= 60) {
    return normalized;
  }

  return `${normalized.slice(0, 60)}...`;
}

/**
 * Run ID 简短显示
 */
function shortRunId(runId) {
  if (!runId) {
    return "-";
  }

  return runId.slice(0, 8);
}

/**
 * 时间格式化
 */
function formatTime(timestamp) {
  if (!timestamp) {
    return "-";
  }

  const date = new Date(timestamp);

  if (Number.isNaN(date.getTime())) {
    return "-";
  }

  return date.toLocaleString();
}

/**
 * 打开任务详情
 */
async function openTask(taskId) {
  router.push(`/tasks/${taskId}`);
  //   console.log('[TaskList] 点击查看:', taskId)

  //   try {
  //     await router.push(`/tasks/${taskId}`)
  //     console.log('[TaskList] 路由跳转成功:', router.currentRoute.value.fullPath)
  //   } catch (error) {
  //     console.error('[TaskList] 路由跳转失败:', error)
  //   }
}

/**
 * 新建任务
 *
 * 第一版先跳到任务详情页不存在的情况不处理。
 * 后续可以增加独立的新建任务页面或 Dialog。
 */
function createNewTask() {
  router.push("/tasks/new");
}
</script>

<style scoped>
.task-list-page {
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.page-description {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.task-card {
  border-radius: 10px;
}

.task-title {
  overflow: hidden;
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-id {
  margin-top: 4px;
  color: #c0c4cc;
  font-family: Consolas, monospace;
  font-size: 11px;
}

.run-text {
  color: #606266;
  font-family: Consolas, monospace;
  font-size: 12px;
}
</style>
<!-- src/components/TaskStatus.vue -->

<template>
  <div class="task-status">
    <el-tag :type="tagType" :effect="effect" size="default" round>
      <span v-if="isRunning" class="status-dot"></span>

      {{ displayStatus }}
    </el-tag>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  /**
   * Java 后端返回的 statusValue
   *
   * 例如：
   * CREATED
   * QUEUED
   * AGENT_THINKING
   * TOOL_CALLING
   * BLOCKED
   * WAITING_HUMAN
   * WAITING_RETRY
   * NEED_RETRY
   * COMPLETED
   * CANCELLED
   */
  statusValue: {
    type: String,
    default: "",
  },
});

/**
 * 状态显示名称
 *
 * 第一版直接前端转换。
 * 后续如果 Java 直接返回 statusDesc，
 * 可以直接使用后端描述。
 */
const statusTextMap = {
  CREATED: "新建任务",
  QUEUED: "等待执行",
  AGENT_THINKING: "AI 推理中",
  TOOL_CALLING: "工具执行中",
  BLOCKED: "等待外部处理",
  WAITING_HUMAN: "等待人工确认",
  WAITING_RETRY: "等待重试",
  NEED_RETRY: "任务失败",
  FINISHED: "任务完成",
  CANCELLED: "任务已取消",
};

const displayStatus = computed(() => {
  return statusTextMap[props.statusValue] || props.statusValue || "未知状态";
});

/**
 * Element Plus Tag 类型
 */
const tagType = computed(() => {
  switch (props.statusValue) {
    case "CREATED":
      return "info";

    case "QUEUED":
      return "info";

    case "AGENT_THINKING":
      return "primary";

    case "TOOL_CALLING":
      return "warning";

    case "BLOCKED":
      return "warning";

    case "WAITING_HUMAN":
      return "warning";

    case "WAITING_RETRY":
      return "warning";

    case "NEED_RETRY":
      return "danger";

    case "COMPLETED":
      return "success";

    case "CANCELLED":
      return "info";

    default:
      return "info";
  }
});

/**
 * 正在运行的状态
 */
const isRunning = computed(() => {
  return [
    "AGENT_THINKING",
    "TOOL_CALLING",
    "BLOCKED",
    "WAITING_HUMAN",
  ].includes(props.statusValue);
});

const effect = computed(() => {
  return isRunning.value ? "light" : "plain";
});
</script>

<style scoped>
.task-status {
  display: inline-flex;
  align-items: center;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
  }

  50% {
    opacity: 1;
  }
}
</style>
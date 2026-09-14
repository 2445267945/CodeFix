<template>
  <div class="action-block" :class="[`is-${status}`]">
    <!-- Action 主行 -->
    <button class="action-row" type="button" @click="toggle">
      <span class="action-marker">
        {{ marker }}
      </span>

      <span class="action-summary">
        {{ block.summary || "" }}
      </span>

      <span v-if="status !== 'completed'" class="action-status">
        {{ statusText }}
      </span>
    </button>

    <!-- Human Approval -->
    <div v-if="requiresApproval" class="approval-row">
      <span class="approval-text">
        {{ block.summary || "等待用户确认此操作" }}
      </span>

      <div class="approval-actions">
        <button
          type="button"
          class="approval-button approve"
          :disabled="approvalLoading"
          @click.stop="approve"
        >
          {{ approvalLoading ? "..." : "Yes" }}
        </button>

        <button
          type="button"
          class="approval-button reject"
          :disabled="approvalLoading"
          @click.stop="reject"
        >
          No
        </button>
      </div>
    </div>

    <!-- Detail -->
    <transition name="collapse">
      <div v-if="expanded" class="action-detail">
        <div class="detail-head">
          <span>
            {{ block.action || "操作" }}
          </span>

          <span v-if="block.timestamp" class="detail-time">
            {{ formatTime(block.timestamp) }}
          </span>
        </div>

        <div v-if="block.detail || block.summary" class="detail-body">
          {{ block.detail || block.summary }}
        </div>

        <div v-if="block.sourceEventIds?.length" class="source-events">
          {{ block.sourceEventIds.length }} 个执行事件
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { useTaskStore } from "../stores/task";

const props = defineProps({
  block: {
    type: Object,
    required: true,
  },
});

const taskStore = useTaskStore();

const expanded = ref(false);
const approvalLoading = ref(false);

/**
 * Product Activity Status
 *
 * waiting
 * running
 * completed
 * failed
 */
const status = computed(() => {
  return props.block?.status || "completed";
});

/**
 * 只有真正等待人工确认时，
 * 才显示 Yes / No。
 */
const requiresApproval = computed(() => {
  return (
    props.block?.requiresApproval === true &&
    props.block?.status === "waiting" &&
    !!props.block?.actionId
  );
});

/**
 * 状态文字仅用于状态展示，
 * 不参与生成主要业务 summary。
 *
 * 主要业务文案统一由后端 block.summary 提供。
 */
const statusText = computed(() => {
  return (
    {
      waiting: "等待确认",
      running: "进行中",
      failed: "失败",
    }[status.value] || ""
  );
});

/**
 * Activity marker。
 *
 * 不再显示具体 Action 图标，
 * 只保留非常轻量的状态标记。
 */
const marker = computed(() => {
  switch (status.value) {
    case "waiting":
      return "Ⅱ";

    case "running":
      return "●";

    case "failed":
      return "×";

    default:
      return "";
  }
});

function toggle() {
  expanded.value = !expanded.value;
}

/*
 * Approve
 */
async function approve() {
  if (!requiresApproval.value || approvalLoading.value) {
    return;
  }

  approvalLoading.value = true;

  try {
    await taskStore.approveAction(props.block.runId, props.block.actionId);
  } catch (error) {
    console.error("[AgentActionBlock] 批准失败:", error);
  } finally {
    approvalLoading.value = false;
  }
}

/*
 * Reject
 */
async function reject() {
  if (!requiresApproval.value || approvalLoading.value) {
    return;
  }

  approvalLoading.value = true;

  try {
    await taskStore.rejectAction(props.block.runId, props.block.actionId);
  } catch (error) {
    console.error("[AgentActionBlock] 拒绝失败:", error);
  } finally {
    approvalLoading.value = false;
  }
}

function formatTime(value) {
  try {
    return new Date(value).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}
</script>

<style scoped>
.action-block {
  width: 100%;
}

/* ============================================================
   Action Row
============================================================ */

.action-row {
  width: 100%;

  display: flex;
  align-items: center;

  min-width: 0;

  padding: 2px 0;

  border: 0;
  background: transparent;

  color: var(--el-text-color-secondary);

  text-align: left;

  cursor: pointer;

  font: inherit;

  line-height: 20px;
}

.action-row:hover {
  color: var(--el-text-color-primary);
}

/* ============================================================
   Marker
============================================================ */

.action-marker {
  flex: 0 0 auto;

  width: 16px;

  margin-right: 4px;

  color: var(--el-text-color-placeholder);

  font-size: 10px;
  line-height: 20px;

  text-align: center;
}

.is-running .action-marker {
  color: var(--el-color-primary);
}

.is-waiting .action-marker {
  color: var(--el-color-warning);
}

.is-failed .action-marker {
  color: var(--el-color-danger);
}

.is-completed .action-marker {
  color: var(--el-text-color-placeholder);
}

/* ============================================================
   Summary
============================================================ */

.action-summary {
  min-width: 0;

  flex: 1;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  color: inherit;

  font-size: 12px;

  line-height: 20px;
}

/* ============================================================
   Status
============================================================ */

.action-status {
  flex: 0 0 auto;

  margin-left: 8px;

  color: var(--el-text-color-placeholder);

  font-size: 11px;

  white-space: nowrap;
}

/* ============================================================
   Approval
============================================================ */

.approval-row {
  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  margin: 2px 0 4px 20px;

  padding: 3px 0;

  color: var(--el-text-color-secondary);
}

.approval-text {
  min-width: 0;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  font-size: 11px;
  line-height: 18px;
}

.approval-actions {
  flex: 0 0 auto;

  display: flex;
  align-items: center;

  gap: 4px;
}

.approval-button {
  min-width: 34px;

  padding: 2px 7px;

  border: 0;
  border-radius: 5px;

  background: transparent;

  color: var(--el-text-color-secondary);

  font-size: 11px;
  line-height: 17px;

  cursor: pointer;
}

.approval-button:hover:not(:disabled) {
  background: var(--el-fill-color-light);

  color: var(--el-text-color-primary);
}

.approval-button.approve:hover:not(:disabled) {
  color: var(--el-color-success);
}

.approval-button.reject:hover:not(:disabled) {
  color: var(--el-color-danger);
}

.approval-button:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ============================================================
   Detail
============================================================ */

.action-detail {
  margin: 2px 0 6px 20px;

  padding: 7px 10px;

  border-left: 1px solid var(--el-border-color-lighter);

  background: transparent;

  color: var(--el-text-color-secondary);
}

.detail-head {
  display: flex;
  justify-content: space-between;

  color: var(--el-text-color-secondary);

  font-size: 10px;
  font-weight: 600;
}

.detail-time {
  color: var(--el-text-color-placeholder);

  font-weight: 400;
}

.detail-body {
  margin-top: 5px;

  color: var(--el-text-color-secondary);

  white-space: pre-wrap;

  font-size: 11px;
  line-height: 1.6;
}

.source-events {
  margin-top: 5px;

  color: var(--el-text-color-placeholder);

  font-size: 9px;
}

/* ============================================================
   Collapse
============================================================ */

.collapse-enter-active,
.collapse-leave-active {
  max-height: 220px;

  overflow: hidden;

  transition: max-height 0.15s ease, opacity 0.15s ease;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
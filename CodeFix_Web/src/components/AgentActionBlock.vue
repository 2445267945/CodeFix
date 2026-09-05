<template>
  <div class="action-block" :class="[`is-${status}`]">
    <!-- Action 主行 -->
    <button class="action-row" type="button" @click="toggle">
      <span class="chevron" :class="{ expanded }">›</span>

      <span class="action-icon">
        {{ icon }}
      </span>

      <span class="action-summary">
        {{ block.summary || fallbackSummary }}
      </span>

      <span class="agent-name">
        {{ block.agent || "Agent" }}
      </span>

      <span class="action-status">
        {{ statusLabel }}
      </span>
    </button>

    <!-- Human Approval -->
    <div v-if="requiresApproval" class="approval-row">
      <span class="approval-text">
        {{ approvalSummary }}
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
          <span>{{ actionLabel }}</span>

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

/*
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

/*
 * 只有真正等待人工确认时，
 * 才显示 Yes / No。
 *
 * requiresApproval 不能单独决定 UI。
 */
const requiresApproval = computed(() => {
  return (
    props.block?.requiresApproval === true &&
    props.block?.status === "waiting" &&
    !!props.block?.actionId
  );
});

const approvalSummary = computed(() => {
  const summary = props.block?.summary || "Agent 请求执行此操作";

  if (summary.startsWith("等待确认")) {
    return summary.replace(/^等待确认\s*/, "");
  }

  return summary;
});

/*
 * 当前 Product Activity 支持的 Action。
 *
 * THINK / FINISH 不在这里。
 *
 * THINK 不生成 Product Activity。
 * FINISH 由 RESULT_REFRESH 处理。
 */
const iconMap = {
  READ: "↗",
  SEARCH: "⌕",
  WRITE: "↙",
  EXECUTE: "▶",
  VERIFY: "✓",
  DELEGATE: "→",
  ERROR: "×",
};

const labelMap = {
  READ: "读取",
  SEARCH: "搜索",
  WRITE: "修改",
  EXECUTE: "执行",
  VERIFY: "验证",
  DELEGATE: "委派",
  ERROR: "错误",
};

const icon = computed(() => {
  return iconMap[props.block?.action] || "•";
});

const actionLabel = computed(() => {
  return labelMap[props.block?.action] || props.block?.action || "执行";
});

const fallbackSummary = computed(() => {
  switch (status.value) {
    case "waiting":
      return `等待确认${actionLabel.value}`;

    case "running":
      return `Agent 正在${actionLabel.value}`;

    case "failed":
      return `${actionLabel.value}失败`;

    case "completed":
      return `${actionLabel.value}完成`;

    default:
      return actionLabel.value;
  }
});

const statusLabel = computed(() => {
  return (
    {
      waiting: "等待确认",
      running: "进行中",
      completed: "完成",
      failed: "失败",
    }[status.value] || ""
  );
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
    await taskStore.approveAction(
      props.block.runId,
    );
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
    await taskStore.rejectAction(
      props.block.runId,
    );
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
  border-bottom: 1px solid var(--border-subtle);
}

/* ============================================================
   Action Row
============================================================ */

.action-row {
  width: 100%;

  display: grid;

  grid-template-columns:
    16px
    22px
    minmax(0, 1fr)
    auto
    auto;

  gap: 9px;

  align-items: center;

  padding: 10px 2px;

  border: 0;

  background: transparent;

  color: var(--text);

  text-align: left;

  cursor: pointer;
}

.action-row:hover {
  background: var(--surface-hover);
}

/* ============================================================
   Chevron
============================================================ */

.chevron {
  color: var(--muted);

  font-size: 16px;

  transition: transform 0.16s ease;
}

.chevron.expanded {
  transform: rotate(90deg);
}

/* ============================================================
   Icon
============================================================ */

.action-icon {
  width: 20px;
  height: 20px;

  display: grid;
  place-items: center;

  border-radius: 6px;

  color: var(--muted-strong);

  background: var(--surface-soft);

  font-size: 12px;
}

.is-running .action-icon {
  color: var(--accent);
  background: var(--accent-soft);
}

.is-waiting .action-icon {
  color: var(--warning);
  background: var(--warning-soft);
}

.is-completed .action-icon {
  color: var(--success);
  background: var(--success-soft);
}

.is-failed .action-icon {
  color: var(--danger);
  background: var(--danger-soft);
}

/* ============================================================
   Summary
============================================================ */

.action-summary {
  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  font-size: 13px;

  line-height: 20px;
}

.agent-name,
.action-status {
  color: var(--muted);

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

  gap: 16px;

  margin: 0 0 6px 47px;
  padding: 4px 2px 8px 0;

  border-bottom: 1px solid var(--border-subtle);

  color: var(--text-soft);
}

.approval-text {
  min-width: 0;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  font-size: 12px;

  line-height: 20px;
}

.approval-actions {
  flex: 0 0 auto;

  display: flex;
  align-items: center;

  gap: 4px;
}

.approval-button {
  min-width: 36px;

  padding: 3px 8px;

  border: 0;

  border-radius: 5px;

  background: transparent;

  color: var(--muted-strong);

  font-size: 11px;

  line-height: 18px;

  cursor: pointer;
}

.approval-button:hover:not(:disabled) {
  background: var(--surface-hover);

  color: var(--text);
}

.approval-button.approve:hover:not(:disabled) {
  color: var(--success);
}

.approval-button.reject:hover:not(:disabled) {
  color: var(--danger);
}

.approval-button:disabled {
  opacity: 0.45;

  cursor: default;
}

/* ============================================================
   Detail
============================================================ */

.action-detail {
  margin: 0 0 10px 47px;

  padding: 10px 12px;

  border-left: 1px solid var(--border);

  background: var(--surface-soft);

  color: var(--muted-strong);
}

.detail-head {
  display: flex;

  justify-content: space-between;

  font-size: 11px;

  font-weight: 600;

  color: var(--text-soft);
}

.detail-time {
  color: var(--muted);

  font-weight: 400;
}

.detail-body {
  margin-top: 8px;

  white-space: pre-wrap;

  font-size: 12px;

  line-height: 1.65;
}

.source-events {
  margin-top: 8px;

  font-size: 10px;

  color: var(--muted);
}

/* ============================================================
   Collapse
============================================================ */

.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.15s ease;

  max-height: 220px;

  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;

  opacity: 0;
}
</style>
<template>
  <div class="delegate-block" :class="[`is-${status}`]">
    <!-- ==================== Delegate 主行 ==================== -->
    <button class="delegate-row" type="button" @click="toggle">
      <span class="delegate-chevron" :class="{ expanded }">›</span>

      <span class="delegate-agent">
        {{ agentLabel }}
      </span>

      <span class="delegate-summary">
        {{ block.summary || block.content || "" }}
      </span>

      <span v-if="statusText" class="delegate-status">
        {{ statusText }}
      </span>
    </button>

    <!-- ==================== 人工确认 ==================== -->
    <div v-if="requiresApproval" class="approval-row">
      <span class="approval-text">
        {{ block.summary || "等待用户确认委派子 Agent" }}
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
          拒绝
        </button>
      </div>
    </div>

    <!-- ==================== 子 Agent 活动 ==================== -->
    <transition name="collapse">
      <div v-show="expanded" class="delegate-children">
        <!-- 子 Agent 尚未回传任何内容 -->
        <div v-if="!children.length" class="delegate-empty">
          {{
            status === "completed"
              ? "子 Agent 未返回可展示内容"
              : `等待 ${agentLabel} 返回结果…`
          }}
        </div>

        <template v-for="child in children" :key="child.id || child.timestamp">
          <!-- 子 Agent Narration -->
          <div v-if="child.type === 'narration'" class="child-narration">
            <VueMarkdown
              :source="child.content || child.summary || ''"
              :options="markdownOptions"
            />
          </div>

          <!-- 子 Agent Activity -->
          <div v-else class="child-activity">
            <span class="child-marker">↳</span>

            <div class="child-content">
              <AgentActionBlock v-if="child.type === 'action'" :block="child" />

              <FileChangeBlock
                v-else-if="child.type === 'file_change'"
                :block="child"
                @open="emit('open-file-change', $event)"
              />

              <ReviewBlock v-else-if="child.type === 'review'" :block="child" />

              <!-- 理论上不会有嵌套委派，兜底渲染 -->
              <DelegateBlock
                v-else-if="child.type === 'delegate'"
                :block="child"
                @open-file-change="emit('open-file-change', $event)"
              />

              <div v-else-if="child.type === 'status'" class="status-activity">
                <span class="status-marker">■</span>

                <span class="status-text">
                  {{ child.content || child.summary || child.title }}
                </span>
              </div>

              <span v-else class="child-text">
                {{ child.content || child.summary || child.title }}
              </span>
            </div>
          </div>
        </template>
      </div>
    </transition>
  </div>
</template> 
 
<script setup>
import { computed, ref } from "vue";
import VueMarkdown from "vue-markdown-render";
import hljs from "highlight.js";

import AgentActionBlock from "./AgentActionBlock.vue";
import FileChangeBlock from "./FileChangeBlock.vue";
import ReviewBlock from "./ReviewBlock.vue";

import { useTaskStore } from "../stores/task";

const props = defineProps({
  block: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(["open-file-change"]);

const taskStore = useTaskStore();

/*
 * Delegate 默认收起。
 *
 * 子 Agent 内部可能产生大量：
 * - THINK
 * - Tool Call
 * - Tool Result
 * - 文件读取
 * - 搜索
 * - 验证
 *
 * 这些属于 Agent 的执行过程。
 *
 * 对用户默认展示压缩后的 summary。
 * 用户需要查看详细执行过程时，再手动展开。
 */
const expanded = ref(false);
const approvalLoading = ref(false);

const status = computed(() => {
  return props.block?.status || "completed";
});

const agentLabel = computed(() => {
  return props.block?.delegateAgent || props.block?.agentName || "子 Agent";
});

const children = computed(() => {
  const list = props.block?.children;

  return Array.isArray(list) ? list : [];
});

const requiresApproval = computed(() => {
  return (
    props.block?.requiresApproval === true &&
    props.block?.status === "waiting" &&
    !!props.block?.actionId
  );
});

const statusText = computed(() => {
  return (
    {
      waiting: "等待确认",
      running: "进行中",
      failed: "失败",
    }[status.value] || ""
  );
});

const markdownOptions = {
  html: false,
  breaks: true,
  linkify: true,

  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, {
          language: lang,
        }).value;
      } catch (e) {
        console.warn("Markdown code highlight failed:", e);
      }
    }

    return hljs.highlightAuto(code).value;
  },
};

function toggle() {
  expanded.value = !expanded.value;
}

async function approve() {
  if (!requiresApproval.value || approvalLoading.value) {
    return;
  }

  approvalLoading.value = true;

  try {
    await taskStore.approveAction(props.block.runId, props.block.actionId);
  } catch (error) {
    console.error("[DelegateBlock] 批准失败:", error);
  } finally {
    approvalLoading.value = false;
  }
}

async function reject() {
  if (!requiresApproval.value || approvalLoading.value) {
    return;
  }

  approvalLoading.value = true;

  try {
    await taskStore.rejectAction(props.block.runId, props.block.actionId);
  } catch (error) {
    console.error("[DelegateBlock] 拒绝失败:", error);
  } finally {
    approvalLoading.value = false;
  }
}
</script> 
 
<style scoped>
.delegate-block {
  margin: 4px 0;
}

/* ============================================================ 
   Delegate 主行 
============================================================ */

.delegate-row {
  display: flex;
  align-items: baseline;
  gap: 6px;

  width: 100%;

  padding: 4px 0;

  border: none;

  background: transparent;

  color: var(--el-text-color-regular);

  font-family: inherit;
  font-size: 11px;
  font-weight: 600;

  text-align: left;

  cursor: pointer;
}

.delegate-chevron {
  display: inline-block;

  color: var(--el-text-color-placeholder);

  transition: transform 0.15s ease;
}

.delegate-chevron.expanded {
  transform: rotate(90deg);
}

.delegate-agent {
  color: var(--el-color-primary);

  white-space: nowrap;
}

.delegate-summary {
  flex: 1;

  font-weight: 400;

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delegate-status {
  color: var(--el-text-color-placeholder);

  font-size: 10px;
  font-weight: 400;

  white-space: nowrap;
}

.is-failed .delegate-agent,
.is-failed .delegate-summary {
  color: var(--el-color-danger);
}

.is-waiting .delegate-summary {
  color: var(--el-text-color-secondary);
}

/* ============================================================ 
   子 Agent 容器 
============================================================ */

.delegate-children {
  margin-left: 10px;
  padding-left: 10px;

  border-left: 1px solid var(--el-border-color-lighter);
}

.delegate-empty {
  padding: 3px 0;

  color: var(--el-text-color-placeholder);

  font-size: 11px;
}

.child-narration {
  padding: 2px 0 4px;

  color: var(--el-text-color-regular);

  font-size: 12px;
  line-height: 1.7;

  overflow-wrap: anywhere;
}

.child-activity {
  display: flex;
  align-items: flex-start;
  gap: 6px;

  padding: 1px 0;
}

.child-marker {
  flex: none;

  color: var(--el-text-color-placeholder);

  font-size: 11px;

  line-height: 18px;
}

.child-content {
  flex: 1;

  min-width: 0;
}

.child-text {
  color: var(--el-text-color-secondary);

  font-size: 11px;
  line-height: 1.6;
}

.status-activity {
  display: flex;
  align-items: flex-start;
  gap: 6px;

  padding: 2px 0;

  color: var(--el-text-color-secondary);

  font-size: 11px;
}

/* ============================================================ 
   Approval 
============================================================ */

.approval-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  margin: 4px 0;
  padding: 6px 8px;

  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;

  background: var(--el-fill-color-lighter);
}

.approval-text {
  color: var(--el-text-color-regular);

  font-size: 11px;
  line-height: 1.6;
}

.approval-actions {
  display: flex;
  gap: 6px;

  flex: none;
}

.approval-button {
  padding: 2px 10px;

  border: 1px solid var(--el-border-color);
  border-radius: 4px;

  background: transparent;

  color: var(--el-text-color-regular);

  font-family: inherit;
  font-size: 11px;

  cursor: pointer;
}

.approval-button:disabled {
  opacity: 0.6;

  cursor: not-allowed;
}

.approval-button.approve {
  border-color: var(--el-color-primary);

  color: var(--el-color-primary);
}

.approval-button.reject {
  border-color: var(--el-color-danger);

  color: var(--el-color-danger);
}

/* ============================================================ 
   Collapse 
============================================================ */

.collapse-enter-active,
.collapse-leave-active {
  overflow: hidden;

  transition: max-height 0.15s ease, opacity 0.15s ease;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style> 
<template>
  <section class="chat-shell">
    <div ref="scrollEl" class="chat-scroll">
      <!-- 空状态 -->
      <div v-if="!chat || !chat.turns?.length" class="chat-empty">
        <div class="empty-mark">✦</div>
        <div class="empty-title">开始与 Agent 工作</div>
        <div class="empty-subtitle">提出需求，Agent 会在这里展示工作过程。</div>
      </div>

      <div v-else class="chat-content">
        <section
          v-for="turn in chat.turns"
          :key="turn.taskId"
          class="chat-turn"
        >
          <!-- ==================== User ==================== -->
          <section v-if="turn.user?.content" class="user-turn">
            <div class="user-text">
              {{ turn.user.content }}
            </div>
          </section>

          <!-- ==================== Agent ==================== -->
          <section v-if="turn.agent" class="agent-turn">
            <div class="agent-heading">
              <span class="agent-mark">✦</span>
              <span>
                {{ turn.agent.agentName || "Agent" }}
              </span>
            </div>

            <!-- ==================== Task Activity ==================== -->
            <div v-if="turn.phases?.length" class="task-activity">
              <!--
               * Phase 仅作为后端聚合容器。
               *
               * 前端不展示：
               * - Phase Header
               * - Phase Title
               * - Phase Status
               * - Phase Toggle
               *
               * 直接按 activities 原始顺序展示。
               -->
              <template v-for="phase in turn.phases" :key="phase.id">
                <template
                  v-for="activity in phase.activities || []"
                  :key="`${phase.id}:${activity.id}`"
                >
                  <!-- ==================== Agent Narration ==================== -->
                  <div
                    v-if="activity.type === 'narration'"
                    class="agent-narration"
                  >
                    <VueMarkdown
                      :source="activity.content || activity.summary || ''"
                      :options="markdownOptions"
                    />
                  </div>

                  <!-- ==================== Activity ==================== -->
                  <div v-else class="phase-activity">
                    <span class="activity-marker">↳</span>

                    <div class="activity-content">
                      <AgentActionBlock
                        v-if="activity.type === 'action'"
                        :block="activity"
                      />

                      <FileChangeBlock
                        v-else-if="activity.type === 'file_change'"
                        :block="activity"
                        @open="emit('open-file-change', $event)"
                      />

                      <ReviewBlock
                        v-else-if="activity.type === 'review'"
                        :block="activity"
                      />

                      <div
                        v-else-if="activity.type === 'status'"
                        class="status-activity"
                      >
                        <span class="status-marker">■</span>

                        <span class="status-text">
                          {{
                            activity.content ||
                            activity.summary ||
                            activity.title
                          }}
                        </span>
                      </div>
                    </div>
                  </div>
                </template>
              </template>
            </div>

            <!--
             * 临时兼容 realtime。
             *
             * 当 realtime Phase 尚未返回时，
             * 直接使用 turn.agent.blocks。
             *
             * BLOCK_APPEND / BLOCK_UPDATE
             * 最终仍会被 Java Phase 快照替代。
             -->
            <div v-else-if="turn.agent.blocks?.length" class="activity-list">
              <template
                v-for="block in turn.agent.blocks || []"
                :key="block.id"
              >
                <!-- Agent Narration -->
                <div v-if="block.type === 'narration'" class="agent-narration">
                  <VueMarkdown
                    :source="block.content || block.summary || ''"
                    :options="markdownOptions"
                  />
                </div>
                <!-- Action -->
                <div v-else class="phase-activity">
                  <span class="activity-marker">↳</span>

                  <div class="activity-content">
                    <AgentActionBlock
                      v-if="block.type === 'action'"
                      :block="block"
                    />

                    <FileChangeBlock
                      v-else-if="block.type === 'file_change'"
                      :block="block"
                      @open="emit('open-file-change', $event)"
                    />

                    <ReviewBlock
                      v-else-if="block.type === 'review'"
                      :block="block"
                    />

                    <div
                      v-else-if="block.type === 'status'"
                      class="status-activity"
                    >
                      <span class="status-marker">■</span>

                      <span class="status-text">
                        {{ block.content || block.summary || block.title }}
                      </span>
                    </div>
                  </div>
                </div>
              </template>
            </div>

            <!-- ==================== Running ==================== -->
            <div v-if="running && turn.runId === activeRunId" class="live-line">
              <span class="live-dot"></span>
              <span>正在工作</span>
            </div>
          </section>
        </section>
      </div>
    </div>

    <!-- ==================== Composer ==================== -->
    <div class="composer-wrap">
      <form class="composer" @submit.prevent="emit('send')">
        <textarea
          :value="message"
          rows="1"
          placeholder="告诉 Agent 下一步做什么…"
          @input="emit('update:message', $event.target.value)"
          @keydown.ctrl.enter.prevent="emit('send')"
        />

        <div class="composer-footer">
          <div class="composer-options">
            <span class="composer-hint"> Ctrl + Enter 发送 </span>

            <select
              v-model="permissionProfile"
              class="permission-select"
              :disabled="running || sending"
              title="Agent 权限"
            >
              <option value="READ_ONLY">只读</option>
              <option value="WORKSPACE">工作区</option>
              <option value="FULL_AUTO">自动执行</option>
            </select>
          </div>

          <button
            v-if="running"
            type="button"
            class="stop-button"
            :disabled="stopping"
            title="暂停 Agent"
            @click="handleStop"
          >
            <span v-if="stopping">…</span>
            <span v-else>■</span>
          </button>

          <button
            v-else
            type="submit"
            :disabled="sending || !message?.trim()"
            title="发送"
          >
            <span v-if="sending">…</span>
            <span v-else>↑</span>
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from "vue";

import AgentActionBlock from "./AgentActionBlock.vue";
import FileChangeBlock from "./FileChangeBlock.vue";
import ReviewBlock from "./ReviewBlock.vue";
import VueMarkdown from "vue-markdown-render";
import hljs from "highlight.js";
const props = defineProps({
  chat: {
    type: Object,
    default: null,
  },

  running: {
    type: Boolean,
    default: false,
  },

  activeRunId: {
    type: String,
    default: "",
  },

  message: {
    type: String,
    default: "",
  },

  sending: {
    type: Boolean,
    default: false,
  },

  stopping: {
    type: Boolean,
    default: false,
  },

  permissionProfile: {
    type: String,
    default: "WORKSPACE",
  },
});

const emit = defineEmits([
  "update:message",
  "send",
  "stop",
  "open-file-change",
  "update:permission-profile",
]);

const scrollEl = ref(null);

const permissionProfile = computed({
  get() {
    return props.permissionProfile;
  },

  set(value) {
    emit("update:permission-profile", value);
  },
});

function handleStop() {
  emit("stop");
}

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

/**
 * AgentChat 内容变化时自动滚动到底部。
 *
 * 这里保留原来的逻辑：
 * - 用户发送消息
 * - SSE 新增 narration
 * - SSE 新增/更新 action
 * - Phase 快照更新
 * - 最终结果刷新
 *
 * 都会自动保持在最新位置。
 */
watch(
  () => props.chat,
  async () => {
    await nextTick();

    if (scrollEl.value) {
      scrollEl.value.scrollTop = scrollEl.value.scrollHeight;
    }
  },
  {
    deep: true,
  }
);
</script>

<style scoped>
.chat-shell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--page);
}

.chat-scroll {
  flex: 1;
  overflow: auto;
  scrollbar-gutter: stable;
}

.chat-content {
  width: min(820px, calc(100% - 56px));
  margin: auto;
  padding: 42px 0 28px;
}

/* ==================== Turn ==================== */

.chat-turn {
  padding-bottom: 42px;
}

/* ==================== User ==================== */

.user-turn {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.user-text {
  max-width: 72%;
  padding: 11px 15px;
  border-radius: 14px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ==================== Agent ==================== */

.agent-turn {
  width: 100%;
}

.agent-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.agent-mark {
  color: var(--el-text-color-secondary);
}

/* ==================== Task Activity ==================== */

.task-activity {
  width: 100%;
}

/*
 * Phase 现在只是数据容器，
 * 不再承担 UI 层级。
 */
.phase-item {
  width: 100%;
}

/* ==================== Narration ==================== */

.agent-narration {
  margin: 0 0 12px;
  padding: 0 0 0 0;

  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.75;

  word-break: break-word;
}

/* 普通段落 */
.agent-narration :deep(p) {
  margin: 0 0 8px;
}

.agent-narration :deep(p:last-child) {
  margin-bottom: 0;
}

/* 标题 */
.agent-narration :deep(h1),
.agent-narration :deep(h2),
.agent-narration :deep(h3),
.agent-narration :deep(h4) {
  margin: 12px 0 6px;
  color: var(--el-text-color-primary);
  font-weight: 600;
  line-height: 1.5;
}

.agent-narration :deep(h1:first-child),
.agent-narration :deep(h2:first-child),
.agent-narration :deep(h3:first-child),
.agent-narration :deep(h4:first-child) {
  margin-top: 0;
}

.agent-narration :deep(h1) {
  font-size: 16px;
}

.agent-narration :deep(h2) {
  font-size: 15px;
}

.agent-narration :deep(h3),
.agent-narration :deep(h4) {
  font-size: 14px;
}

/* 列表 */
.agent-narration :deep(ul),
.agent-narration :deep(ol) {
  margin: 5px 0 8px;
  padding-left: 21px;
}

.agent-narration :deep(li) {
  margin: 2px 0;
}

/* 行内代码 */
.agent-narration :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;

  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);

  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 0.9em;
}

/* 代码块 */
.agent-narration :deep(pre) {
  margin: 8px 0 10px;
  padding: 11px 13px;

  overflow-x: auto;

  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;

  background: var(--el-fill-color-light);

  line-height: 1.55;
}

.agent-narration :deep(pre code) {
  padding: 0;
  background: transparent;

  font-size: 12.5px;
}

/* 引用 */
.agent-narration :deep(blockquote) {
  margin: 7px 0;
  padding: 2px 12px;

  border-left: 3px solid var(--el-border-color);
  color: var(--el-text-color-secondary);
}

/* 分割线 */
.agent-narration :deep(hr) {
  margin: 10px 0;
  border: 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 链接 */
.agent-narration :deep(a) {
  color: var(--el-color-primary);
  text-decoration: none;
}

.agent-narration :deep(a:hover) {
  text-decoration: underline;
}

/* 加粗 */
.agent-narration :deep(strong) {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

/* 表格 */
.agent-narration :deep(table) {
  width: 100%;
  margin: 8px 0 10px;

  border-collapse: collapse;

  font-size: 13px;
}

.agent-narration :deep(th),
.agent-narration :deep(td) {
  padding: 6px 9px;
  border: 1px solid var(--el-border-color-lighter);
  text-align: left;
}

.agent-narration :deep(th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}

/* ==================== Activity ==================== */

.phase-activity {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  padding: 1px 0 1px 18px;
  color: var(--el-text-color-secondary);
}

.activity-marker {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  line-height: 20px;
}

.activity-content {
  min-width: 0;
  flex: 1;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 20px;
}

.status-activity {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.status-marker {
  flex: 0 0 auto;
  color: var(--el-color-warning);
  font-size: 8px;
}

.status-text {
  line-height: 20px;
}

/*
 * Realtime fallback。
 *
 * 等 Java realtime Phase 快照稳定以后，
 * 这一块可以继续保留作为兼容，
 * 也可以后续删除。
 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 4px;
}

/* ==================== Running ==================== */

.live-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}

/* ==================== Composer ==================== */

.composer-wrap {
  padding: 14px 20px 20px;
}

.composer {
  width: min(820px, calc(100% - 56px));
  margin: 0 auto;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  background: var(--el-bg-color);
  box-shadow: 0 2px 10px rgb(0 0 0 / 4%);
}

.composer textarea {
  width: 100%;
  min-height: 72px;
  padding: 14px 16px 8px;

  resize: vertical;

  border: 0;
  outline: none;

  background: transparent;
  color: var(--el-text-color-primary);

  font: inherit;
  line-height: 1.6;

  box-sizing: border-box;
}

.composer textarea::placeholder {
  color: var(--el-text-color-placeholder);
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px 10px;
}

.composer-options {
  display: flex;
  align-items: center;
  gap: 10px;
}

.composer-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.permission-select {
  height: 28px;
  padding: 0 8px;

  border: 1px solid var(--el-border-color);
  border-radius: 7px;

  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);

  font-size: 12px;
}

.composer-footer button {
  width: 32px;
  height: 32px;

  display: inline-flex;
  align-items: center;
  justify-content: center;

  border: 0;
  border-radius: 9px;

  background: var(--el-color-primary);
  color: #fff;

  cursor: pointer;
}

.composer-footer button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.stop-button {
  background: var(--el-fill-color-dark) !important;
  color: var(--el-text-color-regular) !important;
}

/* ==================== Empty ==================== */

.chat-empty {
  height: 100%;

  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  text-align: center;
}

.empty-mark {
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
  font-size: 34px;
}

.empty-title {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 600;
}

.empty-subtitle {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.9);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
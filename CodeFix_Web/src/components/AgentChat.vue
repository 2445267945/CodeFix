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
            <div class="user-text">{{ turn.user.content }}</div>
          </section>

          <!-- ==================== Agent ==================== -->
          <section v-if="turn.agent" class="agent-turn">
            <div class="agent-heading">
              <span class="agent-mark">✦</span>
              <span>{{ turn.agent.agentName || "Agent" }}</span>
            </div>

            <!-- ==================== Task Activity ==================== -->
            <div v-if="turn.phases?.length" class="task-activity">
              <section
                v-for="phase in turn.phases"
                :key="phase.id"
                class="phase-item"
              >
                <!-- Phase Header -->
                <button
                  type="button"
                  class="phase-header"
                  @click="togglePhase(phase.id)"
                >
                  <span
                    class="phase-status"
                    :class="phaseStatusClass(phase.status)"
                  >
                    <span v-if="phase.status === 'completed'">✓</span>
                    <span v-else-if="phase.status === 'failed'">×</span>
                    <span v-else-if="phase.status === 'waiting'">Ⅱ</span>
                    <span v-else class="running-dot">●</span>
                  </span>

                  <span class="phase-title">
                    {{ phase.title || "Agent 工作" }}
                  </span>

                  <span v-if="phase.activities?.length" class="phase-toggle">
                    {{ isPhaseExpanded(phase.id) ? "⌃" : "⌄" }}
                  </span>
                </button>

                <!-- Phase Activities -->
                <div v-if="isPhaseExpanded(phase.id)" class="phase-activities">
                  <template
                    v-for="activity in phase.activities || []"
                    :key="`${phase.id}:${activity.id}`"
                  >
                    <div class="phase-activity">
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
                </div>
              </section>
            </div>

            <!--
             * 临时兼容 realtime。
             *
             * 当前 realtime Phase 还没有实现时，
             * 继续使用原来的 blocks 展示实时 Agent Activity。
             *
             * 等 Java realtime Phase 完成后，
             * 这一段可以删除。
             -->
            <div v-else-if="turn.agent.blocks?.length" class="activity-list">
              <template
                v-for="block in turn.agent.blocks || []"
                :key="block.id"
              >
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
              </template>
            </div>
            <!-- ==================== Final Answer ==================== -->
            <FinalAnswerBlock
              v-if="turn.agent.finalAnswer"
              :answer="turn.agent.finalAnswer"
            />

            <!-- ==================== Running ==================== -->
            <div v-if="running && turn.runId === activeRunId" class="live-line">
              <span class="live-dot"></span>
              <span>正在工作</span>
            </div>
          </section>
        </section>
      </div>
    </div>

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
            <span class="composer-hint">Ctrl + Enter 发送</span>

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
import FinalAnswerBlock from "./FinalAnswerBlock.vue";

const props = defineProps({
  chat: { type: Object, default: null },
  running: { type: Boolean, default: false },
  activeRunId: { type: String, default: "" },
  message: { type: String, default: "" },
  sending: { type: Boolean, default: false },
  stopping: { type: Boolean, default: false },
  permissionProfile: { type: String, default: "WORKSPACE" },
});

const emit = defineEmits([
  "update:message",
  "send",
  "stop",
  "open-file-change",
  "update:permission-profile",
]);

const scrollEl = ref(null);

/**
 * 当前展开的 Phase。
 */
const expandedPhases = ref(new Set());

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

function isPhaseExpanded(phaseId) {
  return expandedPhases.value.has(phaseId);
}

function togglePhase(phaseId) {
  const next = new Set(expandedPhases.value);

  if (next.has(phaseId)) {
    next.delete(phaseId);
  } else {
    next.add(phaseId);
  }

  expandedPhases.value = next;
}

function phaseStatusClass(status) {
  return {
    "is-completed": status === "completed",
    "is-running": status === "running",
    "is-waiting": status === "waiting",
    "is-failed": status === "failed",
  };
}

/**
 * 同步 Phase 展开状态。
 *
 * 默认：
 * - running / waiting 自动展开
 * - 没有运行中 Phase 时，展开最后一个 Phase
 */
function syncExpandedPhases(phases) {
  if (!Array.isArray(phases) || !phases.length) {
    expandedPhases.value = new Set();
    return;
  }

  const validIds = new Set(phases.map((phase) => phase?.id).filter(Boolean));

  const next = new Set(
    [...expandedPhases.value].filter((id) => validIds.has(id))
  );

  // waiting Phase 始终自动展开
  for (const phase of phases) {
    if (phase?.id && phase.status === "waiting") {
      next.add(phase.id);
    }
  }

  if (next.size === 0) {
    // 没有 waiting 时，保持原来的默认展开逻辑
    for (const phase of phases) {
      if (phase?.id && phase.status === "running") {
        next.add(phase.id);
      }
    }

    if (next.size === 0) {
      const latestPhase = phases[phases.length - 1];

      if (latestPhase?.id) {
        next.add(latestPhase.id);
      }
    }
  }

  expandedPhases.value = next;
}

watch(
  () => props.chat?.turns,
  (turns) => {
    if (!Array.isArray(turns)) {
      expandedPhases.value = new Set();
      return;
    }

    const phases = turns.flatMap((turn) =>
      Array.isArray(turn?.phases) ? turn.phases : []
    );

    syncExpandedPhases(phases);
  },
  {
    immediate: true,
    deep: true,
  }
);

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

.phase-item {
  position: relative;
}

/* ==================== Phase ==================== */

.phase-header {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 5px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.phase-header:hover {
  background: var(--el-fill-color-light);
}

.phase-status {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.phase-status.is-completed {
  color: var(--el-color-success);
}

.phase-status.is-running {
  color: var(--el-color-primary);
}

.phase-status.is-waiting {
  color: var(--el-color-warning);
}

.phase-status.is-failed {
  color: var(--el-color-danger);
}

.running-dot {
  font-size: 8px;
}

.phase-title {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
}

.phase-toggle {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

/* ==================== Activity ==================== */

.phase-activities {
  margin-left: 14px;
  padding: 3px 0 5px 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.phase-activity {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  min-height: 30px;
}

.activity-marker {
  flex: 0 0 auto;
  padding-top: 5px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.activity-content {
  min-width: 0;
  flex: 1;
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
 * 兼容 realtime 的旧 Activity 展示。
 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* ==================== Final Answer ==================== */

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
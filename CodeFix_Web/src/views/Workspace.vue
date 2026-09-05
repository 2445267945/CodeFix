<template>
  <div class="workspace">
    <!-- =========================================================
         Topbar
    ========================================================== -->
    <header class="topbar">
      <div class="topbar-left">
        <button
          class="icon-btn"
          type="button"
          title="新建对话"
          @click="startNewConversation"
        >
          +
        </button>

        <div class="brand">
          <span class="brand-mark">✦</span>
          <span class="brand-name">Agent IDE</span>
        </div>

        <div class="topbar-divider"></div>

        <div class="workspace-context">
          <span class="workspace-context-name">
            {{ workspaceName }}
          </span>

          <span v-if="sessionId" class="workspace-context-session">
            {{ sessionShortName }}
          </span>
        </div>
      </div>

      <div class="topbar-right">
        <span class="run-state" :class="statusClass">
          <span class="state-dot"></span>
          {{ statusLabel }}
        </span>

        <button
          v-if="taskStore.canResume"
          class="text-btn"
          :disabled="taskStore.operating"
          @click="handleResume"
        >
          继续
        </button>

        <button
          v-else-if="taskStore.canRetry"
          class="text-btn"
          :disabled="taskStore.operating"
          @click="handleRetry"
        >
          重试
        </button>
      </div>
    </header>

    <!-- =========================================================
         Main
    ========================================================== -->
    <main class="main" :style="mainGridStyle">
      <!-- =======================================================
       Sidebar
  ======================================================== -->
      <aside class="sidebar">
        <!-- =====================================================
         Conversations = Session List
    ====================================================== -->
        <section
          class="sidebar-section conversations-section"
          :class="{
            collapsed: conversationsCollapsed,
          }"
        >
          <button
            class="section-header"
            type="button"
            @click="conversationsCollapsed = !conversationsCollapsed"
          >
            <span class="section-title"> CONVERSATIONS </span>

            <span
              class="section-chevron"
              :class="{
                expanded: !conversationsCollapsed,
              }"
            >
              ›
            </span>
          </button>

          <div v-if="!conversationsCollapsed" class="section-content">
            <button
              class="new-conversation"
              type="button"
              @click="startNewConversation"
            >
              <span class="new-conversation-icon"> + </span>
              <span>新建对话</span>
            </button>

            <div v-if="sessions.length" class="conversation-list">
              <button
                v-for="session in sessions"
                :key="session.sessionId"
                class="conversation-item"
                :class="{
                  active: session.sessionId === currentSessionId,
                }"
                type="button"
                @click="selectSession(session.sessionId)"
              >
                <span
                  class="conversation-status"
                  :class="{
                    active: session.sessionId === currentSessionId,
                  }"
                ></span>

                <span class="conversation-copy">
                  <span class="conversation-title">
                    {{ session.title || "新对话" }}
                  </span>

                  <span class="conversation-meta">
                    {{ formatSessionTime(session.updatedAt) }}
                  </span>
                </span>
              </button>
            </div>

            <div v-else class="sidebar-empty">暂无历史对话</div>
          </div>
        </section>

        <!-- =====================================================
         Files = 当前 Session 对应 Workspace
    ====================================================== -->
        <section
          class="sidebar-section files-section"
          :class="{
            collapsed: filesCollapsed,
          }"
        >
          <button
            class="section-header"
            type="button"
            @click="filesCollapsed = !filesCollapsed"
          >
            <span class="section-title"> FILES </span>

            <div class="section-actions">
              <button
                class="section-icon-btn"
                type="button"
                title="刷新文件树"
                @click.stop="refreshWorkspace"
              >
                ↻
              </button>

              <span
                class="section-chevron"
                :class="{
                  expanded: !filesCollapsed,
                }"
              >
                ›
              </span>
            </div>
          </button>

          <div v-if="!filesCollapsed" class="section-content files-content">
            <div v-if="workspaceId" class="workspace-root">
              <span class="workspace-root-icon"> ▾ </span>

              <span class="workspace-root-name">
                {{ workspaceName }}
              </span>
            </div>

            <div v-else class="workspace-root empty">
              <span class="workspace-root-icon">＋</span>

              <span class="workspace-root-name"> 未关联项目 </span>
            </div>

            <div class="tree-host">
              <WorkspaceTree
                :tree="fileTree"
                :selected-file="selectedFile"
                @select="handleSelectFile"
              />

              <div v-if="fileTreeLoading" class="tree-hint">
                正在读取文件树…
              </div>

              <div v-else-if="!fileTree.length" class="tree-hint">
                <span>
                  {{
                    workspaceId
                      ? "当前 Workspace 暂无文件"
                      : "当前对话还没有关联项目"
                  }}
                </span>

                <span v-if="!workspaceId"> 可在对话中添加或创建项目。 </span>
              </div>
            </div>

            <div v-if="selectedFile" class="selected-path">
              <span>已选中</span>

              <code>
                {{ selectedFile }}
              </code>
            </div>
          </div>
        </section>
      </aside>

      <div
        class="sidebar-resize-handle"
        role="separator"
        aria-label="调整侧边栏宽度"
        aria-orientation="vertical"
        @pointerdown="startSidebarResize"
      >
        <span class="sidebar-resize-grip"></span>
      </div>

      <!-- =======================================================
       Main Content = Tab Workspace
  ======================================================== -->
      <section class="content-pane">
        <WorkspaceTabView
          ref="workspaceTabViewRef"
          :chat="chat"
          :running="taskStore.isRunning"
          :stopping="taskStore.operating"
          :active-run-id="taskStore.runId"
          :workspace-id="workspaceId"
          :workspace-name="workspaceName"
          v-model:message="message"
          v-model:permission-profile="permissionProfile"
          :sending="sending"
          @send="sendMessage"
          @stop="handleCancel"
          @open-file-change="handleFileChangeClick"
          @workspace-name-change="handleWorkspaceNameChange"
        />
      </section>

      <DiffViewer
        v-model="diffVisible"
        :diff="currentDiff"
        :loading="diffLoading"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

import { ElMessage, ElMessageBox } from "element-plus";

import { useTaskStore } from "../stores/task";

import { useSessionStore } from "../stores/session";
import WorkspaceTabView from "../components/WorkspaceTabView.vue";
import WorkspaceTree from "../components/WorkspaceTree.vue";
import { getWorkspaceTree } from "../api/workspace";
import { getFileDiff } from "../api/diff";
import DiffViewer from "../components/DiffViewer.vue";

const taskStore = useTaskStore();
const sessionStore = useSessionStore();

/* ============================================================
   UI State
============================================================ */
const pendingWorkspaceName = ref("");
const workspaceTabViewRef = ref(null);
const message = ref("");

const sending = ref(false);

const selectedFile = ref("");

const fileTree = ref([]);

const fileTreeLoading = ref(false);

const conversationsCollapsed = ref(false);

const filesCollapsed = ref(false);

/**
 * 是否处于：
 *
 * 新建对话，但尚未发送第一条消息
 *
 * 此时：
 *
 * sessionId = ''
 * task = null
 * workspace = null
 */
const isDraftConversation = ref(false);
const currentWorkspaceNameForSend = computed(() => {
  if (workspaceId.value) {
    return "";
  }

  return pendingWorkspaceName.value.trim();
});
const permissionProfile = ref("WORKSPACE");

const SIDEBAR_WIDTH_KEY = "codefix.sidebar-width";
const SIDEBAR_DEFAULT_WIDTH = 264;
const SIDEBAR_MIN_WIDTH = 220;
const SIDEBAR_MAX_WIDTH = 520;

const storedSidebarWidth = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY));
const sidebarWidth = ref(
  Number.isFinite(storedSidebarWidth) &&
    storedSidebarWidth >= SIDEBAR_MIN_WIDTH &&
    storedSidebarWidth <= SIDEBAR_MAX_WIDTH
    ? storedSidebarWidth
    : SIDEBAR_DEFAULT_WIDTH
);
const mainGridStyle = computed(() => ({
  "--sidebar-width": `${sidebarWidth.value}px`,
}));

function clampSidebarWidth(width) {
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width));
}

function updateSidebarWidth(event) {
  const main = document.querySelector(".main");
  if (!main) return;
  const { left } = main.getBoundingClientRect();
  sidebarWidth.value = clampSidebarWidth(event.clientX - left);
}

function finishSidebarResize() {
  document.body.classList.remove("is-resizing-sidebar");
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(sidebarWidth.value));
  window.removeEventListener("pointermove", updateSidebarWidth);
  window.removeEventListener("pointerup", finishSidebarResize);
}

function startSidebarResize(event) {
  event.preventDefault();
  document.body.classList.add("is-resizing-sidebar");
  updateSidebarWidth(event);
  window.addEventListener("pointermove", updateSidebarWidth);
  window.addEventListener("pointerup", finishSidebarResize, { once: true });
}
/* ============================================================
   Session
============================================================ */

const sessions = computed(() => sessionStore.sessions);

const currentSessionId = computed(() => sessionStore.currentSessionId);

/* ============================================================
   Task / Chat
============================================================ */

const task = computed(() => taskStore.task);

const chat = computed(
  () => taskStore.result?.chat || sessionStore.chat || null
);

/**
 * 当前 Session
 *
 * 优先 Task，
 * 因为 Task 上也保存 sessionId。
 */
const sessionId = computed(
  () =>
    currentSessionId.value || taskStore.sessionId || chat.value?.sessionId || ""
);

/**
 * 当前 Task
 */
const currentTaskId = computed(
  () => taskStore.taskId || chat.value?.taskId || ""
);

/**
 * 当前 Run
 */
const currentRunId = computed(() => taskStore.runId || chat.value?.runId || "");

/**
 * 当前 Session 绑定的 Workspace。
 *
 * Workspace 可以为空。
 * 一个 Workspace 可以被多个 Session 使用。
 */
const workspaceId = computed(
  () =>
    chat.value?.workspaceId ||
    task.value?.workspaceId ||
    task.value?.workspace?.workspaceId ||
    ""
);

const workspaceName = computed(
  () =>
    pendingWorkspaceName.value ||
    chat.value?.workspaceName ||
    task.value?.workspaceName ||
    task.value?.workspace?.name ||
    ""
);

const sessionShortName = computed(() => {
  if (!sessionId.value) {
    return "新对话";
  }

  if (sessionId.value.length <= 16) {
    return sessionId.value;
  }

  return `${sessionId.value.slice(0, 8)}…` + `${sessionId.value.slice(-4)}`;
});

function handleWorkspaceNameChange(name) {
  pendingWorkspaceName.value = name?.trim() || "";
}
/* ============================================================
   Status
============================================================ */

const statusLabel = computed(() => {
  if (isDraftConversation.value) {
    return "新对话";
  }

  if (taskStore.isRunning) {
    return "工作中";
  }

  if (taskStore.isCompleted) {
    return "已完成";
  }

  if (taskStore.statusValue === "CANCELLED") {
    return "已取消";
  }

  if (taskStore.statusValue === "ERROR") {
    return "执行失败";
  }

  return taskStore.statusValue || "就绪";
});

const statusClass = computed(() => {
  if (isDraftConversation.value) {
    return "draft";
  }

  if (taskStore.isRunning) {
    return "working";
  }

  if (taskStore.isCompleted) {
    return "done";
  }

  if (taskStore.statusValue === "CANCELLED") {
    return "cancelled";
  }

  if (taskStore.statusValue === "ERROR") {
    return "error";
  }

  return "idle";
});

/* ============================================================
   Session
============================================================ */

/**
 * 打开一个历史 Session。
 *
 * 流程：
 *
 * Session
 *   ↓
 * Session Chat
 *   ↓
 * 当前 Task / Run
 *   ↓
 * Workspace
 *   ↓
 * SSE
 */
async function selectSession(targetSessionId) {
  console.log("[Workspace] 点击 Session:", targetSessionId);

  if (!targetSessionId) {
    console.log("[Workspace] targetSessionId 为空，结束");
    return;
  }

  console.log("[Workspace] 当前 Session:", currentSessionId.value);

  console.log("[Workspace] 目标 Session:", targetSessionId);

  if (targetSessionId === currentSessionId.value) {
    console.log("[Workspace] 目标 Session 已经是当前 Session，结束");
    return;
  }

  console.log("[Workspace] 开始打开 Session");

  taskStore.disconnectSse();

  console.log("[Workspace] disconnectSse 完成");

  isDraftConversation.value = false;

  selectedFile.value = "";

  fileTree.value = [];

  console.log("[Workspace] 本地 Workspace 状态已清空");

  try {
    /*
     * ========================================================
     * 1. 查询整个 Session 的 Chat
     * ========================================================
     */
    console.log(
      "[Workspace] 准备调用 sessionStore.openSession:",
      targetSessionId
    );

    const chatView = await sessionStore.openSession(targetSessionId);

    console.log("[Workspace] sessionStore.openSession 返回:", chatView);

    if (!chatView) {
      console.error("[Workspace] Session Chat 查询为空");

      throw new Error("Session Chat 查询为空");
    }

    console.log("[Workspace] Chat SessionId:", chatView.sessionId);

    console.log("[Workspace] Chat WorkspaceId:", chatView.workspaceId);

    console.log("[Workspace] Chat WorkspaceName:", chatView.workspaceName);

    console.log("[Workspace] Chat TaskId:", chatView.taskId);

    console.log("[Workspace] Chat RunId:", chatView.runId);

    /*
     * ========================================================
     * 2. 当前 Session Chat
     * ========================================================
     */
    taskStore.result = {
      taskId: chatView.taskId || "",
      runId: chatView.runId || "",
      status: "",
      chat: chatView,
    };

    console.log("[Workspace] taskStore.result 已更新:", taskStore.result);

    /*
     * ========================================================
     * 3. 当前 Session 没有 Task
     *
     * 说明这是一个空 Session。
     * 但它仍然可能已经绑定 Workspace。
     * ========================================================
     */
    if (!chatView.taskId) {
      console.log("[Workspace] 当前 Session 没有 Task");

      taskStore.task = null;
      taskStore.runs = [];
      taskStore.viewedRunId = null;
      taskStore.events = [];
      taskStore.currentRunId = null;

      console.log("[Workspace] 开始加载 Workspace Tree");

      console.log("[Workspace] 当前 workspaceId:", workspaceId.value);

      await loadWorkspaceTree();

      console.log("[Workspace] 空 Session 的 Workspace Tree 加载完成");

      return;
    }

    /*
     * ========================================================
     * 4. 加载当前 Task
     * ========================================================
     */
    console.log("[Workspace] 开始加载 Task:", chatView.taskId);

    await taskStore.fetchTask(chatView.taskId);

    console.log("[Workspace] Task 加载完成:", taskStore.task);

    /*
     * ========================================================
     * 5. 当前 Run
     * ========================================================
     */
    const runId = chatView.runId || taskStore.task?.runId || "";

    console.log("[Workspace] 当前 Run:", runId);

    taskStore.currentRunId = runId || null;

    taskStore.viewedRunId = runId || null;

    /*
     * ========================================================
     * 6. 加载 Run 历史
     * ========================================================
     */
    if (runId) {
      console.log("[Workspace] 开始加载 Run 历史:", chatView.taskId);

      await taskStore.fetchRuns(chatView.taskId);

      console.log("[Workspace] Run 历史加载完成");
    }

    /*
     * ========================================================
     * 7. 加载 Workspace Tree
     * ========================================================
     */
    console.log("[Workspace] 准备加载 Workspace Tree");

    console.log("[Workspace] 当前 workspaceId:", workspaceId.value);

    console.log("[Workspace] 当前 workspaceName:", workspaceName.value);

    await loadWorkspaceTree();

    console.log("[Workspace] Workspace Tree 加载完成");

    console.log("[Workspace] 当前 fileTree:", fileTree.value);

    /*
     * ========================================================
     * 8. 当前 Task 开始接受实时事件
     * ========================================================
     */
    console.log("[Workspace] 准备连接 SSE:", chatView.taskId);

    taskStore.connectSse(chatView.taskId);

    console.log("[Workspace] Session 打开完成");
  } catch (error) {
    console.error("[Workspace] 打开 Session 失败:", error);

    console.error("[Workspace] Session Store error:", sessionStore.error);

    console.error("[Workspace] Task Store error:", taskStore.error);

    ElMessage.error(
      sessionStore.error || taskStore.error || error?.message || "打开对话失败"
    );
  }
}

/**
 * 新建对话。
 *
 * 当前只是创建前端草稿状态。
 * 不创建 Session / Workspace / Task / Run。
 *
 * 第一次发送消息时：
 * - Session 会被后端创建
 * - 如果本次带 workspaceName，则创建 Workspace 并绑定 Session
 * - 如果没有 workspaceName，则只创建 Session
 * - 然后创建 Task / Run / ChatMessage
 */
function startNewConversation() {
  taskStore.disconnectSse();

  sessionStore.newConversation();

  taskStore.task = null;

  taskStore.runs = [];

  taskStore.viewedRunId = null;

  taskStore.events = [];

  taskStore.result = null;

  taskStore.currentRunId = null;

  message.value = "";

  selectedFile.value = "";

  fileTree.value = [];

  isDraftConversation.value = true;
}

/* ============================================================
   Workspace
============================================================ */

async function loadWorkspaceTree() {
  if (!workspaceId.value) {
    fileTree.value = [];
    return;
  }

  fileTreeLoading.value = true;

  try {
    const response = await getWorkspaceTree(workspaceId.value);

    const data = response?.data ?? response;

    console.log("[Workspace] workspaceId:", workspaceId.value);

    console.log("[Workspace] workspaceTree response:", data);

    const tree = Array.isArray(data) ? data : data?.children;

    fileTree.value = Array.isArray(tree) ? tree : [];

    console.log("[Workspace] fileTree:", fileTree.value);
  } catch (error) {
    console.error("[Workspace] 获取文件树失败:", error);

    fileTree.value = [];
  } finally {
    fileTreeLoading.value = false;
  }
}

/* ============================================================
   Initial Loading
============================================================ */

async function loadWorkspace() {
  taskStore.disconnectSse();

  taskStore.task = null;

  taskStore.runs = [];

  taskStore.viewedRunId = null;

  taskStore.events = [];

  taskStore.result = null;

  taskStore.currentRunId = null;

  selectedFile.value = "";

  fileTree.value = [];

  isDraftConversation.value = true;

  try {
    /*
     * 页面进入：
     *
     * 只加载 Session 列表。
     *
     * 不自动创建 Session。
     * 不自动创建 Workspace。
     * 不自动创建 Task。
     */
    await sessionStore.fetchSessions();
  } catch (error) {
    console.error("[Workspace] 获取 Session 列表失败:", error);

    ElMessage.error(sessionStore.error || "加载历史对话失败");
  }
}

/* ============================================================
   Send Message
============================================================ */

async function sendMessage() {
  const content = message.value.trim();

  if (!content || sending.value) {
    return;
  }

  sending.value = true;

  try {
    /*
     * taskStore.sendMessage()
     *
     * sessionId：
     *
     * 新对话：
     *   ''
     *
     * 已有 Session：
     *   S001
     */
    const response = await taskStore.sendMessage(
      content,
      currentWorkspaceNameForSend.value,
      permissionProfile.value
    );

    if (response?.sessionId) {
      /*
       * 当前 Session 已经正式创建。
       *
       * Session Store 需要切换到这个 Session。
       */
      sessionStore.currentSessionId = response.sessionId;

      isDraftConversation.value = false;

      /*
       * 左侧 Session 列表重新加载。
       *
       * 因为第一次发送消息时：
       * Session 刚刚创建。
       */
      await sessionStore.fetchSessions();

      /*
       * 当前 Task 对应 Workspace
       */
      await taskStore.fetchTask(response.taskId);

      await loadWorkspaceTree();
    }

    message.value = "";
  } catch (error) {
    console.error("[Workspace] 发送消息失败:", error);

    ElMessage.error(taskStore.error || "发送消息失败");
  } finally {
    sending.value = false;
  }
}

/* ============================================================
   File
============================================================ */

const diffVisible = ref(false);
const diffLoading = ref(false);
const currentDiff = ref(null);

function handleSelectFile(path) {
  if (!path) {
    return;
  }

  if (!workspaceId.value) {
    ElMessage.warning("当前对话还没有关联项目");
    return;
  }

  selectedFile.value = path;

  workspaceTabViewRef.value?.openFile(path, path.split("/").pop());
}

async function handleFileChangeClick(block) {
  if (!block) {
    return;
  }

  if (block.filePath) {
    selectedFile.value = block.filePath;
  }

  if (!block.diffId) {
    ElMessage.warning("当前文件变更暂无 Diff");
    return;
  }

  diffVisible.value = true;
  diffLoading.value = true;
  currentDiff.value = null;

  try {
    const response = await getFileDiff(block.diffId);

    currentDiff.value = response;
  } catch (error) {
    console.error("[Workspace] Diff 查询失败:", error);

    diffVisible.value = false;

    ElMessage.error(
      error?.response?.data?.message || error?.message || "获取文件 Diff 失败"
    );
  } finally {
    diffLoading.value = false;
  }
}

async function refreshWorkspace() {
  try {
    if (currentTaskId.value) {
      await Promise.all([
        taskStore.fetchTask(currentTaskId.value),

        taskStore.fetchResult(currentTaskId.value, taskStore.viewedRunId),
      ]);
    }

    await loadWorkspaceTree();
  } catch (error) {
    console.error("[Workspace] 刷新失败:", error);

    ElMessage.error(taskStore.error || "刷新失败");
  }
}

/* ============================================================
   Task Operations
============================================================ */

async function handleCancel() {
  try {
    await ElMessageBox.confirm("确定停止当前 Run 吗？", "停止 Agent", {
      confirmButtonText: "停止",
      cancelButtonText: "返回",
      type: "warning",
    });

    await taskStore.cancel(currentTaskId.value, taskStore.runId);

    await taskStore.fetchResult(currentTaskId.value, taskStore.viewedRunId);

    ElMessage.success("已发送停止请求");
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(taskStore.error || "停止任务失败");
  }
}

async function handleRetry() {
  try {
    await ElMessageBox.confirm(
      "重新执行将创建一个新的 Run，是否继续？",
      "重新执行",
      {
        confirmButtonText: "继续",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    await taskStore.retry(currentTaskId.value);

    taskStore.viewedRunId = taskStore.task?.runId || taskStore.currentRunId;

    await Promise.all([
      taskStore.fetchRuns(currentTaskId.value),

      taskStore.fetchResult(currentTaskId.value, taskStore.viewedRunId),
    ]);

    taskStore.connectSse(currentTaskId.value);

    ElMessage.success("已创建新的执行 Run");
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(taskStore.error || "重新执行失败");
  }
}

async function handleResume() {
  try {
    await taskStore.resume(currentTaskId.value, taskStore.runId);

    taskStore.connectSse(currentTaskId.value);

    ElMessage.success("已继续执行");
  } catch (error) {
    console.error("[Workspace] 继续执行失败:", error);

    ElMessage.error(taskStore.error || "继续执行失败");
  }
}

/* ============================================================
   UI Helpers
============================================================ */

function formatSessionTime(timestamp) {
  if (!timestamp) {
    return "";
  }

  const time = new Date(timestamp).getTime();

  if (Number.isNaN(time)) {
    return "";
  }

  const diff = Date.now() - time;

  const minute = 60 * 1000;

  const hour = 60 * minute;

  const day = 24 * hour;

  if (diff < minute) {
    return "刚刚";
  }

  if (diff < hour) {
    return `${Math.floor(diff / minute)} 分钟前`;
  }

  if (diff < day) {
    return `${Math.floor(diff / hour)} 小时前`;
  }

  if (diff < 7 * day) {
    return `${Math.floor(diff / day)} 天前`;
  }

  return new Date(timestamp).toLocaleDateString();
}

/* ============================================================
   Lifecycle
============================================================ */

onMounted(() => {
  loadWorkspace();
});

onBeforeUnmount(() => {
  taskStore.disconnectSse();
  finishSidebarResize();
});
</script>

<style>
:root {
  --page: var(--cf-canvas);
  --surface: var(--cf-panel);
  --surface-soft: var(--cf-panel-raised);
  --surface-hover: var(--cf-panel-hover);
  --border: var(--cf-border);
  --border-subtle: var(--cf-border);

  --text: var(--cf-text);
  --text-soft: #c3cad7;
  --muted-strong: var(--cf-text-muted);
  --muted: var(--cf-text-subtle);

  --accent: var(--cf-accent);
  --accent-soft: var(--cf-accent-soft);

  --success: var(--cf-success);
  --success-soft: var(--cf-success-soft);

  --warning: var(--cf-warning);
  --warning-soft: var(--cf-warning-soft);

  --danger: var(--cf-danger);
  --danger-soft: var(--cf-danger-soft);
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
}

body {
  font-family: Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont,
    "Segoe UI", sans-serif;

  color: var(--text);
  background: var(--page);
}

button,
textarea,
input {
  font: inherit;
}

/* ============================================================
   Workspace
============================================================ */

.workspace {
  width: 100%;
  height: 100%;

  display: flex;
  flex-direction: column;

  min-width: 0;
  min-height: 0;

  overflow: hidden;

  background: var(--page);
}

/* ============================================================
   Topbar
============================================================ */

.topbar {
  height: 46px;
  flex: 0 0 46px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  min-width: 0;

  padding: 0 12px;

  border-bottom: 1px solid var(--border-subtle);

  background: rgba(255, 255, 255, 0.94);

  backdrop-filter: blur(12px);
}

.topbar-left,
.topbar-right {
  min-width: 0;

  display: flex;
  align-items: center;

  gap: 8px;
}

.icon-btn {
  width: 28px;
  height: 28px;

  display: grid;
  place-items: center;

  padding: 0;

  border: 0;
  border-radius: 7px;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  font-size: 18px;
}

.icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.brand {
  display: flex;
  align-items: center;

  gap: 7px;
}

.brand-mark {
  width: 22px;
  height: 22px;

  display: grid;
  place-items: center;

  border-radius: 7px;

  background: var(--accent-soft);

  color: var(--accent);

  font-size: 11px;
}

.brand-name {
  font-size: 12px;
  font-weight: 700;
}

.topbar-divider {
  width: 1px;
  height: 20px;

  margin: 0 4px;

  background: var(--border-subtle);
}

.workspace-context {
  min-width: 0;

  display: flex;
  align-items: center;

  gap: 8px;
}

.workspace-context-name {
  max-width: 240px;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 11px;
  font-weight: 650;
}

.workspace-context-session {
  max-width: 180px;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--muted);

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 9px;
}

/* ============================================================
   Status
============================================================ */
.run-state.cancelled {
  color: var(--warning);
  background: var(--warning-soft);
}

.run-state.cancelled .state-dot {
  background: var(--warning);
}

.run-state.error {
  color: var(--danger);
  background: var(--danger-soft);
}

.run-state.error .state-dot {
  background: var(--danger);
}

.run-state {
  display: inline-flex;
  align-items: center;

  gap: 6px;

  padding: 4px 8px;

  border-radius: 999px;

  color: var(--muted-strong);

  background: var(--surface-soft);

  font-size: 10px;
}

.state-dot {
  width: 6px;
  height: 6px;

  border-radius: 50%;

  background: var(--muted);
}

.run-state.working {
  color: var(--accent);
  background: var(--accent-soft);
}

.run-state.working .state-dot {
  background: var(--accent);

  box-shadow: 0 0 0 3px rgba(76, 91, 212, 0.08);
}

.run-state.done {
  color: var(--success);
  background: var(--success-soft);
}

.run-state.done .state-dot {
  background: var(--success);
}

.run-state.draft {
  color: var(--muted-strong);
  background: var(--surface-soft);
}

.text-btn {
  padding: 5px 6px;

  border: 0;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  font-size: 11px;
}

.text-btn:hover {
  color: var(--text);
}

.text-btn.danger:hover {
  color: var(--danger);
}

.text-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

/* ============================================================
   Main
============================================================ */

.main {
  flex: 1 1 auto;

  width: 100%;

  min-width: 0;
  min-height: 0;

  display: grid;

  grid-template-columns:
    var(--sidebar-width, 264px)
    8px
    minmax(0, 1fr);

  overflow: hidden;
}

/* ============================================================
   Sidebar
============================================================ */

.sidebar {
  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;

  border-right: 1px solid var(--border-subtle);

  background: #fafaf8;
}

.sidebar-resize-handle {
  position: relative;
  z-index: 3;
  cursor: col-resize;
  touch-action: none;
}

.sidebar-resize-handle::before {
  position: absolute;
  inset: 0 3px;
  background: transparent;
  content: "";
  transition: background 0.16s ease;
}

.sidebar-resize-handle:hover::before,
body.is-resizing-sidebar .sidebar-resize-handle::before {
  background: var(--accent);
}

.sidebar-resize-grip {
  position: absolute;
  top: 50%;
  left: 2px;
  width: 4px;
  height: 28px;
  border-radius: 99px;
  background: var(--border);
  opacity: 0;
  transform: translateY(-50%);
  transition: opacity 0.16s ease;
}

.sidebar-resize-handle:hover .sidebar-resize-grip,
body.is-resizing-sidebar .sidebar-resize-grip {
  opacity: 1;
}

.sidebar-section {
  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;
}

.conversations-section {
  flex: 1 1 0;
  min-height: 0;
}

.files-section {
  flex: 1 1 0;
  min-height: 0;

  border-top: 1px solid var(--border-subtle);
}

.sidebar-section.collapsed {
  flex: 0 0 auto;
}

.section-header {
  width: 100%;
  height: 40px;

  flex: 0 0 40px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 0 10px 0 14px;

  border: 0;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  text-align: left;
}

.section-header:hover {
  background: var(--surface-hover);
}

.section-title {
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.section-actions {
  display: flex;
  align-items: center;

  gap: 2px;
}

.section-icon-btn {
  width: 24px;
  height: 24px;

  display: grid;
  place-items: center;

  border: 0;
  border-radius: 6px;

  background: transparent;

  color: var(--muted);

  cursor: pointer;

  font-size: 13px;
}

.section-icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.section-chevron {
  color: var(--muted);

  font-size: 17px;

  transform: rotate(0deg);

  transition: transform 0.16s ease;
}

.section-chevron.expanded {
  transform: rotate(90deg);
}

/*
 * section-content 自己不滚。
 * 真正需要滚动的 list/tree 自己负责。
 */
.section-content {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;
}

/* ============================================================
   Conversations
============================================================ */

.new-conversation {
  width: calc(100% - 16px);

  min-height: 32px;

  margin: 0 8px 6px;

  display: flex;
  align-items: center;

  gap: 8px;

  padding: 0 9px;

  border: 0;
  border-radius: 7px;

  background: transparent;

  color: var(--text-soft);

  text-align: left;

  cursor: pointer;

  font-size: 11px;
}

.new-conversation:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.new-conversation-icon {
  width: 19px;
  height: 19px;

  display: grid;
  place-items: center;

  border-radius: 5px;

  background: var(--surface);

  border: 1px solid var(--border);

  color: var(--accent);

  font-size: 13px;
}

.conversation-list {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  padding: 0 8px 10px;

  overflow-x: hidden;
  overflow-y: auto;
}

.conversation-item {
  width: 100%;

  min-height: 42px;

  display: grid;

  grid-template-columns:
    7px
    minmax(0, 1fr);

  gap: 8px;

  align-items: center;

  padding: 7px 8px;

  border: 0;
  border-radius: 7px;

  background: transparent;

  text-align: left;

  color: var(--text-soft);

  cursor: pointer;
}

.conversation-item:hover {
  background: var(--surface-hover);
}

.conversation-item.active {
  background: var(--accent-soft);
  color: var(--text);
}

.conversation-status {
  width: 6px;
  height: 6px;

  border-radius: 50%;

  background: var(--muted);
}

.conversation-status.active {
  background: var(--accent);
}

.conversation-copy {
  min-width: 0;

  display: flex;
  flex-direction: column;

  gap: 3px;
}

.conversation-title {
  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 11px;
  line-height: 1.35;
}

.conversation-meta {
  color: var(--muted);

  font-size: 9px;
}

.sidebar-empty {
  padding: 18px 12px;

  color: var(--muted);

  text-align: center;

  font-size: 10px;
}

/* ============================================================
   Files
============================================================ */

.files-content {
  display: flex;
  flex-direction: column;

  min-width: 0;
  min-height: 0;
}

.workspace-root {
  flex: 0 0 auto;

  display: flex;
  align-items: center;

  gap: 6px;

  padding: 3px 14px 8px;

  color: var(--text-soft);

  font-size: 11px;
}

.workspace-root.empty {
  color: var(--muted);
}

.workspace-root-icon {
  color: var(--muted);
}

.workspace-root-name {
  min-width: 0;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-host {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  position: relative;

  overflow: hidden;
}

.tree-hint {
  display: flex;
  flex-direction: column;

  gap: 4px;

  padding: 26px 20px;

  color: var(--muted);

  text-align: center;

  font-size: 10px;

  line-height: 1.6;
}

.selected-path {
  flex: 0 0 auto;

  display: flex;
  flex-direction: column;

  gap: 4px;

  padding: 10px 14px 12px;

  border-top: 1px solid var(--border-subtle);

  color: var(--muted);

  font-size: 9px;
}

.selected-path code {
  overflow: hidden;

  color: var(--text-soft);

  text-overflow: ellipsis;
  white-space: nowrap;

  font: 10px / 1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* ============================================================
   Main Content / Tab Workspace
============================================================ */

.content-pane {
  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;

  background: var(--page);
}

.content-pane > * {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;
}

/*
 * 旧 chat-pane 不再作为布局容器。
 * 如果 Workspace.vue 已经没有使用它，可以删除。
 */
.chat-pane {
  min-width: 0;
  min-height: 0;

  overflow: hidden;

  background: var(--page);
}

/* ============================================================
   Responsive
============================================================ */

@media (max-width: 900px) {
  .main {
    grid-template-columns:
      248px
      minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .main {
    grid-template-columns:
      0
      0
      minmax(0, 1fr);
  }

  .sidebar {
    display: none;
  }

  .sidebar-resize-handle {
    display: none;
  }

  .workspace-context-session {
    display: none;
  }
}
</style>

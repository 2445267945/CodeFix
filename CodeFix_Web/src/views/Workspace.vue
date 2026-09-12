<template>
  <div class="workspace">
    <!-- =========================================================
         Topbar
    ========================================================== -->
    <WorkspaceTopbar
      :workspace-name="workspaceName"
      :session-id="sessionId"
      :session-short-name="sessionShortName"
      :status-label="statusLabel"
      :status-class="statusClass"
      :can-resume="taskStore.canResume"
      :can-retry="taskStore.canRetry"
      :operating="taskStore.operating"
      @new-conversation="startNewConversation"
      @resume="handleResume"
      @retry="handleRetry"
      @minimize="minimizeWindow"
      @maximize="maximizeWindow"
      @close="closeWindow"
    />

    <!-- =========================================================
         Main Layout

         Sidebar
         Resize
         Content
    ========================================================== -->
    <main
      class="main"
      :class="{
        'with-file-panel': filePanelVisible,
        'sidebar-collapsed': sidebarCollapsed,
      }"
      :style="mainGridStyle"
    >
      <!-- =====================================================
           Sidebar（新对话 + 项目会话）
      ====================================================== -->
      <WorkspaceSidebar
        :collapsed="sidebarCollapsed"
        :workspace-groups="workspaceGroups"
        :current-session-id="currentSessionId"
        :active-workspace-id="filePanelWorkspaceId || workspaceId"
        @new-conversation="startNewConversation"
        @select-session="selectSession"
        @refresh-workspace="refreshWorkspace"
        @open-files="openFilePanel"
        @resize-start="startSidebarResize"
        @collapse-sidebar="toggleSidebar"
      />

      <!-- =====================================================
           Sidebar Resize
      ====================================================== -->
      <div
        class="sidebar-resize-handle"
        role="separator"
        aria-label="调整侧边栏宽度"
        aria-orientation="vertical"
        @pointerdown="startSidebarResize"
      >
        <span class="sidebar-resize-grip"></span>
      </div>

      <!-- =====================================================
           Content
      ====================================================== -->
      <section class="content-pane">
        <WorkspaceTabView
          ref="workspaceTabViewRef"
          :chat="chat"
          :running="taskStore.isRunning"
          :stopping="taskStore.operating"
          :active-run-id="taskStore.runId"
          :workspace-id="workspaceId"
          :workspace-name="workspaceName"
          :workspace-path="workspacePath"
          v-model:message="message"
          v-model:permission-profile="permissionProfile"
          :sending="sending"
          @send="sendMessage"
          @stop="handleCancel"
          @open-file-change="handleFileChangeClick"
          @workspace-path-change="handleWorkspacePathChange"
        />
      </section>

      <!-- =====================================================
           File Panel Resize
      ====================================================== -->
      <div
        v-if="filePanelVisible"
        class="file-panel-resize-handle"
        role="separator"
        aria-label="调整文件面板宽度"
        aria-orientation="vertical"
        @pointerdown="startFilePanelResize"
      >
        <span class="file-panel-resize-grip"></span>
      </div>

      <!-- =====================================================
           File Panel（右侧弹出文件树）
      ====================================================== -->
      <WorkspaceFilePanel
        v-if="filePanelVisible"
        :workspace-name="filePanelWorkspaceName"
        :tree="fileTree"
        :selected-file="selectedFile"
        :loading="fileTreeLoading"
        @close="closeFilePanel"
        @refresh="refreshFilePanelTree"
        @select-file="handleSelectFile"
        @toggle="handleToggleDirectory"
      />
    </main>

    <!-- =========================================================
         侧边栏的收起 / 展开入口内聚在 WorkspaceSidebar 内部，
         收起态由 .main.sidebar-collapsed 控制栅格列宽。
    ========================================================== -->

    <!-- =========================================================
         Diff Overlay

         不参与 Main Grid
    ========================================================== -->
    <DiffViewer
      v-model="diffVisible"
      :diff="currentDiff"
      :loading="diffLoading"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

import { ElMessage, ElMessageBox } from "element-plus";

import { useTaskStore } from "../stores/task";
import { useSessionStore } from "../stores/session";

import WorkspaceTopbar from "../components/WorkspaceTopbar.vue";
import WorkspaceSidebar from "../components/WorkspaceSidebar.vue";
import WorkspaceFilePanel from "../components/WorkspaceFilePanel.vue";

import WorkspaceTabView from "../components/WorkspaceTabView.vue";
import {
  getWorkspaceTree,
  getWorkspaceTreeByPath,
  getWorkspaceChildren,
  getWorkspaceChildrenByPath,
} from "../api/workspace";
import { getFileDiff } from "../api/diff";

import DiffViewer from "../components/DiffViewer.vue";

const taskStore = useTaskStore();

const sessionStore = useSessionStore();

/* ============================================================
   UI State
============================================================ */

const pendingWorkspacePath = ref("");

const workspaceTabViewRef = ref(null);

const message = ref("");

const sending = ref(false);

const selectedFile = ref("");

const fileTree = ref([]);

const fileTreeLoading = ref(false);

/**
 * 右侧文件树面板状态。
 *
 * filePanelVisible：面板是否展开
 * filePanelWorkspaceId：面板手动选中的项目（为空时跟随当前会话）
 */
const filePanelVisible = ref(false);

const filePanelWorkspaceId = ref("");

/**
 * 新建对话草稿状态
 *
 * 不创建：
 * session
 * workspace
 * task
 * run
 */
const isDraftConversation = ref(false);

const permissionProfile = ref("WORKSPACE");

/* ============================================================
   Sidebar Resize
============================================================ */

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

/* ============================================================
   Sidebar Collapse

   收起 ≠ 关闭：
   收起后侧边栏只是从「新建对话 + 项目会话」列表
   退化成一条窄导轨（仅一个「+」入口），
   组件本身依然挂载，列表状态不会丢失。
   ============================================================ */

const SIDEBAR_COLLAPSED_KEY = "codefix.sidebar-collapsed";

const sidebarCollapsed = ref(
  localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "true"
);

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;

  localStorage.setItem(
    SIDEBAR_COLLAPSED_KEY,
    String(sidebarCollapsed.value)
  );
}

/* ============================================================
   File Panel Resize
============================================================ */

const FILE_PANEL_WIDTH_KEY = "codefix.file-panel-width";

const FILE_PANEL_DEFAULT_WIDTH = 280;

const FILE_PANEL_MIN_WIDTH = 200;

const FILE_PANEL_MAX_WIDTH = 640;

const storedFilePanelWidth = Number(
  localStorage.getItem(FILE_PANEL_WIDTH_KEY)
);

const filePanelWidth = ref(
  Number.isFinite(storedFilePanelWidth) &&
    storedFilePanelWidth >= FILE_PANEL_MIN_WIDTH &&
    storedFilePanelWidth <= FILE_PANEL_MAX_WIDTH
    ? storedFilePanelWidth
    : FILE_PANEL_DEFAULT_WIDTH
);

const mainGridStyle = computed(() => ({
  "--sidebar-width": `${sidebarWidth.value}px`,
  "--file-panel-width": `${filePanelWidth.value}px`,
}));

function clampSidebarWidth(width) {
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width));
}

function updateSidebarWidth(event) {
  const main = document.querySelector(".main");

  if (!main) {
    return;
  }

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

  window.addEventListener("pointerup", finishSidebarResize, {
    once: true,
  });
}

/* ============================================================
   File Panel Resize
============================================================ */

function clampFilePanelWidth(width) {
  return Math.min(FILE_PANEL_MAX_WIDTH, Math.max(FILE_PANEL_MIN_WIDTH, width));
}

function updateFilePanelWidth(event) {
  const main = document.querySelector(".main");

  if (!main) {
    return;
  }

  const { right } = main.getBoundingClientRect();

  filePanelWidth.value = clampFilePanelWidth(right - event.clientX);
}

function finishFilePanelResize() {
  document.body.classList.remove("is-resizing-file-panel");

  localStorage.setItem(FILE_PANEL_WIDTH_KEY, String(filePanelWidth.value));

  window.removeEventListener("pointermove", updateFilePanelWidth);

  window.removeEventListener("pointerup", finishFilePanelResize);
}

function startFilePanelResize(event) {
  if (event) {
    event.preventDefault();
  }

  document.body.classList.add("is-resizing-file-panel");

  updateFilePanelWidth(event);

  window.addEventListener("pointermove", updateFilePanelWidth);

  window.addEventListener("pointerup", finishFilePanelResize, {
    once: true,
  });
}

/* ============================================================
   Session
============================================================ */

const sessions = computed(() => sessionStore.sessions);

/**
 * 按项目聚合的会话分组（用于左侧项目列表）。
 */
const workspaceGroups = computed(() => sessionStore.workspaceGroups);

const currentSessionId = computed(() => sessionStore.currentSessionId);

/* ============================================================
   Task / Chat
============================================================ */

const task = computed(() => taskStore.task);

const chat = computed(
  () => taskStore.result?.chat || sessionStore.chat || null
);

const sessionId = computed(
  () =>
    currentSessionId.value || taskStore.sessionId || chat.value?.sessionId || ""
);

const currentTaskId = computed(
  () => taskStore.taskId || chat.value?.taskId || ""
);

const currentRunId = computed(() => taskStore.runId || chat.value?.runId || "");

const workspacePath = computed(() => {
  if (pendingWorkspacePath.value) {
    return pendingWorkspacePath.value;
  }

  return (
    chat.value?.rootPath ||
    chat.value?.workspacePath ||
    chat.value?.workspace?.rootPath ||
    task.value?.rootPath ||
    task.value?.workspacePath ||
    task.value?.workspace?.rootPath ||
    ""
  );
});

const workspaceId = computed(
  () =>
    chat.value?.workspaceId ||
    task.value?.workspaceId ||
    task.value?.workspace?.workspaceId ||
    ""
);

const workspaceName = computed(() => {
  const value =
    chat.value?.workspaceName ||
    task.value?.workspaceName ||
    task.value?.workspace?.name ||
    workspacePath.value ||
    "";

  if (!value) {
    return "";
  }

  return (
    String(value)
      .replace(/[\\/]+$/, "")
      .split(/[\\/]/)
      .filter(Boolean)
      .pop() || ""
  );
});

const sessionShortName = computed(() => {
  if (!sessionId.value) {
    return "新对话";
  }

  if (sessionId.value.length <= 16) {
    return sessionId.value;
  }

  return `${sessionId.value.slice(0, 8)}…` + `${sessionId.value.slice(-4)}`;
});

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

async function selectSession(targetSessionId) {
  if (!targetSessionId) {
    return;
  }

  if (targetSessionId === currentSessionId.value) {
    return;
  }

  taskStore.disconnectSse();

  pendingWorkspacePath.value = "";

  isDraftConversation.value = false;

  selectedFile.value = "";

  fileTree.value = [];

  /**
   * 切换会话时，取消手动指定项目，
   * 让右侧文件树跟随当前会话绑定的 Workspace。
   */
  filePanelWorkspaceId.value = "";

  try {
    const chatView = await sessionStore.openSession(targetSessionId);
    if (!chatView) {
      throw new Error("Session Chat 查询为空");
    }

    taskStore.result = {
      taskId: chatView.taskId || "",

      runId: chatView.runId || "",

      status: "",

      chat: chatView,
    };

    if (!chatView.taskId) {
      taskStore.task = null;

      taskStore.runs = [];

      taskStore.viewedRunId = null;

      taskStore.events = [];

      taskStore.currentRunId = null;

      await loadWorkspaceTree();

      return;
    }

    await taskStore.fetchTask(chatView.taskId);

    const runId = chatView.runId || taskStore.task?.runId || "";

    taskStore.currentRunId = runId || null;

    taskStore.viewedRunId = runId || null;

    if (runId) {
      await taskStore.fetchRuns(chatView.taskId);
    }

    await loadWorkspaceTree();

    taskStore.connectSse(chatView.taskId);
  } catch (error) {
    ElMessage.error(
      sessionStore.error || taskStore.error || error?.message || "打开对话失败"
    );
  }
}

/* ============================================================
   New Conversation
============================================================ */

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

  pendingWorkspacePath.value = "";

  filePanelWorkspaceId.value = "";

  isDraftConversation.value = true;
}

/* ============================================================
   Workspace
============================================================ */

/**
 * 右侧文件树当前展示的项目。
 *
 * 优先使用用户在左侧点击「文件」按钮选中的项目；
 * 没有手动指定时，回退到当前会话绑定的 Workspace。
 */
const activeTreeWorkspaceId = computed(
  () => filePanelWorkspaceId.value || workspaceId.value || ""
);

/**
 * 右侧文件树面板标题展示的项目名称。
 */
const filePanelWorkspaceName = computed(() => {
  const id = activeTreeWorkspaceId.value;

  if (!id) {
    return workspaceName.value;
  }

  const group = sessionStore.workspaceGroups.find(
    (item) => item.workspaceId === id
  );

  return group?.workspaceName || workspaceName.value || "";
});

function handleWorkspacePathChange(path) {
  if (workspaceId.value) {
    ElMessage.warning("当前对话已经绑定项目，无法切换 Workspace");

    return;
  }

  pendingWorkspacePath.value = path || "";

  loadWorkspaceTree();
}

/**
 * 打开右侧文件树面板。
 *
 * 由左侧项目列表中的「文件」按钮触发。
 */
function openFilePanel(targetWorkspaceId) {
  filePanelWorkspaceId.value = targetWorkspaceId || "";

  filePanelVisible.value = true;

  loadWorkspaceTree();
}

function closeFilePanel() {
  filePanelVisible.value = false;

  selectedFile.value = "";
}

function refreshFilePanelTree() {
  loadWorkspaceTree();
}

async function loadWorkspaceTree() {
  const targetWorkspaceId = activeTreeWorkspaceId.value;

  const targetWorkspacePath = workspacePath.value;

  if (!targetWorkspaceId && !targetWorkspacePath) {
    fileTree.value = [];

    return;
  }

  fileTreeLoading.value = true;

  try {
    let response;

    if (targetWorkspaceId) {
      response = await getWorkspaceTree(targetWorkspaceId);
    } else {
      response = await getWorkspaceTreeByPath(targetWorkspacePath);
    }

    const data = response?.data ?? response;

    const tree = Array.isArray(data) ? data : data?.children;

    fileTree.value = Array.isArray(tree) ? tree : [];
  } catch (error) {
    console.error("[Workspace] 文件树读取失败:", error);

    fileTree.value = [];

    ElMessage.error(
      error?.response?.data?.message || error?.message || "读取文件树失败"
    );
  } finally {
    fileTreeLoading.value = false;
  }
}

/**
 * 按路径在文件树中查找节点。
 *
 * 懒加载下节点层级是逐层拼装的，展开时需要根据 path 精确回写 children。
 */
function findTreeNodeByPath(nodes, path) {
  if (!Array.isArray(nodes) || !path) {
    return null;
  }

  for (const node of nodes) {
    if (node.path === path) {
      return node;
    }

    if (Array.isArray(node.children) && node.children.length) {
      const matched = findTreeNodeByPath(node.children, path);

      if (matched) {
        return matched;
      }
    }
  }

  return null;
}

/**
 * 懒加载：展开某个目录时按需请求其直接子节点。
 *
 * 只在首次展开（children 尚未加载）时发请求，之后复用缓存。
 */
async function handleToggleDirectory(path) {
  const targetWorkspaceId = activeTreeWorkspaceId.value;

  const targetWorkspacePath = workspacePath.value;

  if (!targetWorkspaceId && !targetWorkspacePath) {
    return;
  }

  /*
   * 优先在组件传入的节点上回写；若已被压缩/替换，
   * 则按 path 在 fileTree 中重新定位，保证命中真实的响应式节点。
   */
  const node = findTreeNodeByPath(fileTree.value, path);

  if (!node || node.type !== "DIRECTORY") {
    return;
  }

  if (Array.isArray(node.children)) {
    return;
  }

  /*
   * 防止展开动画/压缩链路切换导致的并发重复请求。
   */
  if (node.loading) {
    return;
  }

  node.loading = true;

  try {
    let response;

    if (targetWorkspaceId) {
      response = await getWorkspaceChildren(targetWorkspaceId, path);
    } else {
      response = await getWorkspaceChildrenByPath(targetWorkspacePath, path);
    }

    const data = response?.data ?? response;

    const children = Array.isArray(data) ? data : data?.children;

    node.children = Array.isArray(children) ? children : [];
  } catch (error) {
    console.error("[Workspace] 目录子节点读取失败:", error);

    /*
     * 请求失败时回写空数组，避免节点一直停留在「加载中」状态。
     * 用户可通过面板顶部的「刷新」按钮重新加载整棵文件树后重试。
     */
    node.children = [];

    ElMessage.error(
      error?.response?.data?.message || error?.message || "读取目录失败"
    );
  } finally {
    node.loading = false;
  }
}

/* ============================================================
   Send Message
============================================================ */

async function sendMessage(payload = {}) {
  const content = message.value.trim();

  if (!content || sending.value) {
    return;
  }

  const normalizedWorkspacePath =
    payload?.workspacePath || workspacePath.value || "";

  if (!normalizedWorkspacePath) {
    ElMessage.warning("请先选择一个本地项目目录");

    return;
  }

  sending.value = true;

  try {
    const response = await taskStore.sendMessage(
      content,
      normalizedWorkspacePath,
      permissionProfile.value
    );

    if (response?.sessionId) {
      sessionStore.currentSessionId = response.sessionId;

      isDraftConversation.value = false;

      await sessionStore.fetchSessions();

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

  /**
   * 文件树可能来自当前会话绑定的项目，
   * 也可能来自用户在左侧点击「文件」按钮打开的项目。
   */
  const targetWorkspaceId = activeTreeWorkspaceId.value;
  const targetWorkspacePath = workspacePath.value;

  if (!targetWorkspaceId && !targetWorkspacePath) {
    ElMessage.warning("当前没有可用的 Workspace");

    return;
  }

  selectedFile.value = path;

  workspaceTabViewRef.value?.openFile(path, path.split("/").pop(), {
    workspaceId: targetWorkspaceId || undefined,
    workspacePath: targetWorkspacePath || undefined,
  });
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
     * 页面初始化：
     *
     * 只加载 Session 列表
     *
     * 不创建：
     * Session
     * Workspace
     * Task
     */

    await sessionStore.fetchSessions();
  } catch (error) {
    ElMessage.error(sessionStore.error || "加载历史对话失败");
  }
}

/* ============================================================
   Window Control
============================================================ */

function minimizeWindow() {
  if (!window.electronAPI) {
    console.warn("[Window] Electron API unavailable");
    return;
  }

  window.electronAPI.minimizeWindow();
}

function maximizeWindow() {
  if (!window.electronAPI) {
    console.warn("[Window] Electron API unavailable");
    return;
  }

  window.electronAPI.maximizeWindow();
}

function closeWindow() {
  if (!window.electronAPI) {
    console.warn("[Window] Electron API unavailable");
    return;
  }

  window.electronAPI.closeWindow();
}

onMounted(() => {
  loadWorkspace();
});

onBeforeUnmount(() => {
  taskStore.disconnectSse();

  finishSidebarResize();

  finishFilePanelResize();
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
   Workspace Root
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
   Main Layout
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

/*
  展开右侧文件树面板时，追加「分割条 + 文件面板」两列，
  与左侧 Sidebar 的 [sidebar | handle | content] 结构保持一致。
*/
.main.with-file-panel {
  grid-template-columns:
    var(--sidebar-width, 264px)
    8px
    minmax(0, 1fr)
    8px
    var(--file-panel-width, 280px);
}

/*
  侧边栏收起：

  第一列收缩为窄导轨（仅一个「+」入口），
  第二列（分割条）压成 0 并隐藏，
  其余列保持原有结构，避免栅格子项错位。
*/
@media (min-width: 761px) {
  .main.sidebar-collapsed {
    grid-template-columns:
      var(--sidebar-rail-width, 56px)
      0
      minmax(0, 1fr);
  }

  .main.sidebar-collapsed.with-file-panel {
    grid-template-columns:
      var(--sidebar-rail-width, 56px)
      0
      minmax(0, 1fr)
      8px
      var(--file-panel-width, 280px);
  }

  .main.sidebar-collapsed .sidebar-resize-handle {
    visibility: hidden;
  }
}

/* ============================================================
   File Panel Resize
============================================================ */

.file-panel-resize-handle {
  position: relative;

  z-index: 3;

  cursor: col-resize;

  touch-action: none;
}

.file-panel-resize-handle::before {
  position: absolute;

  inset: 0 3px;

  background: transparent;

  content: "";

  transition: background 0.16s ease;
}

.file-panel-resize-handle:hover::before,
body.is-resizing-file-panel .file-panel-resize-handle::before {
  background: var(--accent);
}

.file-panel-resize-grip {
  position: absolute;

  top: 50%;
  right: 2px;

  width: 4px;
  height: 42px;

  transform: translateY(-50%);

  border-radius: 999px;

  background: var(--border);
}

/* ============================================================
   Content Area
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
  保留兼容。
  后续如果 WorkspaceTabView 完全接管，
  可以删除。
*/

.chat-pane {
  min-width: 0;

  min-height: 0;

  display: flex;

  flex-direction: column;

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
      8px
      minmax(0, 1fr);
  }

  .main.with-file-panel {
    grid-template-columns:
      248px
      8px
      minmax(0, 1fr)
      8px
      var(--file-panel-width, 240px);
  }
}

@media (max-width: 760px) {
  .main {
    grid-template-columns:
      0
      0
      minmax(0, 1fr);
  }

  .main.with-file-panel {
    grid-template-columns:
      0
      0
      minmax(0, 1fr)
      8px
      var(--file-panel-width, 220px);
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
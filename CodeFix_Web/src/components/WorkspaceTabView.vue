<template>
  <section class="workspace-tab-view">
    <!-- =========================================================
         Tab Bar
    ========================================================== -->
    <header class="tab-bar">
      <div class="tab-list">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="tab"
          :class="{ active: tab.id === activeTabId }"
          type="button"
          @click="activateTab(tab.id)"
        >
          <span class="tab-title">
            <span v-if="tab.dirty" class="tab-dirty">●</span>
            {{ tab.title }}
          </span>

          <span
            v-if="tab.closable"
            class="tab-close"
            title="关闭"
            @click.stop="closeTab(tab.id)"
          >
            ×
          </span>
        </button>
      </div>
    </header>

    <!-- =========================================================
         Active Tab
    ========================================================== -->
    <main class="tab-content">
      <!-- =========================
           Chat
      ========================== -->
      <div v-if="activeTab?.type === 'CHAT'" class="chat-view">
        <div class="chat-main">
          <AgentChat
            v-if="activeTab?.type === 'CHAT'"
            :chat="chat"
            :running="running"
            :active-run-id="activeRunId"
            :stopping="stopping"
            v-model:message="message"
            v-model:permission-profile="permissionProfile"
            :sending="sending"
            @send="handleSend"
            @stop="emit('stop')"
            @open-file-change="emit('open-file-change', $event)"
          />
        </div>

        <!-- =====================================================
             Project Toolbar
        ====================================================== -->
        <div class="project-toolbar">
          <!-- 已有关联 / 当前临时选择的本地项目 -->
          <div v-if="currentWorkspaceName" class="project-context">
            <span class="project-icon">📁</span>

            <span class="project-name">
              {{ currentWorkspaceName }}
            </span>

            <!--
              workspaceId 为空：
              说明用户已经选择本地目录，
              但还没有真正发送消息，
              Workspace 尚未持久化。
            -->
          </div>

          <!-- 没有项目 -->
          <button
            v-else
            class="add-project-btn"
            type="button"
            @click="openProjectMenu"
          >
            <span class="add-project-icon">＋</span>
            <span>选择项目</span>
          </button>

          <!-- 项目操作 -->
          <button
            v-if="currentWorkspaceName && !workspaceId"
            class="project-change-btn"
            type="button"
            @click="openProjectMenu"
          >
            更换
          </button>
        </div>

        <!-- =====================================================
             Project Menu
        ====================================================== -->
        <div
          v-if="projectMenuVisible"
          class="project-menu-overlay"
          @click.self="projectMenuVisible = false"
        >
          <div class="project-menu">
            <div class="project-menu-title">选择项目</div>

            <button
              class="project-menu-item"
              type="button"
              @click="handleSelectLocalProject"
            >
              <span class="project-menu-icon">📁</span>

              <span class="project-menu-copy">
                <span class="project-menu-item-title"> 本地项目 </span>

                <span class="project-menu-item-description">
                  选择一个本地目录作为 Workspace
                </span>
              </span>
            </button>

            <button class="project-menu-item disabled" type="button" disabled>
              <span class="project-menu-icon">⇧</span>

              <span class="project-menu-copy">
                <span class="project-menu-item-title"> 本地上传 </span>

                <span class="project-menu-item-description"> 即将支持 </span>
              </span>
            </button>

            <button
              class="project-menu-cancel"
              type="button"
              @click="projectMenuVisible = false"
            >
              取消
            </button>
          </div>
        </div>
      </div>

      <!-- =========================
           File Editor
      ========================== -->
      <Editor
        v-else-if="activeTab?.type === 'FILE'"
        :file-path="activeTab.path"
        :content="activeTab.content"
        :loading="activeTab.loading"
        @update:content="handleEditorContentChange"
        @save="saveActiveFile"
      />

      <!-- =========================
           Empty
      ========================== -->
      <div v-else class="empty-view">请选择一个页面</div>
    </main>
  </section>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";

import AgentChat from "./AgentChat.vue";
import Editor from "./Editor.vue";

import {
  getWorkspaceFile,
  getWorkspaceFileByPath,
  updateWorkspaceFile,
} from "../api/workspace";

const props = defineProps({
  chat: {
    type: [Object, null],
    default: null,
  },

  running: {
    type: Boolean,
    default: false,
  },

  stopping: {
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

  /**
   * 已经正式绑定到 Session 的 Workspace ID。
   */
  workspaceId: {
    type: String,
    default: "",
  },

  /**
   * Workspace 名称。
   *
   * 正式 Workspace：
   *   来自后端。
   *
   * 临时选择：
   *   可以由父组件根据 workspacePath 提供。
   */
  workspaceName: {
    type: String,
    default: "",
  },

  /**
   * 当前 Workspace 实际路径。
   *
   * 用户选择目录后，即使 Workspace 尚未落库，
   * 这里也可以暂时保存路径。
   */
  workspacePath: {
    type: String,
    default: "",
  },

  permissionProfile: {
    type: String,
    default: "WORKSPACE",
  },
});

const emit = defineEmits([
  "update:message",
  "update:permission-profile",
  "send",
  "stop",
  "open-file-change",

  /**
   * 用户选择 / 更换本地项目后，
   * 将目录路径交给父组件保存。
   */
  "workspace-path-change",
]);

/* ============================================================
   Chat Message
============================================================ */

const message = computed({
  get() {
    return props.message;
  },

  set(value) {
    emit("update:message", value);
  },
});

const permissionProfile = computed({
  get() {
    return props.permissionProfile;
  },

  set(value) {
    emit("update:permission-profile", value);
  },
});

/* ============================================================
   Tabs
============================================================ */

const tabs = ref([
  {
    id: "chat",
    type: "CHAT",
    title: "Chat",
    closable: false,
  },
]);

const activeTabId = ref("chat");

const activeTab = computed(() => {
  return tabs.value.find((tab) => tab.id === activeTabId.value) || null;
});

/* ============================================================
   Project
============================================================ */

/**
 * 用户已经选择，但尚未真正发起对话的本地项目路径。
 *
 * 注意：
 * 这里只是前端状态，不代表数据库已经创建 Workspace。
 */
const pendingWorkspacePath = ref("");

const projectMenuVisible = ref(false);

/**
 * 当前真正用于发送消息的 Workspace Path。
 *
 * 优先使用临时选择，
 * 没有临时选择时使用父组件已有值。
 */
const currentWorkspacePath = computed(() => {
  if (props.workspaceId) {
    return props.workspacePath || "";
  }

  return pendingWorkspacePath.value || props.workspacePath || "";
});

/**
 * 当前显示的 Workspace 名称。
 *
 * 正式 Workspace：
 *   优先使用后端返回的 workspaceName。
 *
 * 临时 Workspace：
 *   根据路径最后一段自动解析。
 */
const currentWorkspaceName = computed(() => {
  if (props.workspaceName) {
    return props.workspaceName
      .replace(/[\\/]+$/, "")
      .split(/[\\/]/)
      .pop();
  }

  const path = currentWorkspacePath.value;

  if (!path) {
    return "";
  }

  const normalizedPath = path.replace(/[\\/]+$/, "");

  return normalizedPath.split(/[\\/]/).filter(Boolean).pop() || normalizedPath;
});

/**
 * 已经正式存在 Workspace 时，
 * 清除临时路径状态。
 */
watch(
  () => props.workspaceId,
  (value) => {
    if (value) {
      pendingWorkspacePath.value = "";
    }
  }
);

/**
 * 父组件传入新的 Workspace Path 时，
 * 如果当前还没有正式 Workspace，
 * 则同步到临时状态。
 */
watch(
  () => props.workspacePath,
  (value) => {
    if (props.workspaceId) {
      pendingWorkspacePath.value = "";
      return;
    }

    if (value) {
      pendingWorkspacePath.value = value;
    }
  },
  {
    immediate: true,
  }
);

/* ============================================================
   Project Operations
============================================================ */

function openProjectMenu() {
  projectMenuVisible.value = true;
}

/**
 * 选择本地项目。
 *
 * 这里不直接调用后端。
 * 也不创建 Workspace。
 *
 * 仅通知父组件：
 * “请打开本地目录选择能力”。
 */
async function handleSelectLocalProject() {
  projectMenuVisible.value = false;

  try {
    const path = await window.electronAPI.selectDirectory();

    if (!path) {
      return;
    }

    handleLocalProjectSelected(path);
  } catch (error) {
    console.error("[WorkspaceTabView] 本地项目选择失败:", error);

    ElMessage.error("选择本地项目失败");
  }
}

/**
 * 父组件完成目录选择后，可以直接使用此方法
 * 更新当前临时 Workspace Path。
 */
function handleLocalProjectSelected(path) {
  if (props.workspaceId) {
    ElMessage.warning("当前对话已经绑定 Workspace，无法更换项目");
    return;
  }

  if (!path) {
    return;
  }

  const normalizedPath = String(path).trim();

  if (!normalizedPath) {
    return;
  }

  pendingWorkspacePath.value = normalizedPath;

  emit("workspace-path-change", normalizedPath);

  ElMessage.success(`已选择项目：${getWorkspaceName(normalizedPath)}`);
}

function getWorkspaceName(path) {
  if (!path) {
    return "";
  }

  return path.split(/[\\/]/).filter(Boolean).pop() || path;
}

/**
 * 发送消息前必须有本地 Workspace。
 */
function handleSend() {
  const workspacePath = currentWorkspacePath.value;

  if (!workspacePath) {
    ElMessage.warning("请先选择一个本地项目目录");

    openProjectMenu();

    return;
  }

  emit("send", {
    workspacePath,
  });
}

/* ============================================================
   Tab Operations
============================================================ */

function activateTab(tabId) {
  const tab = tabs.value.find((item) => item.id === tabId);

  if (!tab) {
    return;
  }

  activeTabId.value = tabId;
}

async function openFile(path, name) {
  if (!path) {
    return;
  }

  if (!props.workspaceId && !props.workspacePath) {
    ElMessage.warning("当前没有可用的 Workspace");
    return;
  }

  const existingTab = tabs.value.find(
    (tab) => tab.type === "FILE" && tab.path === path
  );

  if (existingTab) {
    activeTabId.value = existingTab.id;
    return;
  }

  const fileName = name || path.split("/").pop() || path;

  const tab = {
    id: `file:${path}`,
    type: "FILE",
    title: fileName,
    path,
    content: "",
    loading: true,
    dirty: false,
    closable: true,
    saving: false,
  };

  tabs.value.push(tab);
  activeTabId.value = tab.id;

  try {
    let response;

    if (props.workspaceId) {
      response = await getWorkspaceFile(props.workspaceId, path);
    } else {
      response = await getWorkspaceFileByPath(props.workspacePath, path);
    }

    const data = response?.data ?? response;

    const currentTab = tabs.value.find((item) => item.id === tab.id);

    if (!currentTab) {
      return;
    }

    currentTab.content = data?.content || "";
  } catch (error) {
    console.error("[WorkspaceTabView] 文件读取失败:", error);

    const index = tabs.value.findIndex((item) => item.id === tab.id);

    if (index !== -1) {
      tabs.value.splice(index, 1);
    }

    if (activeTabId.value === tab.id) {
      activeTabId.value = "chat";
    }

    ElMessage.error(
      error?.response?.data?.message || error?.message || "读取文件失败"
    );

    return;
  } finally {
    const currentTab = tabs.value.find((item) => item.id === tab.id);

    if (currentTab) {
      currentTab.loading = false;
    }
  }
}

async function saveActiveFile() {
  const tab = activeTab.value;

  if (!tab || tab.type !== "FILE") {
    return;
  }

  if (!props.workspaceId) {
    ElMessage.warning("当前没有可用的 Workspace");

    return;
  }

  if (!tab.path) {
    return;
  }

  if (tab.saving) {
    return;
  }

  if (!tab.dirty) {
    return;
  }

  tab.saving = true;

  try {
    await updateWorkspaceFile(props.workspaceId, tab.path, tab.content);

    tab.dirty = false;

    ElMessage.success(`${tab.title} 保存成功`);
  } catch (error) {
    console.error("[WorkspaceTabView] 文件保存失败:", error);

    ElMessage.error(
      error?.response?.data?.message || error?.message || "文件保存失败"
    );
  } finally {
    tab.saving = false;
  }
}

function handleEditorContentChange(content) {
  const tab = activeTab.value;

  if (!tab || tab.type !== "FILE") {
    return;
  }

  tab.content = content;
  tab.dirty = true;
}

function closeTab(tabId) {
  const index = tabs.value.findIndex((tab) => tab.id === tabId);

  if (index === -1) {
    return;
  }

  const tab = tabs.value[index];

  if (!tab.closable) {
    return;
  }

  const wasActive = activeTabId.value === tabId;

  tabs.value.splice(index, 1);

  if (!wasActive) {
    return;
  }

  const previousTab = tabs.value[index - 1];

  const nextTab = tabs.value[index];

  if (previousTab) {
    activeTabId.value = previousTab.id;

    return;
  }

  if (nextTab) {
    activeTabId.value = nextTab.id;

    return;
  }

  activeTabId.value = "chat";
}

function closeAllFiles() {
  tabs.value = tabs.value.filter((tab) => !tab.closable);

  activeTabId.value = "chat";
}

function openChat() {
  activeTabId.value = "chat";
}

/* ============================================================
   Expose
============================================================ */

defineExpose({
  openFile,
  activateTab,
  closeTab,
  closeAllFiles,
  openChat,
  saveActiveFile,

  /**
   * 父组件拿到系统目录选择结果后，
   * 可以调用：
   *
   * workspaceTabViewRef.handleLocalProjectSelected(path)
   */
  handleLocalProjectSelected,
});
</script>

<style scoped>
.workspace-tab-view {
  display: flex;
  flex-direction: column;

  width: 100%;
  height: 100%;

  min-width: 0;
  min-height: 0;

  background: var(--surface, #ffffff);
}

.tab-bar {
  display: flex;
  align-items: stretch;

  min-height: 38px;

  border-bottom: 1px solid var(--border, #e5e7eb);

  background: var(--surface-soft, #f8fafc);

  overflow-x: auto;
  overflow-y: hidden;
}

.tab-list {
  display: flex;
  align-items: stretch;
  min-width: 0;
}

.tab {
  display: inline-flex;
  align-items: center;

  gap: 7px;

  min-width: 86px;
  max-width: 220px;
  height: 38px;

  padding: 0 11px;

  border: 0;
  border-right: 1px solid var(--border, #e5e7eb);

  background: transparent;

  color: var(--text-soft, #6b7280);

  cursor: pointer;
  white-space: nowrap;
}

.tab:hover {
  background: var(--surface-hover, #f3f4f6);
}

.tab.active {
  background: var(--surface, #ffffff);

  color: var(--text, #111827);
}

.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 12px;
}

.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  width: 18px;
  height: 18px;

  border-radius: 4px;

  color: var(--muted, #9ca3af);

  font-size: 15px;
  line-height: 1;
}

.tab-close:hover {
  background: var(--surface-hover, #e5e7eb);

  color: var(--text, #111827);
}

/* =========================================================
   Content
========================================================= */

.tab-content {
  flex: 1;

  min-width: 0;
  min-height: 0;

  overflow: hidden;
}

.chat-view {
  display: flex;
  flex-direction: column;

  width: 100%;
  height: 100%;

  min-width: 0;
  min-height: 0;

  overflow: hidden;
}

.chat-main {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  overflow: hidden;
}

/* =========================================================
   Project Toolbar
========================================================= */

.project-toolbar {
  display: flex;
  align-items: center;

  gap: 8px;

  min-height: 42px;

  padding: 0 14px;

  border-top: 1px solid var(--border, #e5e7eb);

  background: var(--surface-soft, #fafafa);
}

.add-project-btn,
.project-change-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  gap: 6px;

  border: 0;

  background: transparent;

  color: var(--text-soft, #6b7280);

  font-size: 12px;

  cursor: pointer;
}

.add-project-btn:hover,
.project-change-btn:hover {
  color: var(--text, #111827);
}

.add-project-icon {
  font-size: 16px;
}

.project-context {
  display: flex;
  align-items: center;

  min-width: 0;

  gap: 6px;
}

.project-icon {
  font-size: 13px;
}

.project-name {
  max-width: 220px;

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--text, #111827);

  font-size: 12px;
  font-weight: 600;
}

.project-pending {
  padding: 2px 6px;

  border-radius: 4px;

  background: #fff7ed;

  color: #c2410c;

  font-size: 10px;
}

/* =========================================================
   Project Menu
========================================================= */

.project-menu-overlay {
  position: fixed;

  inset: 0;

  z-index: 1000;

  display: flex;
  align-items: center;
  justify-content: center;

  background: rgba(0, 0, 0, 0.18);
}

.project-menu {
  width: 340px;

  padding: 8px;

  border: 1px solid var(--border, #e5e7eb);

  border-radius: 10px;

  background: var(--surface, #ffffff);

  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
}

.project-menu-title {
  padding: 10px 12px 8px;

  color: var(--text, #111827);

  font-size: 13px;
  font-weight: 600;
}

.project-menu-item {
  display: flex;
  align-items: center;

  width: 100%;

  gap: 12px;

  padding: 11px 12px;

  border: 0;
  border-radius: 7px;

  background: transparent;

  text-align: left;

  cursor: pointer;
}

.project-menu-item:hover:not(.disabled) {
  background: var(--surface-hover, #f3f4f6);
}

.project-menu-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.project-menu-icon {
  width: 24px;

  text-align: center;

  font-size: 16px;
}

.project-menu-copy {
  display: flex;
  flex-direction: column;

  gap: 2px;

  min-width: 0;
}

.project-menu-item-title {
  color: var(--text, #111827);

  font-size: 12px;
}

.project-menu-item-description {
  color: var(--muted, #9ca3af);

  font-size: 11px;
}

.project-menu-cancel {
  width: 100%;

  margin-top: 4px;

  padding: 8px;

  border: 0;

  border-top: 1px solid var(--border, #e5e7eb);

  background: transparent;

  color: var(--muted, #9ca3af);

  cursor: pointer;
}

/* =========================================================
   Empty
========================================================= */

.empty-view {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 100%;
  height: 100%;

  color: var(--muted, #9ca3af);

  font-size: 13px;
}

.tab-dirty {
  margin-right: 4px;

  color: #c2410c;

  font-size: 8px;
}
</style>

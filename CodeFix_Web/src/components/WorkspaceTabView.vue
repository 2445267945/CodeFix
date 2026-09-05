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
            @send="emit('send')"
            @stop="emit('stop')"
            @open-file-change="emit('open-file-change', $event)"
          />
        </div>

        <!-- =====================================================
             Project Toolbar
        ====================================================== -->
        <div class="project-toolbar">
          <!-- 已有关联 / 当前临时项目 -->
          <div v-if="currentWorkspaceName" class="project-context">
            <span class="project-icon">📁</span>

            <span class="project-name">
              {{ currentWorkspaceName }}
            </span>

            <span v-if="!workspaceId" class="project-pending"> 待提交 </span>
          </div>

          <!-- 没有项目 -->
          <button
            v-else
            class="add-project-btn"
            type="button"
            @click="openProjectMenu"
          >
            <span class="add-project-icon">＋</span>
            <span>添加项目</span>
          </button>

          <!-- 项目操作 -->
          <button
            v-if="currentWorkspaceName"
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
            <div class="project-menu-title">添加项目</div>

            <button
              class="project-menu-item"
              type="button"
              @click="handleCreateProject"
            >
              <span class="project-menu-icon"> ＋ </span>

              <span class="project-menu-copy">
                <span class="project-menu-item-title"> 新建项目 </span>

                <span class="project-menu-item-description">
                  创建一个新的项目名称
                </span>
              </span>
            </button>

            <button class="project-menu-item disabled" type="button" disabled>
              <span class="project-menu-icon"> ⇧ </span>

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

        <!-- =====================================================
             Create Project Dialog
        ====================================================== -->
        <div
          v-if="createProjectVisible"
          class="project-dialog-overlay"
          @click.self="closeCreateProject"
        >
          <div class="project-dialog">
            <div class="project-dialog-header">
              <div class="project-dialog-title">新建项目</div>

              <button
                class="project-dialog-close"
                type="button"
                @click="closeCreateProject"
              >
                ×
              </button>
            </div>

            <div class="project-dialog-body">
              <label class="project-dialog-label"> 项目名称 </label>

              <input
                v-model.trim="projectNameInput"
                class="project-dialog-input"
                type="text"
                maxlength="100"
                placeholder="例如：电商后台"
                @keyup.enter="confirmCreateProject"
              />

              <div class="project-dialog-hint">
                项目会先绑定到当前对话，真正发送消息时才创建 Workspace。
              </div>
            </div>

            <div class="project-dialog-footer">
              <button
                class="dialog-btn secondary"
                type="button"
                @click="closeCreateProject"
              >
                取消
              </button>

              <button
                class="dialog-btn primary"
                type="button"
                :disabled="!projectNameInput"
                @click="confirmCreateProject"
              >
                确定
              </button>
            </div>
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

import { getWorkspaceFile, updateWorkspaceFile } from "../api/workspace";

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
   * 已经正式存在的 Workspace ID
   */
  workspaceId: {
    type: String,
    default: "",
  },

  /**
   * 当前 Workspace 名称。
   *
   * 可以来自：
   * 1. 已经存在的 Workspace
   * 2. 当前对话临时选择的新项目
   */
  workspaceName: {
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
  "workspace-name-change",
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
 * 当前对话临时选择的项目名称。
 *
 * 注意：
 * workspaceId 为空时，
 * 这个名称只是前端状态，
 * 不代表后端已经创建 Workspace。
 */
const pendingWorkspaceName = ref("");

const projectMenuVisible = ref(false);

const createProjectVisible = ref(false);

const projectNameInput = ref("");

/**
 * 当前页面显示的项目名称。
 *
 * 已经存在 Workspace：
 *   props.workspaceName
 *
 * 新建但尚未发送：
 *   pendingWorkspaceName
 */
const currentWorkspaceName = computed(() => {
  return pendingWorkspaceName.value || props.workspaceName || "";
});

/**
 * 已经有正式 Workspace 时，
 * 从后端返回的数据同步项目名称。
 */
watch(
  () => props.workspaceName,
  (value) => {
    if (props.workspaceId) {
      pendingWorkspaceName.value = "";
      return;
    }

    /*
     * 没有 Workspace 时，
     * 保留前端当前临时选择。
     */
    if (!pendingWorkspaceName.value && value) {
      pendingWorkspaceName.value = value;
    }
  },
  {
    immediate: true,
  }
);

/**
 * Workspace 已正式创建后，
 * 清除临时项目状态。
 */
watch(
  () => props.workspaceId,
  (value) => {
    if (value) {
      pendingWorkspaceName.value = "";
    }
  }
);

/* ============================================================
   Project Operations
============================================================ */

function openProjectMenu() {
  projectMenuVisible.value = true;
}

function handleCreateProject() {
  projectMenuVisible.value = false;

  projectNameInput.value =
    pendingWorkspaceName.value || props.workspaceName || "";

  createProjectVisible.value = true;
}

function closeCreateProject() {
  createProjectVisible.value = false;
  projectNameInput.value = "";
}

function confirmCreateProject() {
  const name = projectNameInput.value?.trim();

  if (!name) {
    ElMessage.warning("请输入项目名称");

    return;
  }

  /*
   * 这里只修改前端状态。
   *
   * 不调用后端。
   * 不创建 Workspace。
   */
  pendingWorkspaceName.value = name;

  emit("workspace-name-change", name);

  createProjectVisible.value = false;
  projectNameInput.value = "";

  ElMessage.success(`已选择项目：${name}`);
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

  if (!props.workspaceId) {
    ElMessage.warning("当前项目尚未建立 Workspace，暂时无法打开文件");

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
    const response = await getWorkspaceFile(props.workspaceId, path);

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

.project-menu-overlay,
.project-dialog-overlay {
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
   Create Dialog
========================================================= */

.project-dialog {
  width: 420px;

  border-radius: 12px;

  background: var(--surface, #ffffff);

  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
}

.project-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 16px 18px;

  border-bottom: 1px solid var(--border, #e5e7eb);
}

.project-dialog-title {
  color: var(--text, #111827);

  font-size: 14px;
  font-weight: 600;
}

.project-dialog-close {
  width: 28px;
  height: 28px;

  border: 0;
  border-radius: 5px;

  background: transparent;

  color: var(--muted, #9ca3af);

  font-size: 18px;

  cursor: pointer;
}

.project-dialog-close:hover {
  background: var(--surface-hover, #f3f4f6);
}

.project-dialog-body {
  padding: 18px;
}

.project-dialog-label {
  display: block;

  margin-bottom: 8px;

  color: var(--text, #111827);

  font-size: 12px;
  font-weight: 600;
}

.project-dialog-input {
  width: 100%;

  box-sizing: border-box;

  padding: 9px 10px;

  border: 1px solid var(--border, #d1d5db);

  border-radius: 7px;

  outline: none;

  background: var(--surface, #ffffff);

  color: var(--text, #111827);

  font-size: 13px;
}

.project-dialog-input:focus {
  border-color: var(--accent, #7c3aed);

  box-shadow: 0 0 0 2px var(--accent-soft, #ede9fe);
}

.project-dialog-hint {
  margin-top: 8px;

  color: var(--muted, #9ca3af);

  font-size: 11px;
  line-height: 1.5;
}

.project-dialog-footer {
  display: flex;
  justify-content: flex-end;

  gap: 8px;

  padding: 12px 18px 16px;

  border-top: 1px solid var(--border, #e5e7eb);
}

.dialog-btn {
  padding: 7px 14px;

  border-radius: 6px;

  font-size: 12px;

  cursor: pointer;
}

.dialog-btn.secondary {
  border: 1px solid var(--border, #d1d5db);

  background: var(--surface, #ffffff);

  color: var(--text-soft, #6b7280);
}

.dialog-btn.primary {
  border: 0;

  background: var(--text, #111827);

  color: #ffffff;
}

.dialog-btn.primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
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
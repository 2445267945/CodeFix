<template>
  <aside class="sidebar">
    <!-- =====================================================
         Conversations
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
        @click="emit('toggle-conversations')"
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
          @click="emit('new-conversation')"
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
            @click="emit('select-session', session.sessionId)"
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
                {{ session.updatedAtLabel }}
              </span>
            </span>
          </button>
        </div>

        <div v-else class="sidebar-empty">暂无历史对话</div>
      </div>
    </section>

    <!-- =====================================================
         Files
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
        @click="emit('toggle-files')"
      >
        <span class="section-title"> FILES </span>

        <div class="section-actions">
          <button
            class="section-icon-btn"
            type="button"
            title="刷新文件树"
            @click.stop="emit('refresh-workspace')"
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
          <span class="workspace-root-icon"> ＋ </span>

          <span class="workspace-root-name"> 未关联项目 </span>
        </div>

        <div class="tree-host">
          <WorkspaceTree
            :tree="fileTree"
            :selected-file="selectedFile"
            @select="emit('select-file', $event)"
          />

          <div v-if="fileTreeLoading" class="tree-hint">正在读取文件树…</div>

          <div v-else-if="!fileTree.length" class="tree-hint">
            <span>
              {{
                workspaceId || workspacePath
                  ? "当前 Workspace 暂无文件"
                  : "请先选择一个本地项目"
              }}
            </span>

            <span v-if="!workspaceId && !workspacePath">
              请先选择一个本地项目目录。
            </span>
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

    <!-- =====================================================
         Resize Handle
    ====================================================== -->
    <div
      class="sidebar-resize-handle"
      role="separator"
      aria-label="调整侧边栏宽度"
      aria-orientation="vertical"
      @pointerdown="emit('resize-start', $event)"
    >
      <span class="sidebar-resize-grip"></span>
    </div>
  </aside>
</template>

<script setup>
import WorkspaceTree from "./WorkspaceTree.vue";

defineProps({
  sessions: {
    type: Array,
    default: () => [],
  },

  currentSessionId: {
    type: String,
    default: "",
  },

  conversationsCollapsed: {
    type: Boolean,
    default: false,
  },

  filesCollapsed: {
    type: Boolean,
    default: false,
  },

  workspaceId: {
    type: String,
    default: "",
  },

  workspaceName: {
    type: String,
    default: "",
  },

  workspacePath: {
    type: String,
    default: "",
  },

  fileTree: {
    type: Array,
    default: () => [],
  },

  selectedFile: {
    type: String,
    default: "",
  },

  fileTreeLoading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits([
  "toggle-conversations",
  "toggle-files",
  "new-conversation",
  "select-session",
  "refresh-workspace",
  "select-file",
  "resize-start",
]);
</script>

<style>
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

/* ============================================================
   Resize
============================================================ */

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

/* ============================================================
   Section
============================================================ */

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

/* ============================================================
   Section Content
============================================================ */

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

  font: 10px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
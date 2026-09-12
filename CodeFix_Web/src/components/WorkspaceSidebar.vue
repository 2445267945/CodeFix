<template>
  <aside class="sidebar" :class="{ collapsed }">
    <!-- =====================================================
         Collapsed Rail

         收起后：不再展示「新建对话 + 项目会话」列表，
         仅在窄导轨上保留一个「+」入口（新建对话），
         以及一个展开按钮。
    ====================================================== -->
    <div v-if="collapsed" class="sidebar-rail">
      <button
        class="rail-btn rail-new-btn"
        type="button"
        title="新建对话"
        aria-label="新建对话"
        @click="emit('new-conversation')"
      >
        +
      </button>

      <button
        class="rail-btn rail-toggle-btn"
        type="button"
        title="展开侧边栏"
        aria-label="展开侧边栏"
        @click="emit('collapse-sidebar')"
      >
        »
      </button>
    </div>

    <div v-else class="sidebar-body">
      <!-- =====================================================
           New Conversation
      ====================================================== -->
      <div class="new-conversation-row">
        <button
          class="new-conversation"
          type="button"
          @click="emit('new-conversation')"
        >
          <span class="new-conversation-icon"> + </span>

          <span>新建对话</span>
        </button>

        <button
          class="sidebar-collapse-btn"
          type="button"
          title="收起侧边栏"
          aria-label="收起侧边栏"
          @click="emit('collapse-sidebar')"
        >
          «
        </button>
      </div>

      <!-- =====================================================
           Projects
      ====================================================== -->
      <div class="projects-header">
        <span class="section-title"> 项目 </span>

        <button
          class="section-icon-btn"
          type="button"
          title="刷新项目会话"
          @click="emit('refresh-workspace')"
        >
          ↻
        </button>
      </div>

      <div v-if="workspaceGroups.length" class="project-list">
        <div
          v-for="group in workspaceGroups"
          :key="group.workspaceId"
          class="project-group"
        >
          <!-- Project Header -->
          <div
            class="project-header"
            :class="{
              active: group.workspaceId === activeWorkspaceId,
            }"
          >
            <button
              class="project-toggle"
              type="button"
              @click="toggleProject(group.workspaceId)"
            >
              <span
                class="project-chevron"
                :class="{
                  expanded: isExpanded(group.workspaceId),
                }"
              >
                ›
              </span>

              <span class="project-name" :title="group.workspaceName">
                {{ group.workspaceName || "未命名项目" }}
              </span>

              <span class="project-count">
                {{ group.sessions.length }}
              </span>
            </button>

            <button
              class="project-file-btn"
              type="button"
              title="查看项目文件"
              @click.stop="emit('open-files', group.workspaceId)"
            >
              文件
            </button>
          </div>

          <!-- Project Sessions -->
          <div
            v-if="isExpanded(group.workspaceId)"
            class="project-sessions"
          >
            <button
              v-for="session in group.sessions"
              :key="session.sessionId"
              class="session-item"
              :class="{
                active: session.sessionId === currentSessionId,
              }"
              type="button"
              @click="emit('select-session', session.sessionId)"
            >
              <span
                class="session-status"
                :class="{
                  active: session.sessionId === currentSessionId,
                }"
              ></span>

              <span class="session-copy">
                <span class="session-title">
                  {{ session.title || "新对话" }}
                </span>

                <span class="session-meta">
                  {{ formatSessionTime(session.updatedAt) }}
                </span>
              </span>
            </button>

            <div v-if="!group.sessions.length" class="project-empty">
              暂无对话
            </div>
          </div>
        </div>
      </div>

      <div v-else class="sidebar-empty">暂无历史项目</div>
    </div>

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
import { ref } from "vue";

defineProps({
  /**
   * 按项目聚合的会话分组：
   * [{ workspaceId, workspaceName, sessions: [...] }]
   */
  workspaceGroups: {
    type: Array,
    default: () => [],
  },

  currentSessionId: {
    type: String,
    default: "",
  },

  /**
   * 当前正在查看文件树的项目。
   */
  activeWorkspaceId: {
    type: String,
    default: "",
  },

  /**
   * 侧边栏是否收起。
   *
   * 收起 ≠ 关闭：
   * 列表只是从视图中隐藏，
   * 组件状态（折叠的项目、滚动位置）依然保留。
   */
  collapsed: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits([
  "new-conversation",
  "select-session",
  "open-files",
  "refresh-workspace",
  "resize-start",
  "collapse-sidebar",
]);

/**
 * 记录“被折叠”的项目。
 *
 * 默认全部展开（与设计稿一致），
 * 用户点击项目头部时切换折叠状态。
 */
const collapsedProjects = ref(new Set());

function isExpanded(workspaceId) {
  return !collapsedProjects.value.has(workspaceId);
}

function toggleProject(workspaceId) {
  const next = new Set(collapsedProjects.value);

  if (next.has(workspaceId)) {
    next.delete(workspaceId);
  } else {
    next.add(workspaceId);
  }

  collapsedProjects.value = next;
}

/* ============================================================
   Time
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

.sidebar-body {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;
}

/* ============================================================
   New Conversation
============================================================ */

.new-conversation-row {
  flex: 0 0 auto;

  display: flex;
  align-items: center;
  gap: 6px;

  margin: 10px 8px 6px;
}

.new-conversation {
  flex: 1 1 auto;

  min-width: 0;

  height: 36px;

  display: flex;
  align-items: center;
  gap: 8px;

  padding: 0 10px;

  border: 1px solid var(--border);
  border-radius: 8px;

  background: var(--surface);

  color: var(--text);

  cursor: pointer;

  font-size: 12px;
  font-weight: 600;

  text-align: left;
}

.new-conversation:hover {
  background: var(--surface-hover);
}

.new-conversation-icon {
  width: 18px;
  height: 18px;

  display: grid;
  place-items: center;

  border-radius: 5px;

  background: var(--accent-soft);

  color: var(--accent);

  font-size: 14px;
  line-height: 1;
}

/* ============================================================
   Collapse Toggle
============================================================ */

/* ============================================================
   Toggle Button
   ============================================================ */

.sidebar-collapse-btn {
  flex: 0 0 auto;

  width: 26px;
  height: 26px;

  display: grid;
  place-items: center;

  border: 0;
  border-radius: 6px;

  background: transparent;

  color: var(--muted);

  cursor: pointer;

  font-size: 12px;
  line-height: 1;
}

.sidebar-collapse-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

/* ============================================================
   Projects Header
   ============================================================ */

.projects-header {
  flex: 0 0 auto;

  height: 32px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 0 10px 0 14px;
}

.section-title {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;

  color: var(--muted-strong);
}

.section-icon-btn {
  width: 22px;
  height: 22px;

  display: grid;
  place-items: center;

  border: 0;
  border-radius: 5px;

  background: transparent;

  color: var(--muted);

  cursor: pointer;

  font-size: 13px;
  line-height: 1;
}

.section-icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

/* ============================================================
   Project List
============================================================ */

.project-list {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  padding: 2px 8px 12px;

  overflow-x: hidden;
  overflow-y: auto;
}

.project-group {
  margin-bottom: 2px;
}

.project-header {
  display: flex;
  align-items: center;

  border-radius: 7px;
}

.project-header:hover {
  background: var(--surface-hover);
}

.project-header.active {
  background: var(--accent-soft);
}

.project-toggle {
  flex: 1 1 auto;

  min-width: 0;

  display: flex;
  align-items: center;
  gap: 6px;

  padding: 7px 6px;

  border: 0;
  background: transparent;

  color: var(--text);

  cursor: pointer;

  text-align: left;
}

.project-chevron {
  flex: 0 0 auto;

  display: inline-block;

  color: var(--muted);

  font-size: 12px;
  line-height: 1;

  transition: transform 0.15s ease;
}

.project-chevron.expanded {
  transform: rotate(90deg);
}

.project-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 12px;
  font-weight: 600;
}

.project-count {
  flex: 0 0 auto;

  margin-left: 2px;

  color: var(--muted);

  font-size: 10px;
}

.project-file-btn {
  flex: 0 0 auto;

  margin-right: 4px;

  padding: 3px 8px;

  border: 1px solid var(--border);
  border-radius: 6px;

  background: var(--surface);

  color: var(--muted-strong);

  cursor: pointer;

  font-size: 10px;
  line-height: 1.2;
}

.project-file-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

/* ============================================================
   Sessions
============================================================ */

.project-sessions {
  padding: 2px 0 4px;
}

.session-item {
  width: 100%;

  min-height: 34px;

  display: grid;

  grid-template-columns: 7px minmax(0, 1fr);

  gap: 8px;

  align-items: center;

  padding: 6px 8px 6px 22px;

  border: 0;
  border-radius: 7px;

  background: transparent;

  color: var(--text-soft);

  cursor: pointer;

  text-align: left;
}

.session-item:hover {
  background: var(--surface-hover);
}

.session-item.active {
  background: var(--accent-soft);
  color: var(--text);
}

.session-status {
  width: 7px;
  height: 7px;

  border-radius: 50%;

  background: var(--muted);
}

.session-status.active {
  background: var(--accent);
}

.session-copy {
  min-width: 0;

  display: flex;
  flex-direction: column;
  gap: 1px;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 12px;
}

.session-meta {
  color: var(--muted);

  font-size: 10px;
}

.project-empty,
.sidebar-empty {
  padding: 8px 14px;

  color: var(--muted);

  font-size: 11px;
}

/* ============================================================
   Sidebar Rail & Resize
============================================================ */

/* ============================================================
   Collapsed Rail

   收起态下整个侧边栏变成一条窄导轨，
   仅保留一个「+」入口和一个展开按钮。
   ============================================================ */

.sidebar-rail {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  padding: 10px 0;

  overflow: hidden;
}

.rail-btn {
  flex: 0 0 auto;

  width: 36px;
  height: 36px;

  display: grid;
  place-items: center;

  border: 1px solid transparent;
  border-radius: 8px;

  background: transparent;

  color: var(--muted);

  cursor: pointer;

  font-size: 15px;
  line-height: 1;
}

.rail-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.rail-new-btn {
  border-color: var(--border);

  background: var(--surface);

  color: var(--accent);

  font-size: 18px;
  font-weight: 600;
}

.rail-new-btn:hover {
  background: var(--surface-hover);

  color: var(--accent);
}

/*
  收起态下隐藏组件内部的分割条握把，
  避免它在只有 56px 宽的导轨里产生视觉残留。
*/
.sidebar.collapsed .sidebar-resize-handle {
  display: none;
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
  height: 42px;

  transform: translateY(-50%);

  border-radius: 999px;

  background: var(--border);
}
</style>

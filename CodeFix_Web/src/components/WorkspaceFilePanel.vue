<template>
  <aside class="file-panel">
    <!-- =====================================================
         Header
    ====================================================== -->
    <header class="file-panel-header">
      <div class="file-panel-title">
        <span class="file-panel-label"> 文件 </span>

        <span class="file-panel-project" :title="workspaceName">
          {{ workspaceName || "未关联项目" }}
        </span>
      </div>

      <div class="file-panel-actions">
        <button
          class="panel-icon-btn"
          type="button"
          title="刷新文件树"
          @click="emit('refresh')"
        >
          ↻
        </button>

        <button
          class="panel-icon-btn"
          type="button"
          title="关闭"
          @click="emit('close')"
        >
          ×
        </button>
      </div>
    </header>

    <!-- =====================================================
         Body
    ====================================================== -->
    <div class="file-panel-body">
      <WorkspaceTree
        :tree="tree"
        :selected-file="selectedFile"
        @select="emit('select-file', $event)"
        @toggle="emit('toggle', $event)"
      />

      <div v-if="loading" class="file-panel-hint">正在读取文件树…</div>

      <div v-else-if="!tree.length" class="file-panel-hint">
        当前项目暂无文件
      </div>
    </div>

    <!-- =====================================================
         Selected File
    ====================================================== -->
    <div v-if="selectedFile" class="file-panel-selected">
      <span>已选中</span>

      <code :title="selectedFile">{{ selectedFile }}</code>
    </div>
  </aside>
</template>

<script setup>
import WorkspaceTree from "./WorkspaceTree.vue";

defineProps({
  workspaceName: {
    type: String,
    default: "",
  },

  tree: {
    type: Array,
    default: () => [],
  },

  selectedFile: {
    type: String,
    default: "",
  },

  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["close", "refresh", "select-file", "toggle"]);
</script>

<style>
/* ============================================================
   File Panel
============================================================ */

.file-panel {
  min-width: 0;
  min-height: 0;

  display: flex;
  flex-direction: column;

  overflow: hidden;

  border-left: 1px solid var(--border-subtle);

  background: rgb(14 17 24 / 90%);
}

/* ============================================================
   Header
============================================================ */

.file-panel-header {
  flex: 0 0 auto;

  height: 46px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 0 10px 0 14px;

  border-bottom: 1px solid var(--border-subtle);
}

.file-panel-title {
  min-width: 0;

  display: flex;
  align-items: baseline;
  gap: 8px;
}

.file-panel-label {
  flex: 0 0 auto;

  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;

  color: var(--muted-strong);
}

.file-panel-project {
  min-width: 0;

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--text-soft);

  font-size: 11px;
}

.file-panel-actions {
  flex: 0 0 auto;

  display: flex;
  align-items: center;
  gap: 2px;
}

.panel-icon-btn {
  width: 24px;
  height: 24px;

  display: grid;
  place-items: center;

  border: 0;
  border-radius: 6px;

  background: transparent;

  color: var(--muted);

  cursor: pointer;

  font-size: 14px;
  line-height: 1;
}

.panel-icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

/* ============================================================
   Body
============================================================ */

.file-panel-body {
  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  position: relative;

  overflow: hidden;
}

.file-panel-hint {
  position: absolute;
  inset: 0;

  display: flex;
  align-items: center;
  justify-content: center;

  padding: 26px 20px;

  color: var(--muted);

  text-align: center;

  font-size: 10px;
  line-height: 1.6;
}

/* ============================================================
   Selected
============================================================ */

.file-panel-selected {
  flex: 0 0 auto;

  display: flex;
  flex-direction: column;
  gap: 4px;

  padding: 10px 14px 12px;

  border-top: 1px solid var(--border-subtle);
}

.file-panel-selected > span {
  color: var(--muted);

  font-size: 9px;
}

.file-panel-selected code {
  overflow: hidden;

  color: var(--text-soft);

  text-overflow: ellipsis;

  white-space: nowrap;

  font: 10px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>

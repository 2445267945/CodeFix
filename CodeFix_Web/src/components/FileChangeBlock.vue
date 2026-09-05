<template>
  <button
    class="file-change"
    :class="[`is-${status}`]"
    type="button"
    @click="$emit('open', block)"
  >
    <div class="file-main">
      <div class="file-icon">{}</div>

      <div class="file-copy">
        <div class="file-name">
          {{ block.filePath || "文件变更" }}
        </div>

        <div class="file-meta">
          <span>{{ operationLabel }}</span>

          <span v-if="statusLabel"> · {{ statusLabel }} </span>

          <span v-if="block.changeSummary"> · {{ block.changeSummary }} </span>
        </div>
      </div>
    </div>

    <div v-if="hasDiff" class="file-diff">
      <span class="add"> +{{ block.addedLines || 0 }} </span>

      <span class="remove"> -{{ block.removedLines || 0 }} </span>
    </div>

    <div class="arrow">›</div>
  </button>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  block: {
    type: Object,
    required: true,
  },
});

defineEmits(["open"]);

const status = computed(() => {
  return props.block?.status || "completed";
});

const operationLabel = computed(() => {
  return (
    {
      created: "已创建",
      modified: "已修改",
      deleted: "已删除",
      renamed: "已重命名",
    }[props.block?.operation] || "文件变更"
  );
});

const statusLabel = computed(() => {
  return (
    {
      waiting: "等待确认",
      running: "进行中",
      completed: "",
      failed: "失败",
    }[status.value] || ""
  );
});

const hasDiff = computed(() => {
  return props.block?.addedLines != null || props.block?.removedLines != null;
});
</script>

<style scoped>
.file-change {
  width: 100%;

  display: grid;

  grid-template-columns:
    minmax(0, 1fr)
    auto
    16px;

  gap: 10px;

  align-items: center;

  padding: 10px 0;

  border: 0;

  border-bottom: 1px solid var(--border-subtle);

  background: transparent;

  color: var(--text);

  text-align: left;

  cursor: pointer;
}

.file-change:hover {
  background: var(--surface-hover);
}

/* ============================================================
   Status
============================================================ */

.is-failed .file-icon {
  color: var(--danger);

  background: var(--danger-soft);
}

.is-running .file-icon {
  color: var(--accent);

  background: var(--accent-soft);
}

.is-waiting .file-icon {
  color: var(--warning);

  background: var(--warning-soft);
}

.is-completed .file-icon {
  color: var(--accent);

  background: var(--surface-soft);
}

/* ============================================================
   Main
============================================================ */

.file-main {
  min-width: 0;

  display: flex;

  align-items: center;

  gap: 10px;
}

.file-icon {
  width: 24px;
  height: 24px;

  display: grid;

  place-items: center;

  flex: 0 0 auto;

  border-radius: 6px;

  background: var(--surface-soft);

  color: var(--accent);

  font-family: ui-monospace, monospace;

  font-size: 11px;
}

.file-copy {
  min-width: 0;
}

.file-name {
  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 12px;
}

.file-meta {
  margin-top: 3px;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  color: var(--muted);

  font-size: 11px;
}

/* ============================================================
   Diff
============================================================ */

.file-diff {
  display: flex;

  gap: 6px;

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 11px;
}

.add {
  color: var(--success);
}

.remove {
  color: var(--danger);
}

/* ============================================================
   Arrow
============================================================ */

.arrow {
  color: var(--muted);

  font-size: 18px;
}
</style>
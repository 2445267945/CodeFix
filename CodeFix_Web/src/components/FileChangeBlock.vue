<template>
  <button
    class="file-change"
    :class="[`is-${status}`]"
    type="button"
    @click="$emit('open', block)"
  >
    <span class="file-marker">
      {{ marker }}
    </span>

    <span class="file-copy">
      <span class="file-name">
        {{ block.summary || "文件变更" }}
      </span>

      <span v-if="hasDiff" class="file-diff">
        <span v-if="block.addedLines != null" class="add">
          +{{ block.addedLines || 0 }}
        </span>

        <span v-if="block.removedLines != null" class="remove">
          -{{ block.removedLines || 0 }}
        </span>
      </span>
    </span>

    <span class="file-arrow"> › </span>
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

const marker = computed(() => {
  switch (status.value) {
    case "waiting":
      return "Ⅱ";

    case "running":
      return "●";

    case "failed":
      return "×";

    default:
      return "";
  }
});

const hasDiff = computed(() => {
  return props.block?.addedLines != null || props.block?.removedLines != null;
});
</script>

<style scoped>
.file-change {
  width: 100%;

  display: flex;
  align-items: center;

  min-width: 0;

  padding: 2px 0;

  border: 0;
  background: transparent;

  color: var(--el-text-color-secondary);

  text-align: left;

  cursor: pointer;

  font: inherit;

  line-height: 20px;
}

.file-change:hover {
  color: var(--el-text-color-primary);
}

.file-change:focus-visible {
  outline: none;
}

/* ==================== Marker ==================== */

.file-marker {
  flex: 0 0 auto;

  width: 16px;

  margin-right: 4px;

  color: var(--el-text-color-placeholder);

  font-size: 10px;
  line-height: 20px;

  text-align: center;
}

.is-running .file-marker {
  color: var(--el-color-primary);
}

.is-waiting .file-marker {
  color: var(--el-color-warning);
}

.is-failed .file-marker {
  color: var(--el-color-danger);
}

.is-completed .file-marker {
  color: var(--el-text-color-placeholder);
}

/* ==================== Content ==================== */

.file-copy {
  min-width: 0;

  flex: 1;

  display: flex;
  align-items: baseline;

  gap: 6px;

  overflow: hidden;
}

.file-name {
  min-width: 0;

  overflow: hidden;

  text-overflow: ellipsis;

  white-space: nowrap;

  color: inherit;

  font-size: 12px;
  line-height: 20px;
}

/* ==================== Diff ==================== */

.file-diff {
  display: inline-flex;

  flex: 0 0 auto;

  gap: 4px;

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 11px;
}

.add {
  color: var(--el-color-success);
}

.remove {
  color: var(--el-color-danger);
}

/* ==================== Arrow ==================== */

.file-arrow {
  flex: 0 0 auto;

  margin-left: 6px;

  color: var(--el-text-color-placeholder);

  font-size: 13px;
}

.file-change:hover .file-arrow {
  color: var(--el-text-color-secondary);
}
</style>

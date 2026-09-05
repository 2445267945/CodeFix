<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="58%"
    :with-header="false"
    destroy-on-close
  >
    <div class="diff-viewer">
      <header class="diff-header">
        <div class="diff-title-wrap">
          <div class="diff-title">
            {{ diff?.filePath || "文件变更" }}
          </div>

          <div class="diff-meta">
            <span>{{ operationLabel }}</span>

            <span class="add"> +{{ diff?.addedLines || 0 }} </span>

            <span class="remove"> -{{ diff?.removedLines || 0 }} </span>
          </div>
        </div>

        <button class="close-btn" type="button" @click="close">×</button>
      </header>

      <div v-if="loading" class="diff-state">正在加载 Diff…</div>

      <div v-else-if="!diff" class="diff-state">暂无 Diff</div>

      <div v-else class="diff-content">
        <div
          v-for="(line, index) in diffLines"
          :key="`${index}-${line.text}`"
          class="diff-line"
          :class="line.type"
        >
          <span class="line-marker">
            {{ line.marker }}
          </span>

          <code>{{ line.text }}</code>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },

  diff: {
    type: Object,
    default: null,
  },

  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:modelValue"]);

const visible = computed({
  get: () => props.modelValue,

  set: (value) => emit("update:modelValue", value),
});

const operationLabel = computed(() => {
  return (
    {
      created: "已创建",
      modified: "已修改",
      deleted: "已删除",
      renamed: "已重命名",
    }[props.diff?.operation] || "文件变更"
  );
});

const diffLines = computed(() => {
  const diff = props.diff?.diff || "";

  if (!diff) {
    return [];
  }

  return diff.split(/\r?\n/).map((line) => {
    if (line.startsWith("+++") || line.startsWith("---")) {
      return {
        type: "header",
        marker: "",
        text: line,
      };
    }

    if (line.startsWith("@@")) {
      return {
        type: "hunk",
        marker: "",
        text: line,
      };
    }

    if (line.startsWith("+")) {
      return {
        type: "added",
        marker: "+",
        text: line.slice(1),
      };
    }

    if (line.startsWith("-")) {
      return {
        type: "removed",
        marker: "-",
        text: line.slice(1),
      };
    }

    return {
      type: "context",
      marker: " ",
      text: line.startsWith(" ") ? line.slice(1) : line,
    };
  });
});

function close() {
  visible.value = false;
}
</script>

<style scoped>
.diff-viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--page);
}

.diff-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface);
}

.diff-title-wrap {
  min-width: 0;
}

.diff-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  font-weight: 650;
}

.diff-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 5px;
  color: var(--muted);
  font-size: 10px;
}

.add {
  color: var(--success);
}

.remove {
  color: var(--danger);
}

.close-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted-strong);
  cursor: pointer;
  font-size: 20px;
}

.close-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.diff-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 0 24px;
  background: #171917;
  color: #d5d8d2;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

.diff-line {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  min-height: 20px;
}

.diff-line code {
  padding-right: 18px;
  white-space: pre;
}

.line-marker {
  padding-left: 10px;
  user-select: none;
  color: #7f847b;
}

.diff-line.added {
  background: rgba(36, 129, 92, 0.12);
}

.diff-line.added .line-marker {
  color: var(--success);
}

.diff-line.removed {
  background: rgba(177, 59, 59, 0.12);
}

.diff-line.removed .line-marker {
  color: var(--danger);
}

.diff-line.hunk {
  color: #8e98d7;
  background: rgba(76, 91, 212, 0.09);
}

.diff-line.header {
  color: #9da39a;
  background: rgba(255, 255, 255, 0.025);
}

.diff-state {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 12px;
}
</style>